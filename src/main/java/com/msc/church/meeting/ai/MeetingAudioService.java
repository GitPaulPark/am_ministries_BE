package com.msc.church.meeting.ai;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.CommitteeMembershipRepository;
import com.msc.church.meeting.Meeting;
import com.msc.church.meeting.MeetingNotFoundException;
import com.msc.church.meeting.MeetingProcessingJob;
import com.msc.church.meeting.MeetingProcessingJobRepository;
import com.msc.church.meeting.MeetingProcessingStatus;
import com.msc.church.meeting.MeetingRepository;
import com.msc.church.meeting.ai.dto.MeetingProcessingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Handles the audio side of the AI meeting pipeline:
 * <ul>
 *   <li>{@link #upload}: validates + saves to disk, kicks off async processing</li>
 *   <li>{@link #streamAudio}: auth-gated stream-download for committee members</li>
 *   <li>{@link #getProcessingStatus}: snapshot of the latest job for polling</li>
 * </ul>
 *
 * <p>Audio files live under {@code {msc.ai.uploads-dir}/meetings/{id}/audio.{ext}} and
 * are NOT served by the public {@code /uploads/**} static handler — this controller
 * streams them with the same visibility rules as the meeting itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAudioService {

    private static final long ONE_MB = 1024L * 1024L;

    private final MeetingRepository meetingRepository;
    private final MeetingProcessingJobRepository jobRepository;
    private final CommitteeMembershipRepository committeeMembershipRepository;
    private final MeetingProcessingOrchestrator orchestrator;
    private final AiProperties props;

    @Transactional
    public MeetingProcessingResponse upload(Long meetingId, MultipartFile file,
                                            String languageCodeOverride,
                                            AuthenticatedUser caller) throws IOException {
        if (!props.isEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ai_disabled");
        }
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        enforceWriteAccess(m, caller);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "file");
        }
        long maxBytes = (long) props.getMaxAudioMb() * ONE_MB;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "audio_too_large");
        }

        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!"m4a".equals(ext) && !"mp3".equals(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "audio_format");
        }

        Path base = Paths.get(props.getUploadsDir(), "meetings", String.valueOf(meetingId))
                .toAbsolutePath().normalize();
        Files.createDirectories(base);
        // Always store as audio.{ext} — overwrites any prior upload deterministically.
        Path target = base.resolve("audio." + ext);
        // Wipe any opposite-extension leftover so we never end up with two source files.
        cleanupOtherExtension(base, ext);
        file.transferTo(target);

        m.setAudioUrl("/api/v1/meetings/" + meetingId + "/audio");
        m.setAudioSizeBytes(file.getSize());
        m.setProcessingStatus(MeetingProcessingStatus.QUEUED);
        m.setProcessingError(null);

        MeetingProcessingJob job = MeetingProcessingJob.builder()
                .meeting(m)
                .status(MeetingProcessingStatus.QUEUED)
                .triggeredByUserId(caller != null ? caller.id() : null)
                .build();
        job = jobRepository.save(job);
        meetingRepository.flush();
        jobRepository.flush();

        String language = (languageCodeOverride != null && !languageCodeOverride.isBlank())
                ? languageCodeOverride : props.getDefaultLanguage();

        log.info("Meeting audio uploaded: meetingId={} bytes={} ext={} jobId={} lang={} byUser={}",
                meetingId, file.getSize(), ext, job.getId(), language, callerId(caller));

        // Fire-and-forget — proxied @Async on a separate bean.
        orchestrator.processAsync(meetingId, job.getId(), target, language);

        return toResponse(m, job);
    }

    @Transactional(readOnly = true)
    public MeetingProcessingResponse getProcessingStatus(Long meetingId, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        enforceReadAccess(m, caller);
        MeetingProcessingJob job = jobRepository
                .findFirstByMeeting_IdOrderByCreatedAtDesc(meetingId).orElse(null);
        return toResponse(m, job);
    }

    @Transactional(readOnly = true)
    public Resource streamAudio(Long meetingId, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        enforceReadAccess(m, caller);
        Path base = Paths.get(props.getUploadsDir(), "meetings", String.valueOf(meetingId))
                .toAbsolutePath().normalize();
        for (String ext : new String[]{"m4a", "mp3"}) {
            Path candidate = base.resolve("audio." + ext);
            if (Files.exists(candidate)) {
                return new FileSystemResource(candidate);
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "audio");
    }

    // ------------------------------------------------------------------

    private MeetingProcessingResponse toResponse(Meeting m, MeetingProcessingJob job) {
        return new MeetingProcessingResponse(
                m.getId(),
                m.getProcessingStatus(),
                m.getProcessingError(),
                job != null ? job.getStartedAt() : null,
                job != null ? job.getCompletedAt() : null,
                m.getAudioUrl(),
                m.getAudioDurationSec(),
                m.getAudioSizeBytes(),
                job != null ? job.getTranscriptionSeconds() : null,
                job != null ? job.getAnalysisModel() : null,
                job != null ? job.getAnalysisInputTokens() : null,
                job != null ? job.getAnalysisOutputTokens() : null);
    }

    private void enforceWriteAccess(Meeting m, AuthenticatedUser caller) {
        if (isStaff(caller)) return;
        // Committee members may also upload (board secretaries record the meeting).
        if (caller != null && caller.memberId() != null
                && committeeMembershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(
                        m.getCommittee().getId(), caller.memberId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void enforceReadAccess(Meeting m, AuthenticatedUser caller) {
        if (isStaff(caller)) return;
        if (caller != null && caller.memberId() != null
                && committeeMembershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(
                        m.getCommittee().getId(), caller.memberId())) {
            return;
        }
        throw new MeetingNotFoundException(m.getId());
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
    }

    private static Long callerId(AuthenticatedUser caller) {
        return caller == null ? null : caller.id();
    }

    private static String extensionOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void cleanupOtherExtension(Path base, String keepExt) throws IOException {
        for (String ext : new String[]{"m4a", "mp3"}) {
            if (ext.equals(keepExt)) continue;
            Path other = base.resolve("audio." + ext);
            Files.deleteIfExists(other);
        }
    }
}

package com.msc.church.sermon;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.Bulletin;
import com.msc.church.bulletin.BulletinRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import com.msc.church.sermon.dto.SermonAudioResponse;
import com.msc.church.sermon.dto.SermonCreateRequest;
import com.msc.church.sermon.dto.SermonDetail;
import com.msc.church.sermon.dto.SermonSummary;
import com.msc.church.sermon.dto.SermonUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Sermon module service. Handles CRUD, search, audio upload (streamed to disk via
 * {@link MultipartFile#transferTo}), and publish validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SermonService {

    private final SermonRepository sermonRepository;
    private final MemberRepository memberRepository;
    private final BulletinRepository bulletinRepository;
    private final SermonMapper sermonMapper;
    private final SermonTranscriptService transcriptService;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<SermonSummary> search(String q, Integer year, Long preacherId,
                                      Pageable pageable, AuthenticatedUser caller) {
        boolean publishedOnly = !isStaff(caller);
        // Spec: queries shorter than 2 chars return nothing, to keep search noise down.
        String effectiveQ = (q != null && q.trim().length() >= 2) ? q.trim() : null;
        return sermonRepository.search(effectiveQ, year, preacherId, publishedOnly, pageable)
                .map(sermonMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public SermonDetail get(Long id, AuthenticatedUser caller) {
        Sermon s = sermonRepository.findWithRefsById(id)
                .orElseThrow(() -> new SermonNotFoundException(id));
        enforceVisibility(s, caller);
        return sermonMapper.toDetail(s, transcriptService.loadTranscript(id));
    }

    @Transactional(readOnly = true)
    public SermonDetail latest() {
        Sermon s = sermonRepository.findFirstByPublishedTrueOrderBySermonDateDesc()
                .orElseThrow(() -> new SermonNotFoundException("latest"));
        return sermonMapper.toDetail(s, transcriptService.loadTranscript(s.getId()));
    }

    // ---------- writes ----------

    @Transactional
    public SermonDetail create(SermonCreateRequest request, AuthenticatedUser caller) {
        if (sermonRepository.existsBySermonDate(request.sermonDate())) {
            throw new DuplicateSermonDateException(request.sermonDate());
        }
        Sermon s = Sermon.builder()
                .sermonDate(request.sermonDate())
                .titleKr(blankToNull(request.titleKr()))
                .titleEn(blankToNull(request.titleEn()))
                .preacher(loadMember(request.preacherMemberId()))
                .preacherNameLabel(blankToNull(request.preacherNameLabel()))
                .scriptureRef(request.scriptureRef().trim())
                .scriptureTextKr(blankToNull(request.scriptureTextKr()))
                .scriptureTextEn(blankToNull(request.scriptureTextEn()))
                .videoUrl(blankToNull(request.videoUrl()))
                .transcriptKr(blankToNull(request.transcriptKr()))
                .transcriptEn(blankToNull(request.transcriptEn()))
                .theme(blankToNull(request.theme()))
                .cellReflectionQuestionsJson(sermonMapper.serializeQuestions(request.cellReflectionQuestions()))
                .linkedBulletin(loadBulletin(request.bulletinId()))
                .published(false)
                .build();
        Sermon saved = sermonRepository.save(s);
        log.info("Sermon created: id={} date={} byUser={}",
                saved.getId(), saved.getSermonDate(), callerId(caller));
        return sermonMapper.toDetail(saved);
    }

    @Transactional
    public SermonDetail update(Long id, SermonUpdateRequest request, AuthenticatedUser caller) {
        Sermon s = sermonRepository.findById(id)
                .orElseThrow(() -> new SermonNotFoundException(id));

        if (!s.getSermonDate().equals(request.sermonDate())
                && sermonRepository.existsBySermonDate(request.sermonDate())) {
            throw new DuplicateSermonDateException(request.sermonDate());
        }

        s.setSermonDate(request.sermonDate());
        s.setTitleKr(blankToNull(request.titleKr()));
        s.setTitleEn(blankToNull(request.titleEn()));
        s.setPreacher(loadMember(request.preacherMemberId()));
        s.setPreacherNameLabel(blankToNull(request.preacherNameLabel()));
        s.setScriptureRef(request.scriptureRef().trim());
        s.setScriptureTextKr(blankToNull(request.scriptureTextKr()));
        s.setScriptureTextEn(blankToNull(request.scriptureTextEn()));
        s.setVideoUrl(blankToNull(request.videoUrl()));
        s.setTranscriptKr(blankToNull(request.transcriptKr()));
        s.setTranscriptEn(blankToNull(request.transcriptEn()));
        s.setTheme(blankToNull(request.theme()));
        s.setCellReflectionQuestionsJson(sermonMapper.serializeQuestions(request.cellReflectionQuestions()));
        s.setLinkedBulletin(loadBulletin(request.bulletinId()));

        log.info("Sermon updated: id={} byUser={}", id, callerId(caller));
        return sermonMapper.toDetail(s);
    }

    @Transactional
    public SermonDetail publish(Long id, AuthenticatedUser caller) {
        Sermon s = sermonRepository.findById(id)
                .orElseThrow(() -> new SermonNotFoundException(id));
        if (s.isPublished()) {
            // Already published: idempotent — return the current state.
            return sermonMapper.toDetail(s);
        }
        // Publish-time validation: title in at least one locale, scripture ref present
        // (already required), preacher specified somehow.
        if ((s.getTitleKr() == null || s.getTitleKr().isBlank())
                && (s.getTitleEn() == null || s.getTitleEn().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title");
        }
        if (s.getPreacher() == null
                && (s.getPreacherNameLabel() == null || s.getPreacherNameLabel().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "preacher");
        }
        s.setPublished(true);
        s.setPublishedAt(LocalDateTime.now());
        log.info("Sermon published: id={} byUser={}", id, callerId(caller));
        return sermonMapper.toDetail(s);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        Sermon s = sermonRepository.findById(id)
                .orElseThrow(() -> new SermonNotFoundException(id));
        sermonRepository.delete(s);
        // Audio file on disk is left orphaned in V1 — cheap to clean up later. V2
        // hooks an entity listener to {@link Files#deleteIfExists(Path)}.
        log.info("Sermon deleted: id={} byUser={}", id, callerId(caller));
    }

    @Transactional
    public SermonAudioResponse uploadAudio(Long id, MultipartFile file, AuthenticatedUser caller) throws IOException {
        Sermon s = sermonRepository.findById(id)
                .orElseThrow(() -> new SermonNotFoundException(id));
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "file");
        }
        // Normalise to mp3 — V1 accepts mp3 only.
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".mp3")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "audio_format");
        }

        Path base = Paths.get(uploadsDir, "sermons", String.valueOf(id)).toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path target = base.resolve("audio.mp3");
        // Stream straight to disk. transferTo overwrites by default.
        file.transferTo(target);

        // Public URL served by WebConfig at /uploads/**.
        String url = "/uploads/sermons/" + id + "/audio.mp3";
        s.setAudioUrl(url);
        log.info("Sermon audio uploaded: id={} bytes={} byUser={}",
                id, file.getSize(), callerId(caller));
        return new SermonAudioResponse(url);
    }

    // ---------- helpers ----------

    private void enforceVisibility(Sermon s, AuthenticatedUser caller) {
        if (isStaff(caller)) return;
        if (!s.isPublished()) {
            // Looks like 404 to non-staff per the spec.
            throw new SermonNotFoundException(s.getId());
        }
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
    }

    private Member loadMember(Long id) {
        if (id == null) return null;
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Bulletin loadBulletin(Long id) {
        if (id == null) return null;
        return bulletinRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BULLETIN_NOT_FOUND));
    }

    private static Long callerId(AuthenticatedUser caller) {
        return caller == null ? null : caller.id();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

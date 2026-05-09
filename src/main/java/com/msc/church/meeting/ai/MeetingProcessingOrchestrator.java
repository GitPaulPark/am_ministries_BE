package com.msc.church.meeting.ai;

import com.msc.church.meeting.ActionItem;
import com.msc.church.meeting.ActionItemStatus;
import com.msc.church.meeting.Meeting;
import com.msc.church.meeting.MeetingProcessingJob;
import com.msc.church.meeting.MeetingProcessingJobRepository;
import com.msc.church.meeting.MeetingProcessingStatus;
import com.msc.church.meeting.MeetingRepository;
import com.msc.church.meeting.MeetingTopic;
import com.msc.church.meeting.MeetingTopicRepository;
import com.msc.church.meeting.MeetingTopicStatus;
import com.msc.church.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the audio → transcript → topics pipeline asynchronously after an upload.
 *
 * <p>Lives in a separate bean from {@link MeetingAudioService} so Spring's AOP proxy
 * actually intercepts the {@code @Async} method (self-calls bypass the proxy).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>set status QUEUED → TRANSCRIBING, call {@link TranscriptionService}</li>
 *   <li>set status TRANSCRIBING → ANALYZING, call {@link MeetingAnalysisService}</li>
 *   <li>persist transcript + ai_summary on Meeting; replace topics + AI-generated action items</li>
 *   <li>set status ANALYZING → COMPLETE</li>
 * </ol>
 * Failures land in {@code FAILED} with a clear message on the {@link MeetingProcessingJob}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingProcessingOrchestrator {

    private final MeetingRepository meetingRepository;
    private final MeetingTopicRepository topicRepository;
    private final MeetingProcessingJobRepository jobRepository;
    private final TranscriptionService transcriptionService;
    private final MeetingAnalysisService analysisService;
    private final AiProperties props;

    @Async
    public void processAsync(Long meetingId, Long jobId, Path audioPath, String languageCode) {
        try {
            // ---- Phase 1: transcription ----
            updateStatus(meetingId, jobId, MeetingProcessingStatus.TRANSCRIBING, true);
            Transcript transcript = transcriptionService.transcribe(audioPath, languageCode);
            saveTranscript(meetingId, jobId, transcript);

            // ---- Phase 2: analysis ----
            updateStatus(meetingId, jobId, MeetingProcessingStatus.ANALYZING, false);
            MeetingAnalysisInput input = buildAnalysisInput(meetingId, transcript.text());
            MeetingAnalysisResult result = analysisService.analyze(input);
            persistAnalysis(meetingId, jobId, result);

            updateStatus(meetingId, jobId, MeetingProcessingStatus.COMPLETE, false);
            log.info("Meeting AI processing complete: meetingId={} topics={} actions={}",
                    meetingId, result.topics().size(), result.actionItems().size());

        } catch (Exception e) {
            log.error("Meeting AI processing failed: meetingId={} err={}", meetingId, e.getMessage(), e);
            recordFailure(meetingId, jobId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //   Persistence helpers — each in its own @Transactional so phase
    //   transitions land in the DB independently of later failures.
    // ------------------------------------------------------------------

    @Transactional
    public void updateStatus(Long meetingId, Long jobId, MeetingProcessingStatus status, boolean recordStartedAt) {
        Meeting m = meetingRepository.findById(meetingId).orElseThrow();
        m.setProcessingStatus(status);
        MeetingProcessingJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        if (recordStartedAt) job.setStartedAt(LocalDateTime.now());
        // Stamp completion timestamp on terminal status transitions so duration
        // metrics work for successful runs as well as failures.
        if (status == MeetingProcessingStatus.COMPLETE
                || status == MeetingProcessingStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void saveTranscript(Long meetingId, Long jobId, Transcript transcript) {
        Meeting m = meetingRepository.findById(meetingId).orElseThrow();
        m.setTranscript(transcript.text());
        m.setAudioDurationSec(transcript.durationSec());

        MeetingProcessingJob job = jobRepository.findById(jobId).orElseThrow();
        job.setAudioSeconds(transcript.durationSec());
        job.setTranscriptionProvider(transcript.provider());
        job.setTranscriptionModel(transcript.model());
        job.setTranscriptionSeconds(transcript.wallSeconds());
    }

    @Transactional(readOnly = true)
    public MeetingAnalysisInput buildAnalysisInput(Long meetingId, String transcript) {
        Meeting m = meetingRepository.findById(meetingId).orElseThrow();

        // Prior meetings — same committee, before this meeting's date, newest first.
        List<Meeting> prior = meetingRepository
                .findByCommittee_IdAndMeetingDateLessThanOrderByMeetingDateDesc(
                        m.getCommittee().getId(),
                        m.getMeetingDate(),
                        PageRequest.of(0, props.getPriorMeetingsContext()))
                .getContent();

        List<MeetingAnalysisInput.PriorMeeting> priors = new ArrayList<>(prior.size());
        for (Meeting p : prior) {
            priors.add(new MeetingAnalysisInput.PriorMeeting(
                    p.getId(), p.getMeetingDate(), p.getAiSummary()));
        }

        // Open action items still outstanding (any prior meeting, this committee).
        List<MeetingAnalysisInput.OpenActionItem> open = new ArrayList<>();
        for (Meeting p : prior) {
            for (ActionItem a : p.getActionItems()) {
                if (a.getStatus() != ActionItemStatus.OPEN) continue;
                Member assignee = a.getAssignee();
                open.add(new MeetingAnalysisInput.OpenActionItem(
                        a.getId(),
                        a.getDescription(),
                        assignee != null ? assignee.getNameKr() : null,
                        a.getDueDate()));
            }
        }

        return new MeetingAnalysisInput(
                m.getMeetingDate(),
                m.getCommittee().getNameKr(),
                transcript,
                priors,
                open);
    }

    @Transactional
    public void persistAnalysis(Long meetingId, Long jobId, MeetingAnalysisResult result) {
        Meeting m = meetingRepository.findById(meetingId).orElseThrow();

        // Replace any prior AI-generated topics + action items with the fresh set.
        m.setAiSummary(result.summary());
        m.getTopics().removeIf(MeetingTopic::isAiGenerated);
        m.getActionItems().removeIf(ActionItem::isAiGenerated);

        // Materialize topics first; we need their generated IDs to link action items.
        List<MeetingTopic> topics = new ArrayList<>();
        int nextOrderIdx = m.getTopics().stream()
                .mapToInt(t -> t.getOrderIdx() == null ? 0 : t.getOrderIdx())
                .max().orElse(-1) + 1;
        for (var draft : result.topics()) {
            // Cross-meeting parentTopic resolution: Claude returned an ID; verify it
            // exists AND belongs to the same committee before linking. A bad ID just
            // becomes null linkage rather than a dangling FK.
            MeetingTopic parent = null;
            if (draft.parentTopicId() != null) {
                parent = topicRepository.findById(draft.parentTopicId())
                        .filter(p -> p.getMeeting() != null
                                && p.getMeeting().getCommittee() != null
                                && p.getMeeting().getCommittee().getId()
                                        .equals(m.getCommittee().getId()))
                        .orElse(null);
            }
            MeetingTopic topic = MeetingTopic.builder()
                    .meeting(m)
                    .parentTopic(parent)
                    .title(draft.title())
                    .summary(draft.summary())
                    .decision(draft.decision())
                    .status(parseTopicStatus(draft.status()))
                    .transcriptExcerpt(draft.transcriptExcerpt())
                    .orderIdx(draft.orderIdx() != null ? draft.orderIdx() : nextOrderIdx++)
                    .aiGenerated(true)
                    .build();
            m.getTopics().add(topic);
            topics.add(topic);
        }

        int actionStartIdx = m.getActionItems().stream()
                .mapToInt(a -> a.getOrderIdx() == null ? 0 : a.getOrderIdx())
                .max().orElse(-1) + 1;
        for (var draft : result.actionItems()) {
            MeetingTopic linkedTopic = (draft.topicIndex() != null
                    && draft.topicIndex() >= 0 && draft.topicIndex() < topics.size())
                    ? topics.get(draft.topicIndex()) : null;
            ActionItem item = ActionItem.builder()
                    .meeting(m)
                    .description(draft.description())
                    .status(ActionItemStatus.OPEN)
                    .topic(linkedTopic)
                    .aiGenerated(true)
                    .orderIdx(actionStartIdx++)
                    .dueDate(parseDueDateHint(draft.dueDateHint(), m.getMeetingDate()))
                    .build();
            m.getActionItems().add(item);
        }

        m.setProcessedAt(LocalDateTime.now());

        MeetingProcessingJob job = jobRepository.findById(jobId).orElseThrow();
        job.setAnalysisProvider("anthropic");
        job.setAnalysisModel(props.getAnthropic().getModel());
        job.setAnalysisInputTokens(result.inputTokens());
        job.setAnalysisOutputTokens(result.outputTokens());
        job.setAnalysisCacheReadTokens(result.cacheReadTokens());
        job.setAnalysisCacheWriteTokens(result.cacheWriteTokens());
    }

    @Transactional
    public void recordFailure(Long meetingId, Long jobId, String message) {
        meetingRepository.findById(meetingId).ifPresent(m -> {
            m.setProcessingStatus(MeetingProcessingStatus.FAILED);
            m.setProcessingError(message);
        });
        jobRepository.findById(jobId).ifPresent(j -> {
            j.setStatus(MeetingProcessingStatus.FAILED);
            j.setCompletedAt(LocalDateTime.now());
            j.setErrorMessage(message);
        });
    }

    // ------------------------------------------------------------------
    //   Tiny parsing helpers
    // ------------------------------------------------------------------

    private MeetingTopicStatus parseTopicStatus(String s) {
        if (s == null) return MeetingTopicStatus.DISCUSSED;
        try {
            return MeetingTopicStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MeetingTopicStatus.DISCUSSED;
        }
    }

    /**
     * Best-effort interpretation of Claude's free-form due-date hint. Returns null
     * for anything ambiguous — the user can fill it in manually after review.
     */
    private LocalDate parseDueDateHint(String hint, LocalDate meetingDate) {
        if (hint == null || hint.isBlank()) return null;
        String h = hint.trim();
        // Plain ISO date wins.
        try { return LocalDate.parse(h); } catch (Exception ignore) { /* fall through */ }
        // "다음 회의" / "next meeting" → 7 days out as a sensible default.
        if (h.contains("다음 회의") || h.toLowerCase().contains("next meeting")) {
            return meetingDate.plusDays(7);
        }
        return null;
    }
}

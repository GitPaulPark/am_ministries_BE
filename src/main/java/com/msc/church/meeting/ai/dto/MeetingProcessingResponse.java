package com.msc.church.meeting.ai.dto;

import com.msc.church.meeting.MeetingProcessingStatus;

import java.time.LocalDateTime;

/**
 * Snapshot of the latest AI processing attempt for a meeting. The frontend polls
 * this while processingStatus is non-terminal (anything other than NONE / COMPLETE
 * / FAILED), then invalidates the meeting query when the status flips.
 */
public record MeetingProcessingResponse(
        Long meetingId,
        MeetingProcessingStatus status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String audioUrl,
        Integer audioDurationSec,
        Long audioSizeBytes,
        Integer transcriptionSeconds,
        String analysisModel,
        Integer analysisInputTokens,
        Integer analysisOutputTokens) {
}

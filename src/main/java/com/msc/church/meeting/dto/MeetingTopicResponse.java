package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingTopicStatus;

import java.time.LocalDate;

/**
 * Topic returned in {@link MeetingDetail}. AI-generated topics carry
 * {@code aiGenerated=true} so the UI can render an "AI" badge. When a topic
 * continues a discussion from a prior meeting, {@code parentTopicId} (and the
 * helper {@code parentTopicMeetingDate}) drive the "previously discussed" link.
 */
public record MeetingTopicResponse(
        Long id,
        Long meetingId,
        Long parentTopicId,
        Long parentTopicMeetingId,
        LocalDate parentTopicMeetingDate,
        String title,
        String summary,
        String decision,
        MeetingTopicStatus status,
        String comment,
        String transcriptExcerpt,
        Integer orderIdx,
        boolean aiGenerated) {
}

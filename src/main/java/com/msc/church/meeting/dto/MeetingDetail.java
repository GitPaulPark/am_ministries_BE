package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingProcessingStatus;
import com.msc.church.meeting.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MeetingDetail(
        Long id,
        CommitteeRef committee,
        LocalDate meetingDate,
        String title,
        MemberRef presider,
        String agenda,
        String minutes,
        MeetingStatus status,
        LocalDateTime publishedAt,
        List<MeetingAttendeeResponse> attendees,
        List<ActionItemResponse> actionItems,
        // ---- AI fields (V3) ----
        List<MeetingTopicResponse> topics,
        String aiSummary,
        String audioUrl,
        Integer audioDurationSec,
        Long audioSizeBytes,
        MeetingProcessingStatus processingStatus,
        String processingError,
        LocalDateTime processedAt
) {
}

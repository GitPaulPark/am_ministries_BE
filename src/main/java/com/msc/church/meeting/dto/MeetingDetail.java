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
        LocalDateTime processedAt,
        /**
         * Whether the calling user can edit topics + action items + upload audio.
         * Computed server-side using the same rule as
         * {@link com.msc.church.meeting.MeetingTopicService#enforceWrite}: ADMIN/PASTOR
         * always, plus active committee members of this meeting's committee. Avoids
         * the frontend hard-coding canEdit=isStaff and hiding controls from secretaries
         * who are allowed to edit.
         */
        boolean canEdit
) {
}

package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingStatus;

import java.time.LocalDate;

public record MeetingSummary(
        Long id,
        CommitteeRef committee,
        LocalDate meetingDate,
        String title,
        MemberRef presider,
        MeetingStatus status,
        int actionItemCount,
        int openActionItemCount
) {
}

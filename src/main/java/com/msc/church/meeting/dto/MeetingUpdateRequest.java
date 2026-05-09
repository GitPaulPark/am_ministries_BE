package com.msc.church.meeting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MeetingUpdateRequest(
        @NotNull LocalDate meetingDate,
        Long presiderMemberId,
        @Size(max = 255) String title,
        String agenda,
        String minutes,
        List<Long> attendeeMemberIds
) {
}

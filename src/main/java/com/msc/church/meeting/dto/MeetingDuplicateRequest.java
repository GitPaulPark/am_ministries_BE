package com.msc.church.meeting.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MeetingDuplicateRequest(
        @NotNull LocalDate newMeetingDate,
        boolean carryOverOpenActionItems
) {
}

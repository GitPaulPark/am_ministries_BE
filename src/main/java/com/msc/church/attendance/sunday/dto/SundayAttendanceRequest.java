package com.msc.church.attendance.sunday.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SundayAttendanceRequest(
        @NotNull LocalDate serviceDate,
        String entryMode,                   // AFTER | LIVE; defaults to AFTER
        String notes,
        @NotNull List<EntryInput> entries
) {
    public record EntryInput(
            @NotNull Long memberId,
            boolean present,
            String note
    ) {}
}

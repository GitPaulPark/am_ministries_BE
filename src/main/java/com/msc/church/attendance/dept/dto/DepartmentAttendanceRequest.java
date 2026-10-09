package com.msc.church.attendance.dept.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Create-or-update payload for a department attendance event. The endpoint
 * is upsert-by-(committee, date, label) so the client doesn't have to know
 * whether the row exists yet.
 */
public record DepartmentAttendanceRequest(
        @NotNull LocalDate eventDate,
        String eventLabel,                   // nullable -> "" used as default key
        String entryMode,                    // AFTER | LIVE; defaults to AFTER
        String notes,
        @NotNull List<EntryInput> entries
) {
    public record EntryInput(
            @NotNull Long memberId,
            boolean present,
            String note
    ) {}
}

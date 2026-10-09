package com.msc.church.attendance.dept.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response shape for the roster entry + history views. */
public record DepartmentAttendanceDto(
        Long id,
        Long committeeId,
        String committeeNameKr,
        String committeeNameEn,
        LocalDate eventDate,
        String eventLabel,
        String entryMode,                  // AFTER | LIVE
        String notes,
        boolean locked,                    // derived from locked_at OR 30-day window
        LocalDateTime lockedAt,
        LocalDateTime createdAt,
        List<Entry> entries,
        int presentCount,
        int totalCount
) {
    public record Entry(
            Long memberId,
            String nameKr,
            String nameEn,
            boolean present,
            String note
    ) {}
}

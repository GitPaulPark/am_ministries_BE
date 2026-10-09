package com.msc.church.attendance.sunday.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SundayAttendanceDto(
        Long id,
        Long cellId,
        String cellCode,
        String cellNameKr,
        String cellNameEn,
        LocalDate serviceDate,
        String entryMode,
        String notes,
        boolean locked,
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

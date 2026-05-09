package com.msc.church.attendance.dto;

import java.time.LocalDate;

public record CellAttendanceRecord(
        LocalDate serviceDate,
        Long cellId,
        String cellCode,
        String cellNameKr,
        String cellNameEn,
        Integer count
) {
}

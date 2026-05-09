package com.msc.church.attendance.dto;

import java.time.LocalDate;

public record ServiceAttendanceRecord(
        LocalDate serviceDate,
        String serviceType,
        String ageGroup,
        Integer count
) {
}

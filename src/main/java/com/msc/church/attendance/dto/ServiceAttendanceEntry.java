package com.msc.church.attendance.dto;

import com.msc.church.attendance.AgeGroup;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ServiceAttendanceEntry(
        @NotNull AgeGroup ageGroup,
        @NotNull @Min(0) Integer count
) {
}

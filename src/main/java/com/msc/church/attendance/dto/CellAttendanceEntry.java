package com.msc.church.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CellAttendanceEntry(
        @NotNull Long cellId,
        @NotNull @Min(0) Integer count
) {
}

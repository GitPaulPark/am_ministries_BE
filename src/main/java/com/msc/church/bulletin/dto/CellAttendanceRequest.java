package com.msc.church.bulletin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CellAttendanceRequest(
        @NotNull Long cellId,
        @NotNull @Min(0) Integer attendanceCount
) {
}

package com.msc.church.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CellAttendanceUpsertRequest(
        @NotNull LocalDate serviceDate,
        @NotEmpty @Valid List<CellAttendanceEntry> entries
) {
}

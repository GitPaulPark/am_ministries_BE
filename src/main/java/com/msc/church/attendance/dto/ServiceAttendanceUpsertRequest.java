package com.msc.church.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ServiceAttendanceUpsertRequest(
        @NotNull LocalDate serviceDate,
        @NotBlank @Size(max = 50) String serviceType,
        @NotEmpty @Valid List<ServiceAttendanceEntry> entries
) {
}

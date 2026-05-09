package com.msc.church.cell.dto;

import com.msc.church.cell.CellType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CellUpdateRequest(
        @NotBlank @Size(max = 100) String nameKr,
        @Size(max = 100) String nameEn,
        @NotNull CellType type,
        Long leaderMemberId,
        @Size(max = 20) String meetingDay,
        @Size(max = 20) String meetingTime,
        @Size(max = 255) String meetingLocation,
        String description,
        boolean active
) {
}

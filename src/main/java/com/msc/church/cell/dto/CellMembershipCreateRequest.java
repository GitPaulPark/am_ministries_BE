package com.msc.church.cell.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CellMembershipCreateRequest(
        @NotNull Long memberId,
        boolean isPrimary,
        @Size(max = 50) String role,
        LocalDate joinedAt
) {
}

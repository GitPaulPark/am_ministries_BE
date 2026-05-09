package com.msc.church.cell.dto;

import jakarta.validation.constraints.Size;

public record CellMembershipUpdateRequest(
        Boolean isPrimary,
        @Size(max = 50) String role,
        Boolean active
) {
}

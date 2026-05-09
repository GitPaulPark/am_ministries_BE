package com.msc.church.cell.dto;

import java.time.LocalDate;

public record CellMembershipResponse(
        Long id,
        Long cellId,
        Long memberId,
        String memberNameKr,
        String memberNameEn,
        boolean isPrimary,
        String role,
        LocalDate joinedAt,
        LocalDate leftAt,
        boolean active
) {
}

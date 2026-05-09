package com.msc.church.member.dto;

import com.msc.church.cell.CellType;

import java.time.LocalDate;

public record MembershipSummary(
        Long id,
        Long cellId,
        String cellCode,
        String cellNameKr,
        String cellNameEn,
        CellType cellType,
        boolean isPrimary,
        String role,
        LocalDate joinedAt,
        boolean active
) {
}

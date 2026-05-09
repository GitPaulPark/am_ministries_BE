package com.msc.church.cell.dto;

import java.time.LocalDate;

/** One row in a cell's roster (membership joined to member). */
public record CellRosterEntry(
        Long membershipId,
        Long memberId,
        String nameKr,
        String nameEn,
        String role,
        boolean isPrimary,
        LocalDate joinedAt,
        boolean active
) {
}

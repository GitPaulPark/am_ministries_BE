package com.msc.church.cell.dto;

import com.msc.church.cell.CellType;

/** List view shape for {@code GET /cells} — see {@code 04-api-contracts.md}. */
public record CellSummary(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        CellType type,
        CellMemberRef leader,
        long memberCount,
        String meetingDay,
        String meetingTime,
        String meetingLocation,
        boolean active
) {
}

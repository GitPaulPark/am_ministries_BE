package com.msc.church.cell.dto;

import com.msc.church.cell.CellType;

/**
 * Slim list item — V1 only ships the read endpoint that the Member form needs. The
 * full Cells module (Week 5) will introduce richer responses with rosters and
 * meeting info.
 */
public record CellListItem(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        CellType type,
        boolean active
) {
}

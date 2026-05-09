package com.msc.church.bulletin.dto;

import com.msc.church.cell.CellType;

/** Slim cell reference embedded in bulletin responses. */
public record CellRef(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        CellType type
) {
}

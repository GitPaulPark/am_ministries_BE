package com.msc.church.cell.dto;

/** Slim member reference inside cell responses (leader, roster entries). */
public record CellMemberRef(
        Long id,
        String nameKr,
        String nameEn
) {
}

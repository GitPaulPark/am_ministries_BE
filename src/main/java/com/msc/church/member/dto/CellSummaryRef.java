package com.msc.church.member.dto;

/** Slim cell reference embedded in member responses. */
public record CellSummaryRef(
        Long id,
        String code,
        String nameKr,
        String nameEn
) {
}

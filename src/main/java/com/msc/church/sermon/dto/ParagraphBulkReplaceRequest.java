package com.msc.church.sermon.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Bulk-replace payload: ordered list of paragraphs. Server diffs against the
 * existing rows, preserves ids when present, inserts new rows for input items
 * without an id, deletes any existing row not referenced. Order is implied by
 * the array index; the client doesn't send {@code orderIdx}.
 */
public record ParagraphBulkReplaceRequest(
        @NotNull List<Input> paragraphs
) {
    public record Input(
            Long id,                       // null for new rows
            Integer sectionIdx,
            String sectionTitleKr,
            String sectionTitleEn,
            @NotNull String kind,          // paragraph | scripture | section_heading | preacher_meta
            @NotNull String language,      // kr | en
            @NotNull String text,
            String scriptureRef,
            String pairKey                 // client may keep, clear, or regenerate
    ) {}
}

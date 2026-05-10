package com.msc.church.sermon.dto;

import java.util.List;

/**
 * Render-ready transcript shape sent to the bilingual reader. Paragraphs are
 * collapsed into blocks: an EN/KR translation pair becomes one block carrying
 * both languages; a standalone paragraph keeps just the language it has.
 */
public record SermonTranscriptResponse(
        boolean singleLanguage,
        String pdfUrl,
        List<Section> sections) {

    public record Section(
            int idx,
            String titleKr,
            String titleEn,
            List<Block> blocks) {
    }

    /**
     * @param kind one of {@code paragraph} | {@code scripture} | {@code section_heading}
     * @param refKr scripture reference in Korean (only for {@code scripture})
     * @param refEn scripture reference in English (only for {@code scripture})
     */
    public record Block(
            String kind,
            String textKr,
            String textEn,
            String refKr,
            String refEn) {
    }
}

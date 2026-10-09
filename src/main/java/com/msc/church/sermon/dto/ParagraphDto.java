package com.msc.church.sermon.dto;

/** One paragraph row as exposed to the admin transcript editor. */
public record ParagraphDto(
        Long id,
        int orderIdx,
        Integer sectionIdx,
        String sectionTitleKr,
        String sectionTitleEn,
        String kind,                // paragraph | scripture | section_heading | preacher_meta
        String language,            // kr | en
        String text,
        String scriptureRef,
        String pairKey
) {}

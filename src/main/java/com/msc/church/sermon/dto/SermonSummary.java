package com.msc.church.sermon.dto;

import java.time.LocalDate;

public record SermonSummary(
        Long id,
        LocalDate sermonDate,
        String titleKr,
        String titleEn,
        String scriptureRef,
        SermonMemberRef preacher,
        String preacherNameLabel,
        String theme,
        boolean hasAudio,
        boolean published
) {
}

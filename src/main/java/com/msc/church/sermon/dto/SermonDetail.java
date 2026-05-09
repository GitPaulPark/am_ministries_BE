package com.msc.church.sermon.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SermonDetail(
        Long id,
        LocalDate sermonDate,
        String titleKr,
        String titleEn,
        SermonMemberRef preacher,
        String preacherNameLabel,
        String scriptureRef,
        String scriptureTextKr,
        String scriptureTextEn,
        String audioUrl,
        String videoUrl,
        String transcriptKr,
        String transcriptEn,
        String theme,
        List<String> cellReflectionQuestions,
        SermonBulletinRef linkedBulletin,
        boolean published,
        LocalDateTime publishedAt
) {
}

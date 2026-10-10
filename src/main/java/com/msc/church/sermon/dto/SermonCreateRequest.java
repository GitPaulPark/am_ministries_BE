package com.msc.church.sermon.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SermonCreateRequest(
        @NotNull LocalDate sermonDate,
        @Size(max = 255) String titleKr,
        @Size(max = 255) String titleEn,
        Long preacherMemberId,
        /** Freeform preacher name for external speakers / guests. Accepts the
         *  older {@code externalPreacherName} field name too — some clients
         *  still send that; silently ignoring was worse than aliasing. */
        @JsonAlias("externalPreacherName")
        @Size(max = 100) String preacherNameLabel,
        @NotBlank @Size(max = 100) String scriptureRef,
        String scriptureTextKr,
        String scriptureTextEn,
        String videoUrl,
        String transcriptKr,
        String transcriptEn,
        @Size(max = 255) String theme,
        List<String> cellReflectionQuestions,
        Long bulletinId
) {
}

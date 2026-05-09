package com.msc.church.meeting.dto;

public record CommitteeSummary(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        long memberCount,
        boolean active
) {
}

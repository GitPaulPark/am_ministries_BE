package com.msc.church.bulletin.dto;

import java.time.LocalDate;

/** List view shape — see {@code 04-api-contracts.md}. */
public record BulletinSummary(
        Long id,
        LocalDate serviceDate,
        String theme,
        MemberRef presider,
        boolean published
) {
}

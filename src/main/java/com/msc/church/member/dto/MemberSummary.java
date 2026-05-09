package com.msc.church.member.dto;

import com.msc.church.member.MemberStatus;

/** List view shape — see {@code 04-api-contracts.md}. */
public record MemberSummary(
        Long id,
        String nameKr,
        String nameEn,
        String roleLabel,
        CellSummaryRef primaryCell,
        String phone,
        MemberStatus status
) {
}

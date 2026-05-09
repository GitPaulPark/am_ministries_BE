package com.msc.church.member.dto;

import com.msc.church.member.MemberStatus;

import java.time.LocalDate;
import java.util.List;

/** Detail view shape. {@code notes} is null for non-pastor callers. */
public record MemberDetail(
        Long id,
        String nameKr,
        String nameEn,
        String email,
        String phone,
        String roleLabel,
        LocalDate birthdate,
        String gender,
        boolean baptized,
        LocalDate baptizedAt,
        LocalDate joinedAt,
        MemberStatus status,
        String preferredLocale,
        List<MembershipSummary> memberships,
        String notes
) {
}

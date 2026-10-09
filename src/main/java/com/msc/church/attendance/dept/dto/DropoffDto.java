package com.msc.church.attendance.dept.dto;

import java.time.LocalDate;

/**
 * One "concerning absence" row for the dashboard: a member who has been absent
 * for {@code consecutiveMissed} most-recent events in this committee.
 */
public record DropoffDto(
        Long committeeId,
        String committeeNameKr,
        String committeeNameEn,
        Long memberId,
        String memberNameKr,
        String memberNameEn,
        int consecutiveMissed,
        LocalDate lastEventDate
) {}

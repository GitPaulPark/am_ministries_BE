package com.msc.church.meeting.dto;

import com.msc.church.meeting.CommitteeRole;

import java.time.LocalDate;

public record CommitteeMembershipResponse(
        Long id,
        Long committeeId,
        Long memberId,
        String memberNameKr,
        String memberNameEn,
        CommitteeRole role,
        LocalDate joinedAt,
        boolean active
) {
}

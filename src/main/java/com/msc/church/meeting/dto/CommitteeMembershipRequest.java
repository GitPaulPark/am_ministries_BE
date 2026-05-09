package com.msc.church.meeting.dto;

import com.msc.church.meeting.CommitteeRole;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CommitteeMembershipRequest(
        @NotNull Long memberId,
        @NotNull CommitteeRole role,
        LocalDate joinedAt
) {
}

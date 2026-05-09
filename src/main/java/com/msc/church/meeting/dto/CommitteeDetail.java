package com.msc.church.meeting.dto;

import java.util.List;

public record CommitteeDetail(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        String description,
        boolean active,
        List<CommitteeMembershipResponse> members
) {
}

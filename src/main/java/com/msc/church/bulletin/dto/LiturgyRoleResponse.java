package com.msc.church.bulletin.dto;

import com.msc.church.bulletin.LiturgyRoleType;

public record LiturgyRoleResponse(
        Long id,
        LiturgyRoleType roleType,
        String title,
        MemberRef assigneeMember,
        CellRef assigneeCell,
        String assigneeLabel,
        Integer orderIdx,
        boolean standing
) {
}

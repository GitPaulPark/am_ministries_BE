package com.msc.church.bulletin.dto;

import com.msc.church.bulletin.LiturgyRoleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One row of the liturgy in a create/update request. The service layer enforces
 * "exactly one of (assigneeMemberId, assigneeCellId, assigneeLabel) is set" — the
 * DB allows all three to be null (e.g. a default Lord's Prayer with no specific
 * leader).
 */
public record LiturgyRoleRequest(
        @NotNull LiturgyRoleType roleType,
        @Size(max = 255) String title,
        Long assigneeMemberId,
        Long assigneeCellId,
        @Size(max = 255) String assigneeLabel,
        @NotNull Integer orderIdx,
        boolean standing
) {
}

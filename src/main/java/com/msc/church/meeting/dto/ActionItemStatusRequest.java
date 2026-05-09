package com.msc.church.meeting.dto;

import com.msc.church.meeting.ActionItemStatus;
import jakarta.validation.constraints.NotNull;

public record ActionItemStatusRequest(
        @NotNull ActionItemStatus status,
        String notes
) {
}

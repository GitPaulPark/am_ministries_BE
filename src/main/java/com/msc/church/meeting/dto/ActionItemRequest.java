package com.msc.church.meeting.dto;

import com.msc.church.meeting.ActionItemStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ActionItemRequest(
        @NotBlank String description,
        Long assigneeMemberId,
        LocalDate dueDate,
        ActionItemStatus status,
        String notes,
        Integer orderIdx
) {
}

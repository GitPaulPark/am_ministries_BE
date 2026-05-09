package com.msc.church.meeting.dto;

import com.msc.church.meeting.ActionItemStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActionItemResponse(
        Long id,
        Long meetingId,
        LocalDate meetingDate,
        CommitteeRef committee,
        String description,
        MemberRef assignee,
        LocalDate dueDate,
        ActionItemStatus status,
        LocalDateTime completedAt,
        String notes,
        Integer orderIdx
) {
}

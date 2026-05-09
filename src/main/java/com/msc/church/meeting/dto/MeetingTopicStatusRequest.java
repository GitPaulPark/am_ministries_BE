package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingTopicStatus;
import jakarta.validation.constraints.NotNull;

public record MeetingTopicStatusRequest(
        @NotNull MeetingTopicStatus status,
        String comment) {
}

package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingTopicStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeetingTopicCreateRequest(
        @NotBlank @Size(max = 500) String title,
        String summary,
        String decision,
        MeetingTopicStatus status,
        String comment,
        Integer orderIdx,
        Long parentTopicId) {
}

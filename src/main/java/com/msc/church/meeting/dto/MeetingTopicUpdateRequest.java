package com.msc.church.meeting.dto;

import com.msc.church.meeting.MeetingTopicStatus;
import jakarta.validation.constraints.Size;

/**
 * Partial update — null means "leave as is" except for {@code status}, which always
 * replaces (the user picks one from the enum). For pure status flips and comments,
 * {@link com.msc.church.meeting.MeetingTopicController}'s status / comment endpoints
 * are simpler.
 */
public record MeetingTopicUpdateRequest(
        @Size(max = 500) String title,
        String summary,
        String decision,
        MeetingTopicStatus status,
        String comment,
        Integer orderIdx) {
}

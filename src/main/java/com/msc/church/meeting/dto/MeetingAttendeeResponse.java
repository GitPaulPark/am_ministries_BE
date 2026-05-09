package com.msc.church.meeting.dto;

public record MeetingAttendeeResponse(
        Long id,
        Long memberId,
        String memberNameKr,
        String memberNameEn,
        boolean attended
) {
}

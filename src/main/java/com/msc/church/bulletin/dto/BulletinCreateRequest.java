package com.msc.church.bulletin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record BulletinCreateRequest(
        @NotNull LocalDate serviceDate,
        Long presiderMemberId,
        @Size(max = 255) String theme,
        @Size(max = 255) String songOfWeekTitle,
        String songOfWeekLyrics,
        @Size(max = 50) String memoryVerseRef,
        String memoryVerseTextKr,
        String memoryVerseTextEn,
        Long nextWeekPrayerMemberId,
        @Valid List<LiturgyRoleRequest> liturgyRoles,
        @Valid List<AnnouncementRequest> announcements,
        @Valid List<PrayerItemRequest> prayerItems,
        @Valid List<CellAttendanceRequest> cellAttendance
) {
}

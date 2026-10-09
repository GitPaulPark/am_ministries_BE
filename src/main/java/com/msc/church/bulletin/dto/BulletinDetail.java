package com.msc.church.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BulletinDetail(
        Long id,
        LocalDate serviceDate,
        String theme,
        MemberRef presider,
        String songOfWeekTitle,
        String songOfWeekLyrics,
        String memoryVerseRef,
        String memoryVerseTextKr,
        String memoryVerseTextEn,
        String pdfUrl,
        MemberRef nextWeekPrayer,
        List<LiturgyRoleResponse> liturgyRoles,
        List<AnnouncementResponse> announcements,
        List<PrayerItemResponse> prayerItems,
        List<CellAttendanceResponse> cellAttendance,
        boolean published,
        LocalDateTime publishedAt
) {
}

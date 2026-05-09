package com.msc.church.bulletin.dto;

import com.msc.church.bulletin.PrayerCategory;

public record PrayerItemResponse(
        Long id,
        PrayerCategory category,
        String contentKr,
        String contentEn,
        Integer orderIdx
) {
}

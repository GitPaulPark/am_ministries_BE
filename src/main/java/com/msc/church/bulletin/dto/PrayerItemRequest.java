package com.msc.church.bulletin.dto;

import com.msc.church.bulletin.PrayerCategory;
import jakarta.validation.constraints.NotNull;

public record PrayerItemRequest(
        @NotNull PrayerCategory category,
        String contentKr,
        String contentEn,
        @NotNull Integer orderIdx
) {
}

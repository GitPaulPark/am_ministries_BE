package com.msc.church.bulletin.dto;

import jakarta.validation.constraints.NotNull;

public record AnnouncementRequest(
        String contentKr,
        String contentEn,
        @NotNull Integer orderIdx
) {
}

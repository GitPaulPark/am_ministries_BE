package com.msc.church.bulletin.dto;

public record AnnouncementResponse(
        Long id,
        String contentKr,
        String contentEn,
        Integer orderIdx
) {
}

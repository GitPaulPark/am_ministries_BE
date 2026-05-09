package com.msc.church.bulletin.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BulletinDuplicateRequest(
        @NotNull LocalDate newServiceDate
) {
}

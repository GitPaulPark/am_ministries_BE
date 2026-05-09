package com.msc.church.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommitteeCreateRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String nameKr,
        @Size(max = 100) String nameEn,
        String description
) {
}

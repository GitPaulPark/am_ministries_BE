package com.msc.church.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommitteeUpdateRequest(
        @NotBlank @Size(max = 100) String nameKr,
        @Size(max = 100) String nameEn,
        String description,
        boolean active
) {
}

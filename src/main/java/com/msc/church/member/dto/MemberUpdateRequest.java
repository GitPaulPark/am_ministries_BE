package com.msc.church.member.dto;

import com.msc.church.member.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MemberUpdateRequest(
        @NotBlank @Size(max = 100) String nameKr,
        @Size(max = 100) String nameEn,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 50) String roleLabel,
        LocalDate birthdate,
        @Pattern(regexp = "M|F", message = "must be M or F") String gender,
        LocalDate joinedAt,
        @NotNull MemberStatus status,
        boolean baptized,
        LocalDate baptizedAt,
        Long primaryCellId,
        String preferredLocale,
        String notes
) {
}

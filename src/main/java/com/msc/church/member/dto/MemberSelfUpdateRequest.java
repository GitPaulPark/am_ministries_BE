package com.msc.church.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Limited self-edit shape per the module spec — phone, email, preferredLocale only. */
public record MemberSelfUpdateRequest(
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Pattern(regexp = "ko|en", message = "must be ko or en") String preferredLocale
) {
}

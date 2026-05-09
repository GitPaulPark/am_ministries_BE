package com.msc.church.auth.dto;

/**
 * Slim member info embedded in {@link AuthUserResponse}. Frontend wants enough to
 * greet the user; full detail is fetched separately when needed.
 */
public record AuthMemberRef(
        Long id,
        String nameKr,
        String nameEn
) {
}

package com.msc.church.auth.dto;

public record LoginResponse(
        String accessToken,
        AuthUserResponse user,
        /** True when the user must change their password before using the app. */
        boolean passwordChangeRequired
) {
}

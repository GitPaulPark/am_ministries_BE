package com.msc.church.auth.dto;

public record LoginResponse(
        String accessToken,
        AuthUserResponse user
) {
}

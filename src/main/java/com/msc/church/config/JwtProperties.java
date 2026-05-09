package com.msc.church.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code jwt.*} in application.yml. Secret comes from an env var so it is
 * never committed. Keep it ≥ 32 bytes (HS256 needs a 256-bit key).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}

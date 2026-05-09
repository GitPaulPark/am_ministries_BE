package com.msc.church.auth;

import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies JWTs. Access tokens are short-lived (15m default), refresh tokens
 * are longer (7d) and only their SHA-256 hash is stored in {@code refresh_tokens} so a
 * DB leak doesn't expose live tokens.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private final SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = decodeSecret(props.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Issues an opaque-feeling refresh JWT. Use {@link #hashRefreshToken(String)} on the
     * returned string before persisting; never store the raw token.
     */
    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.refreshTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public ParsedToken parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public ParsedToken parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private ParsedToken parse(String token, String expectedType) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(props.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TYPE, String.class);
            if (!expectedType.equals(type)) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            return new ParsedToken(userId, email, role);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token parse failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** SHA-256 hex digest, suitable for the {@code refresh_tokens.token_hash} column. */
    public String hashRefreshToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] decodeSecret(String secret) {
        // Accept either base64 or raw text; longer raw text is fine for HS256 too.
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public record ParsedToken(Long userId, String email, String role) {
    }

    // unused but kept so future modules don't reach for raw Map building
    @SuppressWarnings("unused")
    private Map<String, Object> empty() {
        return Map.of();
    }
}

package com.msc.church.auth;

import com.msc.church.auth.dto.AuthMemberRef;
import com.msc.church.auth.dto.AuthUserResponse;
import com.msc.church.auth.dto.LoginRequest;
import com.msc.church.auth.dto.LoginResponse;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Login / refresh / logout. The HTTP cookie wiring lives in {@link AuthController}; this
 * service deals only in opaque token strings so it can be exercised from tests without
 * a servlet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record TokenBundle(String accessToken, String refreshToken, AuthUserResponse user,
                              boolean passwordChangeRequired) {
    }

    @Transactional
    public TokenBundle login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("invalid"));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid");
        }

        user.setLastLoginAt(LocalDateTime.now());
        return issueTokens(user);
    }

    @Transactional
    public TokenBundle refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        JwtService.ParsedToken parsed;
        try {
            parsed = jwtService.parseRefreshToken(refreshToken);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String hash = jwtService.hashRefreshToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Rotate: revoke old, issue new pair.
        stored.setRevoked(true);

        User user = userRepository.findById(parsed.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        if (userId != null) {
            refreshTokenRepository.revokeAllForUser(userId);
        }
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return toUserResponse(user);
    }

    /**
     * Verifies the current password, sets a new BCrypt-hashed one, and revokes all
     * refresh tokens so the user has to re-authenticate on other devices.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // A successful change clears the forced-change flag — the gate stays set
        // on bootstrap/seed accounts until the real human picks a password.
        user.setPasswordChangeRequired(false);
        refreshTokenRepository.revokeAllForUser(userId);
        log.info("Password changed: userId={}", userId);
    }

    // -----------------------------------------------------------------

    private TokenBundle issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(), user.getMemberId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken stored = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(jwtService.hashRefreshToken(refreshToken))
                .expiresAt(LocalDateTime.ofInstant(
                        java.time.Instant.now().plus(java.time.Duration.ofDays(7)), ZoneOffset.UTC))
                .revoked(false)
                .build();
        refreshTokenRepository.save(stored);

        return new TokenBundle(accessToken, refreshToken, toUserResponse(user),
                user.isPasswordChangeRequired());
    }

    private AuthUserResponse toUserResponse(User user) {
        AuthMemberRef memberRef = null;
        if (user.getMemberId() != null) {
            memberRef = memberRepository.findById(user.getMemberId())
                    .map(this::toMemberRef)
                    .orElse(null);
        }
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getRole(), memberRef);
    }

    private AuthMemberRef toMemberRef(Member m) {
        return new AuthMemberRef(m.getId(), m.getNameKr(), m.getNameEn());
    }
}

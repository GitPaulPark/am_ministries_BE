package com.msc.church.auth;

import com.msc.church.auth.dto.AuthUserResponse;
import com.msc.church.auth.dto.ChangePasswordRequest;
import com.msc.church.auth.dto.LoginRequest;
import com.msc.church.auth.dto.LoginResponse;
import com.msc.church.auth.dto.LogoutResponse;
import com.msc.church.common.ApiResponse;
import com.msc.church.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @Value("${app.cookies.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        AuthService.TokenBundle bundle = authService.login(request);
        writeRefreshCookie(response, bundle.refreshToken());
        log.info("Login success: userId={}", bundle.user().id());
        return ApiResponse.ok(new LoginResponse(bundle.accessToken(), bundle.user()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                              HttpServletResponse response) {
        AuthService.TokenBundle bundle = authService.refresh(refreshToken);
        writeRefreshCookie(response, bundle.refreshToken());
        return ApiResponse.ok(new LoginResponse(bundle.accessToken(), bundle.user()));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(HttpServletRequest request,
                                              HttpServletResponse response) {
        // Logout doesn't strictly need an auth header — we'll revoke whatever the
        // refresh cookie names (best-effort) and clear the cookie either way.
        Long userId = SecurityUtil.currentAuthentication()
                .map(a -> {
                    Object p = a.getPrincipal();
                    if (p instanceof AuthenticatedUser au) return au.id();
                    if (p instanceof Long l) return l;
                    return null;
                })
                .orElse(null);
        authService.logout(userId);
        clearRefreshCookie(response);
        return ApiResponse.ok(new LogoutResponse("Logged out"));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.ok(authService.me(userId));
    }

    @PatchMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletResponse response) {
        Long userId = SecurityUtil.currentUserId();
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        // Old refresh tokens were just revoked — clear the cookie so the next /refresh
        // returns 401 cleanly instead of trying a stale token.
        clearRefreshCookie(response);
        return ApiResponse.ok();
    }

    private void writeRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) jwtProperties.refreshTokenTtl().toSeconds());
        // Servlet 6: SameSite via response header (no Cookie API).
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", buildSameSiteCookie(token));
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * Builds the same Set-Cookie header twice — once via the Servlet API (no SameSite
     * support pre-Servlet 6.1) and once raw with SameSite=Strict so modern browsers
     * apply the stricter rule. The duplicate is harmless: browsers de-dupe by name.
     */
    private String buildSameSiteCookie(String token) {
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE).append('=').append(token);
        sb.append("; Path=/api/v1/auth");
        sb.append("; Max-Age=").append(jwtProperties.refreshTokenTtl().toSeconds());
        sb.append("; HttpOnly");
        sb.append("; SameSite=Strict");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}

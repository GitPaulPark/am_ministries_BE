package com.msc.church.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msc.church.common.ApiResponse;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <token>}, verifies it, and populates the
 * SecurityContext with the {@link AuthenticatedUser} principal so downstream code
 * can call {@link SecurityUtil#currentUser()} without another DB hit.
 *
 * <p>Auth endpoints (under {@code /api/v1/auth/}) are skipped — the filter only
 * enforces presence of a valid token elsewhere when the SecurityFilterChain demands it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            JwtService.ParsedToken parsed = jwtService.parseAccessToken(token);
            if (parsed.role() == null) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
            AuthenticatedUser principal = new AuthenticatedUser(
                    parsed.userId(),
                    parsed.email(),
                    parsed.memberId(),
                    parsed.role(),
                    true /* enabled — JWT-only check; revocation requires re-login */
            );
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ex.getErrorCode());
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        // Only the public auth endpoints skip the filter; /me and /logout still
        // run through it so SecurityUtil.currentUser() is populated.
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/actuator/health");
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Frontend localizes from errorCode; we don't have a Locale here cheaply.
        var body = ApiResponse.error(code, code.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

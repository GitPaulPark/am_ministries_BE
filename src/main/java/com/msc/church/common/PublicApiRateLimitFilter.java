package com.msc.church.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for the public landing API. One bucket per client IP,
 * configurable requests-per-window; 429 on overflow with a {@code Retry-After}
 * header so a well-behaved scraper backs off instead of hammering the DB.
 *
 * <p>Scope is intentionally narrow — only paths under {@code /api/v1/public/**}
 * are counted. Authenticated endpoints have their own natural throttle (an
 * attacker burning them has to acquire a token first) and the WebConfig static
 * handler (`/uploads/**`) is served by nginx/CloudFront in prod anyway.
 *
 * <p>Behind an ALB / nginx the client IP arrives in {@code X-Forwarded-For};
 * take the first entry (the original client). When running without a proxy
 * (dev + direct curl against :8080) we fall back to {@code remoteAddr}.
 *
 * <p>Memory: ~100 bytes per unique IP in the window. A sweeper reaps stale
 * entries lazily on each window reset — no scheduled thread needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/public/";

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.public.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.public.max-requests:60}")
    private int maxRequests;

    @Value("${app.rate-limit.public.window-seconds:60}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !req.getRequestURI().startsWith(PATH_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String ip = clientIp(req);
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        Window window = buckets.compute(ip, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMs) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });

        int remaining = Math.max(0, maxRequests - window.count);
        res.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (window.count > maxRequests) {
            long resetInSeconds = Math.max(1, (window.start + windowMs - now) / 1000);
            res.setStatus(ErrorCode.RATE_LIMITED.getStatus().value());
            res.setHeader("Retry-After", String.valueOf(resetInSeconds));
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String message = messageSource.getMessage(
                    ErrorCode.RATE_LIMITED.getMessageKey(),
                    new Object[]{resetInSeconds},
                    ErrorCode.RATE_LIMITED.getMessageKey(),
                    LocaleContextHolder.getLocale());
            objectMapper.writeValue(res.getOutputStream(), ApiResponse.error(ErrorCode.RATE_LIMITED, message));
            log.warn("Rate limited: ip={} path={} count={}/{} window={}s",
                    ip, req.getRequestURI(), window.count, maxRequests, windowSeconds);
            return;
        }

        // Opportunistic cleanup — purge stale windows on each successful request so
        // the map doesn't grow unbounded. Caps at ~5000 entries before forcing a
        // sweep; a church landing page won't realistically exceed that.
        if (buckets.size() > 5000) {
            buckets.entrySet().removeIf(e -> now - e.getValue().start >= windowMs);
        }

        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            // First entry = original client; proxies append on the right.
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    /** Mutable per-IP state. Volatile count because reads from the response path
     *  are not strictly synchronised with the compute() writes. */
    private static final class Window {
        final long start;
        volatile int count;
        Window(long start, int count) { this.start = start; this.count = count; }
    }
}

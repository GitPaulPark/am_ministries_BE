package com.msc.church.auth;

import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * One place to ask "who is calling?". The {@link com.msc.church.config.SecurityConfig}
 * pipeline ensures that for any authenticated request the principal is either an
 * {@link AuthenticatedUser} (full /api/v1/* request) or a numeric Long (carried by the
 * JWT filter when richer info was not loaded). For controller-level decisions prefer
 * {@link #currentUserId()}; for service-level role-aware filters call
 * {@link #currentUser()}.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Optional<Authentication> currentAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated);
    }

    public static Long currentUserId() {
        return currentAuthentication()
                .map(Authentication::getPrincipal)
                .map(SecurityUtil::extractUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public static AuthenticatedUser currentUser() {
        Object principal = currentAuthentication()
                .map(Authentication::getPrincipal)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (principal instanceof AuthenticatedUser au) {
            return au;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private static Long extractUserId(Object principal) {
        if (principal instanceof AuthenticatedUser au) {
            return au.id();
        }
        if (principal instanceof Long l) {
            return l;
        }
        if (principal instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}

package com.msc.church.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal. {@link #getUsername()} returns the user id (as String) so
 * {@code Authentication.getName()} ⇒ user id everywhere. Email and memberId are kept
 * as fields for service code that needs them; downstream callers should prefer
 * {@code SecurityUtil.currentUser()} so the cast is centralized.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        Long memberId,
        Role role,
        boolean enabled
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return null; // password is never loaded onto the principal
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

package com.msc.church.auth;

/**
 * Authorization role. Stored as a string in {@code users.role}. Mapped to a
 * {@code ROLE_*} GrantedAuthority by the JWT filter so {@code @PreAuthorize} works.
 */
public enum Role {
    ADMIN,
    PASTOR,
    LEADER,
    MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}

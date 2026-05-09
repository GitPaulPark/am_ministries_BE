package com.msc.church.auth.dto;

import com.msc.church.auth.Role;

public record AuthUserResponse(
        Long id,
        String email,
        Role role,
        AuthMemberRef member
) {
}

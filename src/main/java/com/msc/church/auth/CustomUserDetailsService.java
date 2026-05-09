package com.msc.church.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads users by email at login. Once authenticated the JWT carries the user id;
 * subsequent requests don't hit this service again.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return toPrincipal(user);
    }

    public AuthenticatedUser loadById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException(String.valueOf(userId)));
        return toPrincipal(user);
    }

    private AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getMemberId(),
                user.getRole(),
                user.isEnabled()
        );
    }
}

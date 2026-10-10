package com.msc.church.config;

import com.msc.church.auth.Role;
import com.msc.church.auth.User;
import com.msc.church.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prod bootstrap for the first ADMIN account. Reads
 * {@code INITIAL_ADMIN_EMAIL} / {@code INITIAL_ADMIN_PASSWORD} from the
 * environment and creates one row in {@code users} with
 * {@code password_change_required = true}, so the operator's bootstrap
 * password is only valid long enough to reach the forced change-password
 * screen on first login.
 *
 * <p>Idempotent: skips if any ADMIN already exists. Also skips (with a warn
 * log) if either env var is missing — the service still boots, but there's
 * no way to log in until an admin is inserted manually.
 *
 * <p>Explicitly {@code @Profile("prod")} so dev continues to use
 * {@link DataInitializer} with its seeded cells + known default password.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String initialAdminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String initialAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Prod bootstrap: ADMIN already exists — skipping.");
            return;
        }
        if (isBlank(initialAdminEmail) || isBlank(initialAdminPassword)) {
            log.warn("Prod bootstrap: INITIAL_ADMIN_EMAIL / INITIAL_ADMIN_PASSWORD "
                    + "env vars are not set. The service is running without any ADMIN; "
                    + "insert one manually or redeploy with the env vars set.");
            return;
        }
        User admin = User.builder()
                .email(initialAdminEmail.trim())
                .passwordHash(passwordEncoder.encode(initialAdminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                // The frontend forces /me/password on first login — the env-var
                // password is only alive long enough to reach that screen.
                .passwordChangeRequired(true)
                .memberId(null)
                .build();
        userRepository.save(admin);
        log.warn("Prod bootstrap: created initial ADMIN {} with "
                + "password_change_required=true. Sign in once and change the password immediately.",
                initialAdminEmail);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

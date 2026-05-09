package com.msc.church.config;

import com.msc.church.auth.Role;
import com.msc.church.auth.User;
import com.msc.church.auth.UserRepository;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellRepository;
import com.msc.church.cell.CellType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dev-only bootstrap. On first start, fills the {@code cells} table with the V1 list
 * from {@code files/03-database-schema-v1.md} and seeds a default ADMIN account so a
 * fresh clone goes from zero to "I can log in" in two commands. Idempotent — re-runs
 * are no-ops.
 *
 * <p><strong>Default credentials (change immediately):</strong>
 * <ul>
 *   <li>email: {@code admin@msc.local}</li>
 *   <li>password: {@code ChangeMe123!}</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    public static final String DEFAULT_ADMIN_EMAIL = "admin@msc.local";
    public static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    private final CellRepository cellRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedCells();
        seedAdmin();
    }

    private void seedCells() {
        if (cellRepository.count() > 0) return;
        // V1 cells from the May 3 bulletin / 03-database-schema-v1.md.
        List<Cell> cells = List.of(
                cell("C", "셀 C", "Cell C", CellType.REGULAR),
                cell("D", "셀 D", "Cell D", CellType.REGULAR),
                cell("F", "셀 F", "Cell F", CellType.REGULAR),
                cell("G", "셀 G", "Cell G", CellType.REGULAR),
                cell("Nec", "뉴커머", "Newcomers", CellType.NEWCOMER),
                cell("TeH", "테힐라", "Tehillah", CellType.PRAISE_TEAM),
                cell("Sho", "쇼산나", "Shoshannah", CellType.CHOIR),
                cell("Bcst", "방송팀", "Broadcast", CellType.MEDIA),
                cell("InT", "중보기도팀", "Intercessor", CellType.INTERCESSOR)
        );
        cellRepository.saveAll(cells);
        log.info("Seeded {} cells.", cells.size());
    }

    private void seedAdmin() {
        if (userRepository.existsByRole(Role.ADMIN)) return;
        User admin = User.builder()
                .email(DEFAULT_ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .role(Role.ADMIN)
                .enabled(true)
                .memberId(null)
                .build();
        userRepository.save(admin);
        log.warn("============================================================");
        log.warn(" Seeded default ADMIN — email='{}', password='{}'",
                DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
        log.warn(" CHANGE THIS PASSWORD ON FIRST LOGIN. Dev profile only.");
        log.warn("============================================================");
    }

    private static Cell cell(String code, String nameKr, String nameEn, CellType type) {
        return Cell.builder()
                .code(code)
                .nameKr(nameKr)
                .nameEn(nameEn)
                .type(type)
                .active(true)
                .build();
    }
}

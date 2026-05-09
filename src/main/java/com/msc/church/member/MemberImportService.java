package com.msc.church.member;

import com.msc.church.cell.Cell;
import com.msc.church.cell.CellMembership;
import com.msc.church.cell.CellMembershipService;
import com.msc.church.cell.CellRepository;
import com.msc.church.member.dto.MemberImportError;
import com.msc.church.member.dto.MemberImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV bulk import per the spec. Headers (case-insensitive):
 * <pre>
 *   name_kr, name_en, email, phone, role_label, birthdate, gender,
 *   baptized, baptized_at, joined_at, status, primary_cell_code
 * </pre>
 *
 * <p>Each row is validated and persisted in its own try/catch so a single bad row
 * doesn't abort the rest. Returns a summary with imported / failed counts and a
 * per-row error list. Wrapped in {@code @Transactional} so a server crash mid-import
 * leaves the DB consistent (rollback on uncaught throw); known per-row failures are
 * caught and recorded, not propagated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberImportService {

    private static final String[] EXPECTED_HEADERS = {
            "name_kr", "name_en", "email", "phone", "role_label",
            "birthdate", "gender", "baptized", "baptized_at",
            "joined_at", "status", "primary_cell_code"
    };

    private final MemberRepository memberRepository;
    private final CellRepository cellRepository;
    private final CellMembershipService cellMembershipService;

    @Transactional
    public MemberImportResult importCsv(MultipartFile file) throws IOException {
        int imported = 0;
        int failed = 0;
        List<MemberImportError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                int rowNum = (int) record.getRecordNumber() + 1; // +1 because header is row 1
                try {
                    importRow(record);
                    imported++;
                } catch (RowFailure rf) {
                    failed++;
                    errors.add(new MemberImportError(rowNum, rf.field, rf.getMessage()));
                } catch (Exception e) {
                    failed++;
                    errors.add(new MemberImportError(rowNum, null, e.getMessage()));
                }
            }
        }
        log.info("Member CSV import complete: imported={} failed={}", imported, failed);
        return new MemberImportResult(imported, failed, errors);
    }

    private void importRow(CSVRecord record) {
        String nameKr = trimToNull(get(record, "name_kr"));
        if (nameKr == null) throw new RowFailure("name_kr", "required");

        String email = trimToNull(get(record, "email"));
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new RowFailure("email", "duplicate email");
        }

        String genderRaw = trimToNull(get(record, "gender"));
        if (genderRaw != null && !genderRaw.equalsIgnoreCase("M") && !genderRaw.equalsIgnoreCase("F")) {
            throw new RowFailure("gender", "must be M or F");
        }

        String statusRaw = trimToNull(get(record, "status"));
        MemberStatus status = MemberStatus.ACTIVE;
        if (statusRaw != null) {
            try {
                status = MemberStatus.valueOf(statusRaw.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RowFailure("status", "unknown value: " + statusRaw);
            }
        }

        boolean baptized = parseBool(get(record, "baptized"));
        LocalDate baptizedAt = parseDate(get(record, "baptized_at"), "baptized_at");
        if (baptized && baptizedAt == null) {
            throw new RowFailure("baptized_at", "required when baptized=true");
        }

        Member member = Member.builder()
                .nameKr(nameKr)
                .nameEn(trimToNull(get(record, "name_en")))
                .email(email)
                .phone(trimToNull(get(record, "phone")))
                .roleLabel(trimToNull(get(record, "role_label")))
                .birthdate(parseDate(get(record, "birthdate"), "birthdate"))
                .gender(genderRaw == null ? null : genderRaw.toUpperCase())
                .baptized(baptized)
                .baptizedAt(baptizedAt)
                .joinedAt(parseDate(get(record, "joined_at"), "joined_at"))
                .status(status)
                .build();
        Member saved = memberRepository.save(member);

        String cellCode = trimToNull(get(record, "primary_cell_code"));
        if (cellCode != null) {
            Cell cell = cellRepository.findByCode(cellCode)
                    .orElseThrow(() -> new RowFailure("primary_cell_code", "unknown cell code: " + cellCode));
            cellMembershipService.setPrimary(saved, cell.getId());
        }
    }

    private static String get(CSVRecord r, String header) {
        return r.isMapped(header) ? r.get(header) : null;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean parseBool(String s) {
        String t = trimToNull(s);
        if (t == null) return false;
        return t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("y") || t.equalsIgnoreCase("yes");
    }

    private static LocalDate parseDate(String s, String field) {
        String t = trimToNull(s);
        if (t == null) return null;
        try {
            return LocalDate.parse(t);
        } catch (Exception e) {
            throw new RowFailure(field, "expected ISO date YYYY-MM-DD: " + t);
        }
    }

    /** Row-level reject signal — caught by the importer and turned into an error entry. */
    private static class RowFailure extends RuntimeException {
        final String field;
        RowFailure(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}

package com.msc.church.attendance.sunday;

import com.msc.church.attendance.sunday.dto.SundayAttendanceDto;
import com.msc.church.attendance.sunday.dto.SundayAttendanceRequest;
import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellMembershipRepository;
import com.msc.church.cell.CellNotFoundException;
import com.msc.church.cell.CellRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.CommitteeMembershipRepository;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sunday cell attendance: per-cell roster check-ins on Sunday.
 *
 * <p>Auth model:
 * <ul>
 *   <li>READ: ADMIN/PASTOR, HQ_BOARD members, the cell's leader, or any active cell member.
 *   <li>WRITE: ADMIN/PASTOR, HQ_BOARD members, or the cell's leader.
 *   <li>LOCK: rows lock 30 days after creation; only ADMIN can edit past the lock.
 * </ul>
 *
 * <p>Future: aggregate functions over {@code sunday_attendance_entries.present}
 * will power streaks and attendance rewards. Schema captures the raw data now
 * even though no UI surfaces it yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SundayAttendanceService {

    static final String HQ_BOARD_CODE = "HQ_BOARD";
    static final Duration LOCK_WINDOW = Duration.ofDays(30);

    private final SundayAttendanceRepository attendanceRepository;
    private final CellRepository cellRepository;
    private final CellMembershipRepository cellMembershipRepository;
    private final CommitteeMembershipRepository committeeMembershipRepository;
    private final MemberRepository memberRepository;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public SundayAttendanceDto get(Long id, AuthenticatedUser caller) {
        SundayAttendance att = attendanceRepository.findWithEntriesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertCanRead(att.getCell().getId(), caller);
        return toDto(att);
    }

    @Transactional(readOnly = true)
    public List<SundayAttendanceDto> listByCell(Long cellId, AuthenticatedUser caller) {
        assertCanRead(cellId, caller);
        return attendanceRepository.findByCell_IdOrderByServiceDateDesc(cellId).stream()
                .map(this::toDto)
                .toList();
    }

    // ---------- writes ----------

    @Transactional
    public SundayAttendanceDto upsert(Long cellId, SundayAttendanceRequest req,
                                      AuthenticatedUser caller) {
        assertCanWrite(cellId, caller);

        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new CellNotFoundException(cellId));

        SundayAttendance att = attendanceRepository
                .findByCell_IdAndServiceDate(cellId, req.serviceDate())
                .orElse(null);

        if (att != null) {
            assertNotLocked(att, caller);
        } else {
            att = SundayAttendance.builder()
                    .cell(cell)
                    .serviceDate(req.serviceDate())
                    .entryMode(req.entryMode() == null ? "AFTER" : req.entryMode())
                    .createdBy(caller == null ? null : caller.id())
                    .build();
        }

        att.setEntryMode(req.entryMode() == null ? att.getEntryMode() : req.entryMode());
        att.setNotes(blankToNull(req.notes()));

        Map<Long, SundayAttendanceEntry> existing = new HashMap<>();
        for (SundayAttendanceEntry e : att.getEntries()) {
            existing.put(e.getMember().getId(), e);
        }

        List<SundayAttendanceEntry> newEntries = new java.util.ArrayList<>();
        for (SundayAttendanceRequest.EntryInput in : req.entries()) {
            SundayAttendanceEntry e = existing.remove(in.memberId());
            if (e == null) {
                Member m = memberRepository.findById(in.memberId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                e = SundayAttendanceEntry.builder()
                        .id(new SundayAttendanceEntry.Pk(null, in.memberId()))
                        .attendance(att)
                        .member(m)
                        .present(in.present())
                        .note(blankToNull(in.note()))
                        .build();
            } else {
                e.setPresent(in.present());
                e.setNote(blankToNull(in.note()));
            }
            newEntries.add(e);
        }
        att.getEntries().clear();
        att.getEntries().addAll(newEntries);

        SundayAttendance saved = attendanceRepository.save(att);
        log.info("Sunday attendance upserted: id={} cell={} date={} present={}/{} byUser={}",
                saved.getId(), cellId, req.serviceDate(),
                newEntries.stream().filter(SundayAttendanceEntry::isPresent).count(),
                newEntries.size(), caller == null ? null : caller.id());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        SundayAttendance att = attendanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (caller == null || caller.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        attendanceRepository.delete(att);
        log.info("Sunday attendance deleted: id={} byUser={}", id, caller.id());
    }

    // ---------- auth helpers ----------

    private void assertCanRead(Long cellId, AuthenticatedUser caller) {
        if (caller == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (isStaff(caller)) return;
        if (isHqBoardMember(caller)) return;
        if (caller.memberId() != null) {
            if (isCellLeader(cellId, caller.memberId())) return;
            if (cellMembershipRepository.existsByMember_IdAndCell_IdAndActiveTrue(caller.memberId(), cellId)) return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void assertCanWrite(Long cellId, AuthenticatedUser caller) {
        if (caller == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (isStaff(caller)) return;
        if (isHqBoardMember(caller)) return;
        if (caller.memberId() != null && isCellLeader(cellId, caller.memberId())) return;
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void assertNotLocked(SundayAttendance att, AuthenticatedUser caller) {
        boolean locked = att.getLockedAt() != null
                || (att.getCreatedAt() != null
                    && Duration.between(att.getCreatedAt(), LocalDateTime.now()).compareTo(LOCK_WINDOW) > 0);
        if (locked && (caller == null || caller.role() != Role.ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "locked");
        }
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller.role() == Role.ADMIN || caller.role() == Role.PASTOR;
    }

    private boolean isHqBoardMember(AuthenticatedUser caller) {
        return caller.memberId() != null
                && committeeMembershipRepository
                        .existsByCommittee_CodeAndMember_IdAndActiveTrue(HQ_BOARD_CODE, caller.memberId());
    }

    /** True when the given member is the registered leader of the given cell. */
    private boolean isCellLeader(Long cellId, Long memberId) {
        return cellRepository.findById(cellId)
                .map(c -> c.getLeader() != null && memberId.equals(c.getLeader().getId()))
                .orElse(false);
    }

    // ---------- mapping ----------

    private SundayAttendanceDto toDto(SundayAttendance att) {
        boolean locked = att.getLockedAt() != null
                || (att.getCreatedAt() != null
                    && Duration.between(att.getCreatedAt(), LocalDateTime.now()).compareTo(LOCK_WINDOW) > 0);
        List<SundayAttendanceDto.Entry> entries = att.getEntries().stream()
                .sorted((a, b) -> {
                    String na = a.getMember() == null ? "" : safe(a.getMember().getNameKr());
                    String nb = b.getMember() == null ? "" : safe(b.getMember().getNameKr());
                    return na.compareTo(nb);
                })
                .map(e -> new SundayAttendanceDto.Entry(
                        e.getMember().getId(),
                        e.getMember().getNameKr(),
                        e.getMember().getNameEn(),
                        e.isPresent(),
                        e.getNote()))
                .toList();
        int present = (int) entries.stream().filter(SundayAttendanceDto.Entry::present).count();
        return new SundayAttendanceDto(
                att.getId(),
                att.getCell().getId(),
                att.getCell().getCode(),
                att.getCell().getNameKr(),
                att.getCell().getNameEn(),
                att.getServiceDate(),
                att.getEntryMode(),
                att.getNotes(),
                locked,
                att.getLockedAt(),
                att.getCreatedAt(),
                entries,
                present,
                entries.size());
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

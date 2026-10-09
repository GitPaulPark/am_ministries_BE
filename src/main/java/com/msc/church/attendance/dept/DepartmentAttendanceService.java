package com.msc.church.attendance.dept;

import com.msc.church.attendance.dept.dto.DepartmentAttendanceDto;
import com.msc.church.attendance.dept.dto.DepartmentAttendanceRequest;
import com.msc.church.attendance.dept.dto.DropoffDto;
import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.Committee;
import com.msc.church.meeting.CommitteeMembership;
import com.msc.church.meeting.CommitteeMembershipRepository;
import com.msc.church.meeting.CommitteeNotFoundException;
import com.msc.church.meeting.CommitteeRepository;
import com.msc.church.meeting.CommitteeRole;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Department attendance: per-committee roster check-ins.
 *
 * <p>Auth model (matches AUTH.md / 2026-05-13 discussion):
 * <ul>
 *   <li>READ: ADMIN/PASTOR, HQ_BOARD members, or any active member of the committee.
 *   <li>WRITE: ADMIN/PASTOR, HQ_BOARD members, or CHAIR/SECRETARY of the committee.
 *   <li>LOCK: rows lock 30 days after creation; only ADMIN can edit past the lock.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentAttendanceService {

    static final String HQ_BOARD_CODE = "HQ_BOARD";
    static final Duration LOCK_WINDOW = Duration.ofDays(30);

    private final DepartmentAttendanceRepository attendanceRepository;
    private final CommitteeRepository committeeRepository;
    private final CommitteeMembershipRepository membershipRepository;
    private final MemberRepository memberRepository;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public DepartmentAttendanceDto get(Long id, AuthenticatedUser caller) {
        DepartmentAttendance att = attendanceRepository.findWithEntriesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertCanRead(att.getCommittee().getId(), caller);
        return toDto(att);
    }

    @Transactional(readOnly = true)
    public List<DepartmentAttendanceDto> listByCommittee(Long committeeId, AuthenticatedUser caller) {
        assertCanRead(committeeId, caller);
        return attendanceRepository.findByCommittee_IdOrderByEventDateDesc(committeeId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Dropoffs — members who have been absent for the most recent
     * {@code MIN_CONSECUTIVE} events of a committee the caller can manage.
     *
     * <p>Scope of committees considered:
     * <ul>
     *   <li>ADMIN / PASTOR → all active committees</li>
     *   <li>HQ_BOARD member → all active committees</li>
     *   <li>Otherwise → committees where caller is active CHAIR or SECRETARY</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<DropoffDto> findDropoffs(AuthenticatedUser caller) {
        if (caller == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        final int minConsecutive = 3;
        final int maxResults = 20;

        List<Committee> committees;
        if (isStaff(caller) || isHqBoardMember(caller)) {
            committees = committeeRepository.findByActiveTrue(Sort.by("code"));
        } else if (caller.memberId() != null) {
            committees = membershipRepository.findByMember_IdAndActiveTrue(caller.memberId()).stream()
                    .filter(cm -> cm.getRole() == CommitteeRole.CHAIR || cm.getRole() == CommitteeRole.SECRETARY)
                    .map(CommitteeMembership::getCommittee)
                    .filter(Committee::isActive)
                    .toList();
        } else {
            return List.of();
        }

        List<DropoffDto> out = new java.util.ArrayList<>();
        for (Committee c : committees) {
            // HQ_BOARD itself is a synthetic group — skip dropoff tracking for it.
            if (HQ_BOARD_CODE.equals(c.getCode())) continue;

            List<DepartmentAttendance> events =
                    attendanceRepository.findByCommittee_IdOrderByEventDateDesc(c.getId());

            // Per-member walk from most recent event backward.
            Map<Long, MemberWalk> walks = new HashMap<>();
            for (DepartmentAttendance ev : events) {
                for (DepartmentAttendanceEntry e : ev.getEntries()) {
                    Long mid = e.getMember().getId();
                    MemberWalk w = walks.computeIfAbsent(mid, k -> new MemberWalk(e.getMember()));
                    if (w.streakBroken) continue;
                    if (e.isPresent()) {
                        w.streakBroken = true;
                    } else {
                        w.consecutive++;
                        if (w.lastEventDate == null || ev.getEventDate().isAfter(w.lastEventDate)) {
                            w.lastEventDate = ev.getEventDate();
                        }
                    }
                }
            }
            for (MemberWalk w : walks.values()) {
                if (w.consecutive >= minConsecutive) {
                    out.add(new DropoffDto(c.getId(), c.getNameKr(), c.getNameEn(),
                            w.member.getId(), w.member.getNameKr(), w.member.getNameEn(),
                            w.consecutive, w.lastEventDate));
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.consecutiveMissed(), a.consecutiveMissed()));
        return out.size() > maxResults ? out.subList(0, maxResults) : out;
    }

    private static class MemberWalk {
        final Member member;
        int consecutive = 0;
        boolean streakBroken = false;
        java.time.LocalDate lastEventDate;
        MemberWalk(Member m) { this.member = m; }
    }

    // ---------- writes ----------

    /**
     * Upsert by (committee, eventDate, eventLabel). Replaces all entries.
     */
    @Transactional
    public DepartmentAttendanceDto upsert(Long committeeId, DepartmentAttendanceRequest req,
                                          AuthenticatedUser caller) {
        assertCanWrite(committeeId, caller);

        Committee committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new CommitteeNotFoundException(committeeId));

        String label = req.eventLabel() == null ? "" : req.eventLabel().trim();
        DepartmentAttendance att = attendanceRepository
                .findByCommittee_IdAndEventDateAndEventLabel(committeeId, req.eventDate(), label)
                .orElse(null);

        if (att != null) {
            assertNotLocked(att, caller);
        } else {
            att = DepartmentAttendance.builder()
                    .committee(committee)
                    .eventDate(req.eventDate())
                    .eventLabel(label)
                    .entryMode(req.entryMode() == null ? "AFTER" : req.entryMode())
                    .createdBy(caller == null ? null : caller.id())
                    .build();
        }

        // Update simple fields.
        att.setEntryMode(req.entryMode() == null ? att.getEntryMode() : req.entryMode());
        att.setNotes(blankToNull(req.notes()));

        // Replace entries. Index existing by memberId for in-place mutation.
        Map<Long, DepartmentAttendanceEntry> existing = new HashMap<>();
        for (DepartmentAttendanceEntry e : att.getEntries()) {
            existing.put(e.getMember().getId(), e);
        }

        List<DepartmentAttendanceEntry> newEntries = new java.util.ArrayList<>();
        for (DepartmentAttendanceRequest.EntryInput in : req.entries()) {
            DepartmentAttendanceEntry e = existing.remove(in.memberId());
            if (e == null) {
                Member m = memberRepository.findById(in.memberId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                e = DepartmentAttendanceEntry.builder()
                        .id(new DepartmentAttendanceEntry.Pk(null, in.memberId()))
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
        // Anything left in `existing` was removed from the request → drop it.
        att.getEntries().clear();
        att.getEntries().addAll(newEntries);

        DepartmentAttendance saved = attendanceRepository.save(att);
        log.info("Department attendance upserted: id={} committee={} date={} present={}/{} byUser={}",
                saved.getId(), committeeId, req.eventDate(),
                newEntries.stream().filter(DepartmentAttendanceEntry::isPresent).count(),
                newEntries.size(), caller == null ? null : caller.id());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        DepartmentAttendance att = attendanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // Delete = destructive: ADMIN only.
        if (caller == null || caller.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        attendanceRepository.delete(att);
        log.info("Department attendance deleted: id={} byUser={}", id, caller.id());
    }

    // ---------- auth helpers ----------

    private void assertCanRead(Long committeeId, AuthenticatedUser caller) {
        if (caller == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (isStaff(caller)) return;
        if (isHqBoardMember(caller)) return;
        if (caller.memberId() != null
                && membershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(committeeId, caller.memberId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void assertCanWrite(Long committeeId, AuthenticatedUser caller) {
        if (caller == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (isStaff(caller)) return;
        if (isHqBoardMember(caller)) return;
        if (caller.memberId() != null
                && membershipRepository.isCommitteeOfficer(committeeId, caller.memberId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void assertNotLocked(DepartmentAttendance att, AuthenticatedUser caller) {
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
                && membershipRepository.existsByCommittee_CodeAndMember_IdAndActiveTrue(HQ_BOARD_CODE, caller.memberId());
    }

    // ---------- mapping ----------

    private DepartmentAttendanceDto toDto(DepartmentAttendance att) {
        boolean locked = att.getLockedAt() != null
                || (att.getCreatedAt() != null
                    && Duration.between(att.getCreatedAt(), LocalDateTime.now()).compareTo(LOCK_WINDOW) > 0);
        List<DepartmentAttendanceDto.Entry> entries = att.getEntries().stream()
                .sorted((a, b) -> {
                    String na = a.getMember() == null ? "" : safe(a.getMember().getNameKr());
                    String nb = b.getMember() == null ? "" : safe(b.getMember().getNameKr());
                    return na.compareTo(nb);
                })
                .map(e -> new DepartmentAttendanceDto.Entry(
                        e.getMember().getId(),
                        e.getMember().getNameKr(),
                        e.getMember().getNameEn(),
                        e.isPresent(),
                        e.getNote()))
                .toList();
        int present = (int) entries.stream().filter(DepartmentAttendanceDto.Entry::present).count();
        return new DepartmentAttendanceDto(
                att.getId(),
                att.getCommittee().getId(),
                att.getCommittee().getNameKr(),
                att.getCommittee().getNameEn(),
                att.getEventDate(),
                att.getEventLabel(),
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

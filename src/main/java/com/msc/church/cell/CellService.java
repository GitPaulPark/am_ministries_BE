package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.dto.CellCreateRequest;
import com.msc.church.cell.dto.CellDetail;
import com.msc.church.cell.dto.CellRosterEntry;
import com.msc.church.cell.dto.CellSummary;
import com.msc.church.cell.dto.CellUpdateRequest;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Cell module CRUD and roster lookups. Membership writes live in
 * {@link CellMembershipService} so the membership invariants (single primary, etc.)
 * are concentrated in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CellService {

    private final CellRepository cellRepository;
    private final CellMembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final CellMapper cellMapper;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public List<CellSummary> list(CellType type, boolean includeInactive) {
        Sort sort = Sort.by(Sort.Order.asc("type"), Sort.Order.asc("code"));
        List<Cell> cells;
        if (includeInactive) {
            cells = type == null ? cellRepository.findAll(sort)
                                 : cellRepository.findAll(sort).stream()
                                         .filter(c -> c.getType() == type)
                                         .toList();
        } else {
            cells = type == null ? cellRepository.findByActiveTrue(sort)
                                 : cellRepository.findByActiveTrueAndType(type, sort);
        }
        return cells.stream()
                .map(c -> cellMapper.toSummary(c,
                        membershipRepository.countByCell_IdAndActiveTrue(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CellDetail get(Long id, boolean includeInactiveRoster) {
        Cell cell = cellRepository.findById(id)
                .orElseThrow(() -> new CellNotFoundException(id));
        return toDetailWithRoster(cell, includeInactiveRoster);
    }

    /**
     * Returns the calling user's primary cell with full roster, or {@code null}
     * when the caller has no member record (e.g. admin@msc.local) or no primary
     * cell. Returning null keeps the contract noise-free in the console — the
     * frontend renders an empty state regardless.
     */
    @Transactional(readOnly = true)
    public CellDetail getMyPrimaryCell(AuthenticatedUser caller) {
        if (caller == null || caller.memberId() == null) return null;
        return membershipRepository.findByMember_IdAndPrimaryTrue(caller.memberId())
                .map(cm -> toDetailWithRoster(cm.getCell(), false))
                .orElse(null);
    }

    // ---------- writes ----------

    @Transactional
    public CellDetail create(CellCreateRequest request, AuthenticatedUser caller) {
        if (cellRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCellCodeException(request.code());
        }
        Cell cell = Cell.builder()
                .code(request.code().trim())
                .nameKr(request.nameKr().trim())
                .nameEn(blankToNull(request.nameEn()))
                .type(request.type())
                .leader(loadMember(request.leaderMemberId()))
                .meetingDay(blankToNull(request.meetingDay()))
                .meetingTime(blankToNull(request.meetingTime()))
                .meetingLocation(blankToNull(request.meetingLocation()))
                .description(blankToNull(request.description()))
                .active(true)
                .build();
        Cell saved = cellRepository.save(cell);
        log.info("Cell created: id={} code={} byUser={}",
                saved.getId(), saved.getCode(), callerId(caller));
        return toDetailWithRoster(saved, false);
    }

    @Transactional
    public CellDetail update(Long id, CellUpdateRequest request, AuthenticatedUser caller) {
        Cell cell = cellRepository.findById(id)
                .orElseThrow(() -> new CellNotFoundException(id));

        if (caller != null && caller.role() == Role.LEADER) {
            // LEADER own-cell limited edit: meeting + description only.
            if (caller.memberId() == null
                    || cell.getLeader() == null
                    || !cell.getLeader().getId().equals(caller.memberId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            cell.setMeetingDay(blankToNull(request.meetingDay()));
            cell.setMeetingTime(blankToNull(request.meetingTime()));
            cell.setMeetingLocation(blankToNull(request.meetingLocation()));
            cell.setDescription(blankToNull(request.description()));
        } else {
            cell.setNameKr(request.nameKr().trim());
            cell.setNameEn(blankToNull(request.nameEn()));
            cell.setType(request.type());
            cell.setLeader(loadMember(request.leaderMemberId()));
            cell.setMeetingDay(blankToNull(request.meetingDay()));
            cell.setMeetingTime(blankToNull(request.meetingTime()));
            cell.setMeetingLocation(blankToNull(request.meetingLocation()));
            cell.setDescription(blankToNull(request.description()));
            cell.setActive(request.active());
        }
        log.info("Cell updated: id={} byUser={}", id, callerId(caller));
        return toDetailWithRoster(cell, false);
    }

    /**
     * Soft-deactivate. Refuses if any active primary memberships still reference the
     * cell — admin must reassign each member's primary cell first.
     */
    @Transactional
    public void deactivate(Long id, AuthenticatedUser caller) {
        Cell cell = cellRepository.findById(id)
                .orElseThrow(() -> new CellNotFoundException(id));
        if (membershipRepository.existsByCell_IdAndPrimaryTrueAndActiveTrue(id)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        cell.setActive(false);
        log.info("Cell deactivated: id={} byUser={}", id, callerId(caller));
    }

    // ---------- helpers ----------

    private CellDetail toDetailWithRoster(Cell cell, boolean includeInactive) {
        List<CellMembership> memberships = includeInactive
                ? membershipRepository.findByCell_Id(cell.getId())
                : membershipRepository.findByCell_IdAndActiveTrue(cell.getId());
        List<CellRosterEntry> roster = memberships.stream()
                .sorted(Comparator
                        .<CellMembership, Boolean>comparing(cm -> !cm.isPrimary()) // primaries first
                        .thenComparing(cm -> cm.getMember() == null ? "" : cm.getMember().getNameKr()))
                .map(cellMapper::toRosterEntry)
                .toList();
        return cellMapper.toDetail(cell, roster);
    }

    private Member loadMember(Long id) {
        if (id == null) return null;
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private static Long callerId(AuthenticatedUser caller) {
        return caller == null ? null : caller.id();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

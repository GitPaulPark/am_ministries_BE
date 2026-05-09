package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.cell.dto.CellMembershipCreateRequest;
import com.msc.church.cell.dto.CellMembershipResponse;
import com.msc.church.cell.dto.CellMembershipUpdateRequest;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * All writes to {@code cell_memberships}. The single-primary invariant lives here:
 * {@link #setPrimary} demotes the previous primary in the same transaction so a
 * crash mid-write can't leave a member with two primaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CellMembershipService {

    private final CellRepository cellRepository;
    private final CellMembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final CellMapper cellMapper;

    /**
     * Sets the given cell as the member's primary, demoting any existing primary.
     * Reuses an existing membership row for the (member, cell) pair if present.
     * Used by {@code MemberService.create/update} when {@code primaryCellId} is set.
     */
    @Transactional
    public void setPrimary(Member member, Long cellId) {
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new CellNotFoundException(cellId));

        membershipRepository.clearPrimaryFor(member.getId());

        var existing = member.getMemberships().stream()
                .filter(cm -> cm.getCell() != null && cm.getCell().getId().equals(cellId))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setPrimary(true);
            existing.setActive(true);
            return;
        }

        CellMembership membership = CellMembership.builder()
                .member(member)
                .cell(cell)
                .primary(true)
                .active(true)
                .joinedAt(LocalDate.now())
                .role("MEMBER")
                .build();
        membershipRepository.save(membership);
        member.getMemberships().add(membership);
    }

    @Transactional
    public CellMembershipResponse addMembership(Long cellId,
                                                CellMembershipCreateRequest request,
                                                AuthenticatedUser caller) {
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new CellNotFoundException(cellId));
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (membershipRepository.existsByMember_IdAndCell_IdAndActiveTrue(member.getId(), cellId)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (request.isPrimary()) {
            membershipRepository.clearPrimaryFor(member.getId());
        }

        CellMembership membership = CellMembership.builder()
                .member(member)
                .cell(cell)
                .primary(request.isPrimary())
                .role(blankToNull(request.role()) == null ? "MEMBER" : request.role())
                .joinedAt(request.joinedAt() == null ? LocalDate.now() : request.joinedAt())
                .active(true)
                .build();
        CellMembership saved = membershipRepository.save(membership);
        log.info("Membership added: cell={} member={} byUser={}",
                cellId, member.getId(), callerId(caller));
        return cellMapper.toMembershipResponse(saved);
    }

    @Transactional
    public CellMembershipResponse updateMembership(Long membershipId,
                                                   CellMembershipUpdateRequest request,
                                                   AuthenticatedUser caller) {
        CellMembership cm = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new CellMembershipNotFoundException(membershipId));

        if (request.role() != null) cm.setRole(blankToNull(request.role()));
        if (request.active() != null) cm.setActive(request.active());

        if (Boolean.TRUE.equals(request.isPrimary())) {
            membershipRepository.clearPrimaryFor(cm.getMember().getId());
            cm.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.isPrimary())) {
            cm.setPrimary(false);
        }
        log.info("Membership updated: id={} byUser={}", membershipId, callerId(caller));
        return cellMapper.toMembershipResponse(cm);
    }

    @Transactional
    public void removeMembership(Long membershipId, AuthenticatedUser caller) {
        CellMembership cm = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new CellMembershipNotFoundException(membershipId));
        cm.setActive(false);
        cm.setLeftAt(LocalDate.now());
        // Removing a primary leaves the member with no primary. Caller must assign a
        // new one — UI prompts for it; backend doesn't try to guess.
        cm.setPrimary(false);
        log.info("Membership removed: id={} byUser={}", membershipId, callerId(caller));
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

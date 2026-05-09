package com.msc.church.member;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.CellMembershipService;
import com.msc.church.cell.CellRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.dto.MemberCreateRequest;
import com.msc.church.member.dto.MemberDetail;
import com.msc.church.member.dto.MemberSelfUpdateRequest;
import com.msc.church.member.dto.MemberSummary;
import com.msc.church.member.dto.MemberUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical module service. All future modules should mirror this shape:
 * <ul>
 *   <li>constructor-injected collaborators</li>
 *   <li>read-only methods marked {@code @Transactional(readOnly=true)}</li>
 *   <li>writes guarded by validation + custom exceptions</li>
 *   <li>role-aware filtering done here, not in the controller</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final CellRepository cellRepository;
    private final CellMembershipService cellMembershipService;
    private final MemberMapper memberMapper;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<MemberSummary> list(MemberListFilter filter, Pageable pageable, AuthenticatedUser caller) {
        Specification<Member> spec = Specification
                .where(MemberSpecifications.matchesQuery(filter.q()))
                .and(MemberSpecifications.hasStatus(filter.status()))
                .and(MemberSpecifications.hasRoleLabel(filter.roleLabel()))
                .and(MemberSpecifications.inPrimaryCell(filter.cellId()));

        // LEADER scoping: restrict to members of their own primary cell.
        // Implementation defer until Cells module ships full membership APIs (Week 5);
        // for now only ADMIN / PASTOR reach this code (controller-level guard).
        if (caller != null && caller.role() == Role.LEADER) {
            spec = spec.and(MemberSpecifications.inPrimaryCell(filter.cellId()));
        }

        return memberRepository.findAll(spec, pageable).map(memberMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public MemberDetail get(Long id, AuthenticatedUser caller) {
        Member member = memberRepository.findWithMembershipsById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
        enforceReadAccess(member, caller);
        return memberMapper.toDetail(member, canSeePastorNotes(caller));
    }

    // ---------- writes ----------

    @Transactional
    public MemberDetail create(MemberCreateRequest request, AuthenticatedUser caller) {
        if (request.email() != null && memberRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        Member member = Member.builder()
                .nameKr(request.nameKr().trim())
                .nameEn(blankToNull(request.nameEn()))
                .email(blankToNull(request.email()))
                .phone(blankToNull(request.phone()))
                .roleLabel(blankToNull(request.roleLabel()))
                .birthdate(request.birthdate())
                .gender(blankToNull(request.gender()))
                .joinedAt(request.joinedAt())
                .baptized(false)
                .status(MemberStatus.ACTIVE)
                .build();

        Member saved = memberRepository.save(member);

        if (request.primaryCellId() != null) {
            cellMembershipService.setPrimary(saved, request.primaryCellId());
        }

        log.info("Member created: id={} byUser={}", saved.getId(),
                caller != null ? caller.id() : null);

        // Re-load with memberships so the response includes the freshly assigned cell.
        Member full = memberRepository.findWithMembershipsById(saved.getId()).orElse(saved);
        return memberMapper.toDetail(full, canSeePastorNotes(caller));
    }

    @Transactional
    public MemberDetail update(Long id, MemberUpdateRequest request, AuthenticatedUser caller) {
        Member member = memberRepository.findWithMembershipsById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));

        if (request.email() != null
                && memberRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateEmailException(request.email());
        }

        if (request.baptized() && request.baptizedAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        member.setNameKr(request.nameKr().trim());
        member.setNameEn(blankToNull(request.nameEn()));
        member.setEmail(blankToNull(request.email()));
        member.setPhone(blankToNull(request.phone()));
        member.setRoleLabel(blankToNull(request.roleLabel()));
        member.setBirthdate(request.birthdate());
        member.setGender(blankToNull(request.gender()));
        member.setJoinedAt(request.joinedAt());
        member.setStatus(request.status());
        member.setBaptized(request.baptized());
        member.setBaptizedAt(request.baptizedAt());
        member.setPreferredLocale(blankToNull(request.preferredLocale()));
        member.setNotes(request.notes()); // pastor notes pass-through; controller restricts who can call PUT

        if (request.primaryCellId() != null) {
            cellMembershipService.setPrimary(member, request.primaryCellId());
        }

        Member full = memberRepository.findWithMembershipsById(id).orElse(member);
        return memberMapper.toDetail(full, canSeePastorNotes(caller));
    }

    @Transactional
    public MemberDetail selfUpdate(Long id, MemberSelfUpdateRequest request, AuthenticatedUser caller) {
        if (caller == null || caller.memberId() == null || !caller.memberId().equals(id)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Member member = memberRepository.findWithMembershipsById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));

        if (request.email() != null
                && memberRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateEmailException(request.email());
        }

        if (request.email() != null) member.setEmail(blankToNull(request.email()));
        if (request.phone() != null) member.setPhone(blankToNull(request.phone()));
        if (request.preferredLocale() != null) member.setPreferredLocale(request.preferredLocale());

        return memberMapper.toDetail(member, canSeePastorNotes(caller));
    }

    @Transactional
    public void delete(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));

        if (cellRepository.existsByLeader_Id(id)) {
            // Spec: cannot soft-delete a member who leads a cell.
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        memberRepository.delete(member); // intercepted by @SQLDelete → soft delete
        log.info("Member soft-deleted: id={}", id);
    }

    // ---------- helpers ----------

    /**
     * Read access rules per spec:
     * <pre>
     *   ADMIN, PASTOR : any
     *   LEADER        : own primary cell only (placeholder — Cells module wires this)
     *   MEMBER        : self only
     * </pre>
     */
    private void enforceReadAccess(Member target, AuthenticatedUser caller) {
        if (caller == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        switch (caller.role()) {
            case ADMIN, PASTOR -> { /* always allowed */ }
            case MEMBER -> {
                if (caller.memberId() == null || !caller.memberId().equals(target.getId())) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
            }
            case LEADER -> {
                // Self always OK; cross-member same-cell scoping lands in Week 5.
                if (caller.memberId() != null && caller.memberId().equals(target.getId())) {
                    return;
                }
                // Until cell scoping ships, block LEADER cross-member to be safe.
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private boolean canSeePastorNotes(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.common.BusinessException;
import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.CommitteeCreateRequest;
import com.msc.church.meeting.dto.CommitteeDetail;
import com.msc.church.meeting.dto.CommitteeMembershipRequest;
import com.msc.church.meeting.dto.CommitteeMembershipResponse;
import com.msc.church.meeting.dto.CommitteeSummary;
import com.msc.church.meeting.dto.CommitteeUpdateRequest;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final CommitteeMembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final MeetingMapper mapper;

    @Transactional(readOnly = true)
    public List<CommitteeSummary> list(boolean includeInactive) {
        Sort sort = Sort.by(Sort.Order.asc("code"));
        List<Committee> rows = includeInactive
                ? committeeRepository.findAll(sort)
                : committeeRepository.findByActiveTrue(sort);
        return rows.stream()
                .map(c -> mapper.toCommitteeSummary(
                        c,
                        membershipRepository.findByCommittee_IdAndActiveTrue(c.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommitteeDetail get(Long id) {
        Committee c = committeeRepository.findById(id)
                .orElseThrow(() -> new CommitteeNotFoundException(id));
        return mapper.toCommitteeDetail(c, rosterFor(id));
    }

    @Transactional
    public CommitteeDetail create(CommitteeCreateRequest req, AuthenticatedUser caller) {
        if (committeeRepository.findByCode(req.code()).isPresent()) {
            throw new DuplicateException(ErrorCode.DUPLICATE_COMMITTEE_CODE, req.code());
        }
        Committee c = Committee.builder()
                .code(req.code().trim())
                .nameKr(req.nameKr().trim())
                .nameEn(blankToNull(req.nameEn()))
                .description(blankToNull(req.description()))
                .active(true)
                .build();
        Committee saved = committeeRepository.save(c);
        log.info("Committee created: id={} code={} byUser={}",
                saved.getId(), saved.getCode(), callerId(caller));
        return mapper.toCommitteeDetail(saved, List.of());
    }

    @Transactional
    public CommitteeDetail update(Long id, CommitteeUpdateRequest req, AuthenticatedUser caller) {
        Committee c = committeeRepository.findById(id)
                .orElseThrow(() -> new CommitteeNotFoundException(id));
        c.setNameKr(req.nameKr().trim());
        c.setNameEn(blankToNull(req.nameEn()));
        c.setDescription(blankToNull(req.description()));
        c.setActive(req.active());
        log.info("Committee updated: id={} byUser={}", id, callerId(caller));
        return mapper.toCommitteeDetail(c, rosterFor(id));
    }

    @Transactional
    public CommitteeMembershipResponse addMembership(Long committeeId,
                                                     CommitteeMembershipRequest req,
                                                     AuthenticatedUser caller) {
        Committee c = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new CommitteeNotFoundException(committeeId));
        Member m = memberRepository.findById(req.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // If a membership row already exists (active or not), reuse it; otherwise insert.
        CommitteeMembership cm = membershipRepository
                .findByCommittee_IdAndMember_Id(committeeId, req.memberId())
                .orElse(null);
        if (cm == null) {
            cm = CommitteeMembership.builder()
                    .committee(c).member(m).role(req.role())
                    .joinedAt(req.joinedAt() == null ? LocalDate.now() : req.joinedAt())
                    .active(true)
                    .build();
            membershipRepository.save(cm);
        } else {
            cm.setRole(req.role());
            cm.setActive(true);
            cm.setLeftAt(null);
            if (cm.getJoinedAt() == null) cm.setJoinedAt(LocalDate.now());
        }
        log.info("Committee member added: committee={} member={} byUser={}",
                committeeId, req.memberId(), callerId(caller));
        return mapper.toCommitteeMembership(cm);
    }

    @Transactional
    public void removeMembership(Long committeeId, Long membershipId, AuthenticatedUser caller) {
        CommitteeMembership cm = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!cm.getCommittee().getId().equals(committeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        cm.setActive(false);
        cm.setLeftAt(LocalDate.now());
        log.info("Committee member removed: committee={} membership={} byUser={}",
                committeeId, membershipId, callerId(caller));
    }

    private List<CommitteeMembershipResponse> rosterFor(Long committeeId) {
        return membershipRepository.findByCommittee_IdAndActiveTrue(committeeId).stream()
                .sorted(Comparator
                        .comparingInt((CommitteeMembership cm) -> cm.getRole().ordinal())
                        .thenComparing(cm -> cm.getMember() == null ? "" : cm.getMember().getNameKr()))
                .map(mapper::toCommitteeMembership)
                .toList();
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

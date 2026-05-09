package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.MeetingCreateRequest;
import com.msc.church.meeting.dto.MeetingDetail;
import com.msc.church.meeting.dto.MeetingDuplicateRequest;
import com.msc.church.meeting.dto.MeetingSummary;
import com.msc.church.meeting.dto.MeetingUpdateRequest;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Meetings module service. Mirrors the Bulletin module pattern: delete-and-reinsert
 * attendees on update; deep-clone children on duplicate; publish flips a flag with
 * an idempotent guard. Visibility:
 * <ul>
 *   <li>ADMIN / PASTOR — full read/write everywhere</li>
 *   <li>Committee member — sees all meetings of their committee (any status), can mutate
 *       only their own action item statuses</li>
 *   <li>Anyone else — published meetings only</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final CommitteeRepository committeeRepository;
    private final CommitteeMembershipRepository committeeMembershipRepository;
    private final MemberRepository memberRepository;
    private final MeetingMapper mapper;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<MeetingSummary> list(Long committeeId, MeetingStatus status, Pageable pageable,
                                     AuthenticatedUser caller) {
        boolean staff = isStaff(caller);
        boolean canSeeDrafts = staff
                || (caller != null && caller.memberId() != null
                    && committeeMembershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(
                            committeeId, caller.memberId()));
        Page<Meeting> page;
        if (status != null) {
            page = meetingRepository.findByCommittee_IdAndStatusOrderByMeetingDateDesc(
                    committeeId, status, pageable);
        } else if (canSeeDrafts) {
            page = meetingRepository.findByCommittee_IdOrderByMeetingDateDesc(committeeId, pageable);
        } else {
            page = meetingRepository.findByCommittee_IdAndStatusOrderByMeetingDateDesc(
                    committeeId, MeetingStatus.PUBLISHED, pageable);
        }
        return page.map(m -> {
            int total = m.getActionItems().size();
            int open = (int) m.getActionItems().stream()
                    .filter(a -> a.getStatus() == ActionItemStatus.OPEN).count();
            return mapper.toMeetingSummary(m, total, open);
        });
    }

    @Transactional(readOnly = true)
    public MeetingDetail get(Long id, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findWithRefsById(id)
                .orElseThrow(() -> new MeetingNotFoundException(id));
        enforceVisibility(m, caller);
        return mapper.toMeetingDetail(m);
    }

    // ---------- writes ----------

    @Transactional
    public MeetingDetail create(MeetingCreateRequest req, AuthenticatedUser caller) {
        Committee committee = committeeRepository.findById(req.committeeId())
                .orElseThrow(() -> new CommitteeNotFoundException(req.committeeId()));
        if (meetingRepository.existsByCommittee_IdAndMeetingDate(committee.getId(), req.meetingDate())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_MEETING_DATE, req.meetingDate());
        }

        Meeting meeting = Meeting.builder()
                .committee(committee)
                .meetingDate(req.meetingDate())
                .presider(loadMember(req.presiderMemberId()))
                .title(blankToNull(req.title()))
                .agenda(blankToNull(req.agenda()))
                .minutes(blankToNull(req.minutes()))
                .status(MeetingStatus.DRAFT)
                .build();
        meeting.replaceAttendees(buildAttendees(meeting, req.attendeeMemberIds()));

        Meeting saved = meetingRepository.save(meeting);
        meetingRepository.flush();
        log.info("Meeting created: id={} committee={} date={} byUser={}",
                saved.getId(), committee.getId(), saved.getMeetingDate(), callerId(caller));
        return mapper.toMeetingDetail(saved);
    }

    @Transactional
    public MeetingDetail update(Long id, MeetingUpdateRequest req, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findWithRefsById(id)
                .orElseThrow(() -> new MeetingNotFoundException(id));

        if (!m.getMeetingDate().equals(req.meetingDate())
                && meetingRepository.existsByCommittee_IdAndMeetingDate(
                        m.getCommittee().getId(), req.meetingDate())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_MEETING_DATE, req.meetingDate());
        }

        m.setMeetingDate(req.meetingDate());
        m.setPresider(loadMember(req.presiderMemberId()));
        m.setTitle(blankToNull(req.title()));
        m.setAgenda(blankToNull(req.agenda()));
        m.setMinutes(blankToNull(req.minutes()));
        m.replaceAttendees(buildAttendees(m, req.attendeeMemberIds()));

        meetingRepository.flush();
        log.info("Meeting updated: id={} byUser={}", id, callerId(caller));
        return mapper.toMeetingDetail(m);
    }

    @Transactional
    public MeetingDetail duplicate(Long sourceId, MeetingDuplicateRequest req, AuthenticatedUser caller) {
        Meeting source = meetingRepository.findWithRefsById(sourceId)
                .orElseThrow(() -> new MeetingNotFoundException(sourceId));
        if (meetingRepository.existsByCommittee_IdAndMeetingDate(
                source.getCommittee().getId(), req.newMeetingDate())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_MEETING_DATE, req.newMeetingDate());
        }

        Meeting copy = Meeting.builder()
                .committee(source.getCommittee())
                .meetingDate(req.newMeetingDate())
                .presider(source.getPresider())
                .title(source.getTitle())
                .agenda(source.getAgenda())
                .minutes(null) // fresh meeting → no minutes carried over
                .status(MeetingStatus.DRAFT)
                .build();

        // Deep-clone attendees from the source (default attended=true; admin can edit).
        List<MeetingAttendee> attendees = new ArrayList<>();
        for (MeetingAttendee a : source.getAttendees()) {
            attendees.add(MeetingAttendee.builder()
                    .member(a.getMember())
                    .attended(true)
                    .build());
        }
        copy.replaceAttendees(attendees);

        if (req.carryOverOpenActionItems()) {
            // Open items → fresh ActionItem rows on the new meeting, status=OPEN.
            int idx = 1;
            List<ActionItem> carried = new ArrayList<>();
            for (ActionItem a : source.getActionItems()) {
                if (a.getStatus() != ActionItemStatus.OPEN) continue;
                ActionItem fresh = ActionItem.builder()
                        .meeting(copy)
                        .description(a.getDescription())
                        .assignee(a.getAssignee())
                        .dueDate(a.getDueDate())
                        .status(ActionItemStatus.OPEN)
                        .notes(a.getNotes())
                        .orderIdx(idx++)
                        .build();
                carried.add(fresh);
            }
            copy.getActionItems().addAll(carried);
        }

        Meeting saved = meetingRepository.save(copy);
        meetingRepository.flush();
        log.info("Meeting duplicated: source={} new={} carryOver={} byUser={}",
                sourceId, saved.getId(), req.carryOverOpenActionItems(), callerId(caller));
        return mapper.toMeetingDetail(saved);
    }

    @Transactional
    public MeetingDetail publish(Long id, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findWithRefsById(id)
                .orElseThrow(() -> new MeetingNotFoundException(id));
        if (m.getStatus() == MeetingStatus.PUBLISHED) {
            return mapper.toMeetingDetail(m); // idempotent
        }
        m.setStatus(MeetingStatus.PUBLISHED);
        m.setPublishedAt(LocalDateTime.now());
        log.info("Meeting published: id={} byUser={}", id, callerId(caller));
        return mapper.toMeetingDetail(m);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new MeetingNotFoundException(id));
        if (m.getStatus() == MeetingStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        meetingRepository.delete(m);
        log.info("Meeting deleted: id={} byUser={}", id, callerId(caller));
    }

    // ---------- helpers ----------

    private List<MeetingAttendee> buildAttendees(Meeting meeting, List<Long> memberIds) {
        if (memberIds == null) return List.of();
        List<MeetingAttendee> out = new ArrayList<>(memberIds.size());
        for (Long mid : memberIds) {
            Member m = memberRepository.findById(mid)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            out.add(MeetingAttendee.builder()
                    .meeting(meeting)
                    .member(m)
                    .attended(true)
                    .build());
        }
        return out;
    }

    private void enforceVisibility(Meeting m, AuthenticatedUser caller) {
        if (isStaff(caller)) return;
        if (m.getStatus() == MeetingStatus.PUBLISHED) return;
        // Drafts: visible to active committee members.
        if (caller != null && caller.memberId() != null
                && committeeMembershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(
                        m.getCommittee().getId(), caller.memberId())) {
            return;
        }
        throw new MeetingNotFoundException(m.getId()); // looks like 404 to outsiders
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
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

package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.MeetingTopicCreateRequest;
import com.msc.church.meeting.dto.MeetingTopicResponse;
import com.msc.church.meeting.dto.MeetingTopicStatusRequest;
import com.msc.church.meeting.dto.MeetingTopicUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for meeting topics. The user can edit AI-generated topics in place — title,
 * status, comment — and add new topics manually for things the AI missed.
 *
 * <p>Permissions mirror {@link MeetingService}: ADMIN/PASTOR + active committee
 * members of the meeting's committee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingTopicService {

    private final MeetingRepository meetingRepository;
    private final MeetingTopicRepository topicRepository;
    private final CommitteeMembershipRepository committeeMembershipRepository;
    private final MeetingMapper mapper;

    @Transactional
    public MeetingTopicResponse create(Long meetingId, MeetingTopicCreateRequest req,
                                       AuthenticatedUser caller) {
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        enforceWrite(m, caller);

        int nextIdx = m.getTopics().stream()
                .mapToInt(t -> t.getOrderIdx() == null ? 0 : t.getOrderIdx())
                .max().orElse(-1) + 1;

        MeetingTopic parent = null;
        if (req.parentTopicId() != null) {
            parent = topicRepository.findById(req.parentTopicId())
                    .orElseThrow(() -> new MeetingTopicNotFoundException(req.parentTopicId()));
        }

        MeetingTopic t = MeetingTopic.builder()
                .meeting(m)
                .parentTopic(parent)
                .title(req.title().trim())
                .summary(blankToNull(req.summary()))
                .decision(blankToNull(req.decision()))
                .status(req.status() != null ? req.status() : MeetingTopicStatus.DISCUSSED)
                .comment(blankToNull(req.comment()))
                .orderIdx(req.orderIdx() != null ? req.orderIdx() : nextIdx)
                .aiGenerated(false)
                .build();
        m.getTopics().add(t);
        meetingRepository.flush();
        log.info("Topic created: meetingId={} topicId={} byUser={}", meetingId, t.getId(), callerId(caller));
        return mapper.toTopicResponse(t);
    }

    @Transactional
    public MeetingTopicResponse update(Long topicId, MeetingTopicUpdateRequest req, AuthenticatedUser caller) {
        MeetingTopic t = topicRepository.findById(topicId)
                .orElseThrow(() -> new MeetingTopicNotFoundException(topicId));
        enforceWrite(t.getMeeting(), caller);

        if (req.title() != null && !req.title().isBlank()) t.setTitle(req.title().trim());
        if (req.summary() != null) t.setSummary(blankToNull(req.summary()));
        if (req.decision() != null) t.setDecision(blankToNull(req.decision()));
        if (req.status() != null) t.setStatus(req.status());
        if (req.comment() != null) t.setComment(blankToNull(req.comment()));
        if (req.orderIdx() != null) t.setOrderIdx(req.orderIdx());
        return mapper.toTopicResponse(t);
    }

    @Transactional
    public MeetingTopicResponse setStatus(Long topicId, MeetingTopicStatusRequest req, AuthenticatedUser caller) {
        MeetingTopic t = topicRepository.findById(topicId)
                .orElseThrow(() -> new MeetingTopicNotFoundException(topicId));
        enforceWrite(t.getMeeting(), caller);
        t.setStatus(req.status());
        if (req.comment() != null) t.setComment(blankToNull(req.comment()));
        log.info("Topic status: id={} status={} byUser={}", topicId, req.status(), callerId(caller));
        return mapper.toTopicResponse(t);
    }

    @Transactional
    public void delete(Long topicId, AuthenticatedUser caller) {
        MeetingTopic t = topicRepository.findById(topicId)
                .orElseThrow(() -> new MeetingTopicNotFoundException(topicId));
        enforceWrite(t.getMeeting(), caller);
        // orphanRemoval handles the cascade; remove from collection so the in-memory
        // Meeting view stays consistent with the persisted state.
        t.getMeeting().getTopics().remove(t);
        log.info("Topic deleted: id={} byUser={}", topicId, callerId(caller));
    }

    // ------------------------------------------------------------------

    private void enforceWrite(Meeting m, AuthenticatedUser caller) {
        if (caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR)) return;
        if (caller != null && caller.memberId() != null
                && committeeMembershipRepository.existsByCommittee_IdAndMember_IdAndActiveTrue(
                        m.getCommittee().getId(), caller.memberId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
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

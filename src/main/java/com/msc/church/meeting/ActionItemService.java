package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.ActionItemRequest;
import com.msc.church.meeting.dto.ActionItemResponse;
import com.msc.church.meeting.dto.ActionItemStatusRequest;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Action items live under a meeting but have their own write surface so an assignee
 * can flip status without holding the meeting lock. ADMIN/PASTOR full mutation;
 * the assignee may patch the status of their own item.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionItemService {

    private final ActionItemRepository actionItemRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final MeetingMapper mapper;

    @Transactional
    public ActionItemResponse create(Long meetingId, ActionItemRequest req, AuthenticatedUser caller) {
        if (!isStaff(caller)) throw new BusinessException(ErrorCode.FORBIDDEN);
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        ActionItem a = ActionItem.builder()
                .meeting(m)
                .description(req.description().trim())
                .assignee(loadMember(req.assigneeMemberId()))
                .dueDate(req.dueDate())
                .status(req.status() == null ? ActionItemStatus.OPEN : req.status())
                .notes(blankToNull(req.notes()))
                .orderIdx(req.orderIdx() == null ? nextOrderIdx(m) : req.orderIdx())
                .build();
        ActionItem saved = actionItemRepository.save(a);
        log.info("Action item created: id={} meeting={} byUser={}",
                saved.getId(), meetingId, callerId(caller));
        return mapper.toActionItemResponse(saved, m);
    }

    @Transactional
    public ActionItemResponse update(Long id, ActionItemRequest req, AuthenticatedUser caller) {
        if (!isStaff(caller)) throw new BusinessException(ErrorCode.FORBIDDEN);
        ActionItem a = actionItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_ITEM_NOT_FOUND));
        a.setDescription(req.description().trim());
        a.setAssignee(loadMember(req.assigneeMemberId()));
        a.setDueDate(req.dueDate());
        if (req.status() != null) a.setStatus(req.status());
        a.setNotes(blankToNull(req.notes()));
        if (req.orderIdx() != null) a.setOrderIdx(req.orderIdx());
        log.info("Action item updated: id={} byUser={}", id, callerId(caller));
        return mapper.toActionItemResponse(a, a.getMeeting());
    }

    @Transactional
    public ActionItemResponse setStatus(Long id, ActionItemStatusRequest req, AuthenticatedUser caller) {
        ActionItem a = actionItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_ITEM_NOT_FOUND));

        boolean canMutate = isStaff(caller)
                || (caller != null && caller.memberId() != null
                    && a.getAssignee() != null
                    && a.getAssignee().getId().equals(caller.memberId()));
        if (!canMutate) throw new BusinessException(ErrorCode.FORBIDDEN);

        a.setStatus(req.status());
        if (req.notes() != null) a.setNotes(blankToNull(req.notes()));
        if (req.status() == ActionItemStatus.DONE || req.status() == ActionItemStatus.CANCELLED) {
            a.setCompletedAt(LocalDateTime.now());
            a.setCompletedByUserId(callerId(caller));
        } else {
            a.setCompletedAt(null);
            a.setCompletedByUserId(null);
        }
        log.info("Action item status: id={} status={} byUser={}",
                id, req.status(), callerId(caller));
        return mapper.toActionItemResponse(a, a.getMeeting());
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        if (!isStaff(caller)) throw new BusinessException(ErrorCode.FORBIDDEN);
        ActionItem a = actionItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_ITEM_NOT_FOUND));
        actionItemRepository.delete(a);
        log.info("Action item deleted: id={} byUser={}", id, callerId(caller));
    }

    @Transactional(readOnly = true)
    public List<ActionItemResponse> myOpen(AuthenticatedUser caller) {
        if (caller == null || caller.memberId() == null) return List.of();
        return actionItemRepository.findOpenForAssignee(caller.memberId()).stream()
                .map(a -> mapper.toActionItemResponse(a, a.getMeeting()))
                .toList();
    }

    // ---------- helpers ----------

    private int nextOrderIdx(Meeting m) {
        return m.getActionItems().stream()
                .mapToInt(a -> a.getOrderIdx() == null ? 0 : a.getOrderIdx())
                .max().orElse(0) + 1;
    }

    private Member loadMember(Long id) {
        if (id == null) return null;
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
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

package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.ActionItemRequest;
import com.msc.church.meeting.dto.ActionItemStatusRequest;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import com.msc.church.member.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionItemServiceTest {

    @Mock ActionItemRepository actionItemRepository;
    @Mock MeetingRepository meetingRepository;
    @Mock MemberRepository memberRepository;

    MeetingMapper mapper = new MeetingMapper();
    @InjectMocks ActionItemService actionItemService;

    private AuthenticatedUser admin;
    private AuthenticatedUser kim;       // assignee
    private AuthenticatedUser someoneElse;
    private Member kimMember;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a", null, Role.ADMIN, true);
        kim = new AuthenticatedUser(2L, "k", 42L, Role.MEMBER, true);
        someoneElse = new AuthenticatedUser(3L, "x", 99L, Role.MEMBER, true);

        kimMember = Member.builder().nameKr("김민수").baptized(false).status(MemberStatus.ACTIVE).build();
        ReflectionTestUtils.setField(kimMember, "id", 42L);

        meeting = Meeting.builder()
                .meetingDate(LocalDate.of(2026, 5, 12))
                .actionItems(new ArrayList<>())
                .attendees(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(meeting, "id", 100L);
        ReflectionTestUtils.setField(actionItemService, "mapper", mapper);
    }

    @Test
    @DisplayName("create: non-staff is forbidden")
    void create_nonStaffForbidden() {
        var req = new ActionItemRequest("write up budget", null, null, null, null, null);
        assertThatThrownBy(() -> actionItemService.create(100L, req, kim))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("create: staff path persists with default OPEN status")
    void create_staff() {
        when(meetingRepository.findById(100L)).thenReturn(Optional.of(meeting));
        when(memberRepository.findById(42L)).thenReturn(Optional.of(kimMember));
        when(actionItemRepository.save(any(ActionItem.class))).thenAnswer(inv -> {
            ActionItem a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 1L);
            return a;
        });

        var req = new ActionItemRequest("write up budget", 42L, LocalDate.of(2026, 5, 19), null, null, null);
        var resp = actionItemService.create(100L, req, admin);

        assertThat(resp.status()).isEqualTo(ActionItemStatus.OPEN);
        assertThat(resp.assignee().id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("setStatus: assignee can mark their item DONE")
    void setStatus_assigneeAllowed() {
        ActionItem a = ActionItem.builder().meeting(meeting).description("x").assignee(kimMember)
                .status(ActionItemStatus.OPEN).orderIdx(1).build();
        ReflectionTestUtils.setField(a, "id", 5L);
        when(actionItemRepository.findById(5L)).thenReturn(Optional.of(a));

        actionItemService.setStatus(5L, new ActionItemStatusRequest(ActionItemStatus.DONE, "did it"), kim);

        assertThat(a.getStatus()).isEqualTo(ActionItemStatus.DONE);
        assertThat(a.getCompletedAt()).isNotNull();
        assertThat(a.getCompletedByUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("setStatus: another member can't change someone else's item")
    void setStatus_strangerForbidden() {
        ActionItem a = ActionItem.builder().meeting(meeting).description("x").assignee(kimMember)
                .status(ActionItemStatus.OPEN).orderIdx(1).build();
        ReflectionTestUtils.setField(a, "id", 5L);
        when(actionItemRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> actionItemService.setStatus(5L,
                new ActionItemStatusRequest(ActionItemStatus.DONE, null), someoneElse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("setStatus: staff (PASTOR) can change anyone's item")
    void setStatus_staffAllowed() {
        ActionItem a = ActionItem.builder().meeting(meeting).description("x").assignee(kimMember)
                .status(ActionItemStatus.OPEN).orderIdx(1).build();
        ReflectionTestUtils.setField(a, "id", 5L);
        when(actionItemRepository.findById(5L)).thenReturn(Optional.of(a));

        var pastor = new AuthenticatedUser(1L, "p", 50L, Role.PASTOR, true);
        actionItemService.setStatus(5L, new ActionItemStatusRequest(ActionItemStatus.CANCELLED, null), pastor);
        assertThat(a.getStatus()).isEqualTo(ActionItemStatus.CANCELLED);
    }
}

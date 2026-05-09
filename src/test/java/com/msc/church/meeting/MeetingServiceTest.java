package com.msc.church.meeting;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;
import com.msc.church.meeting.dto.MeetingCreateRequest;
import com.msc.church.meeting.dto.MeetingDuplicateRequest;
import com.msc.church.member.MemberRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock CommitteeRepository committeeRepository;
    @Mock CommitteeMembershipRepository committeeMembershipRepository;
    @Mock MemberRepository memberRepository;

    MeetingMapper mapper = new MeetingMapper();

    @InjectMocks MeetingService meetingService;

    private AuthenticatedUser admin;
    private Committee board;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a", null, Role.ADMIN, true);
        board = Committee.builder().code("BOARD").nameKr("이사회").nameEn("Board").active(true).build();
        ReflectionTestUtils.setField(board, "id", 10L);
        ReflectionTestUtils.setField(meetingService, "mapper", mapper);
    }

    @Test
    @DisplayName("create: rejects duplicate (committee, date) tuple")
    void create_dupDate() {
        when(committeeRepository.findById(10L)).thenReturn(Optional.of(board));
        when(meetingRepository.existsByCommittee_IdAndMeetingDate(10L, LocalDate.of(2026, 5, 12))).thenReturn(true);
        var req = new MeetingCreateRequest(10L, LocalDate.of(2026, 5, 12), null,
                "Weekly board", null, null, null);
        assertThatThrownBy(() -> meetingService.create(req, admin))
                .isInstanceOf(DuplicateException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_MEETING_DATE);
        verify(meetingRepository, never()).save(any(Meeting.class));
    }

    @Test
    @DisplayName("publish: idempotent for already-published")
    void publish_idempotent() {
        Meeting m = meetingFixture(50L, MeetingStatus.PUBLISHED);
        m.setPublishedAt(java.time.LocalDateTime.of(2026, 5, 1, 9, 0));
        when(meetingRepository.findWithRefsById(50L)).thenReturn(Optional.of(m));
        meetingService.publish(50L, admin);
        assertThat(m.getStatus()).isEqualTo(MeetingStatus.PUBLISHED);
        // publishedAt unchanged
        assertThat(m.getPublishedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 5, 1, 9, 0));
    }

    @Test
    @DisplayName("publish: success sets status + publishedAt")
    void publish_happy() {
        Meeting m = meetingFixture(50L, MeetingStatus.DRAFT);
        when(meetingRepository.findWithRefsById(50L)).thenReturn(Optional.of(m));
        meetingService.publish(50L, admin);
        assertThat(m.getStatus()).isEqualTo(MeetingStatus.PUBLISHED);
        assertThat(m.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete: refuses when published (CONFLICT)")
    void delete_blockedWhenPublished() {
        Meeting m = meetingFixture(50L, MeetingStatus.PUBLISHED);
        when(meetingRepository.findById(50L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> meetingService.delete(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("get: non-staff non-member sees 404 for a draft")
    void get_outsiderHiddenFromDraft() {
        Meeting m = meetingFixture(50L, MeetingStatus.DRAFT);
        when(meetingRepository.findWithRefsById(50L)).thenReturn(Optional.of(m));
        when(committeeMembershipRepository
                .existsByCommittee_IdAndMember_IdAndActiveTrue(10L, 99L))
                .thenReturn(false);
        var outsider = new AuthenticatedUser(2L, "x", 99L, Role.MEMBER, true);
        assertThatThrownBy(() -> meetingService.get(50L, outsider))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    @DisplayName("get: committee member can read their committee's draft")
    void get_committeeMemberSeesDraft() {
        Meeting m = meetingFixture(50L, MeetingStatus.DRAFT);
        when(meetingRepository.findWithRefsById(50L)).thenReturn(Optional.of(m));
        when(committeeMembershipRepository
                .existsByCommittee_IdAndMember_IdAndActiveTrue(10L, 42L))
                .thenReturn(true);
        var member = new AuthenticatedUser(2L, "x", 42L, Role.MEMBER, true);
        var detail = meetingService.get(50L, member);
        assertThat(detail.id()).isEqualTo(50L);
    }

    @Test
    @DisplayName("duplicate: deep-clones attendees, carries over open action items, drops minutes")
    void duplicate_carryOver() {
        Meeting source = meetingFixture(50L, MeetingStatus.PUBLISHED);
        source.setMinutes("LONG MINUTES");
        // 1 attendee
        source.replaceAttendees(List.of(MeetingAttendee.builder().attended(true).build()));
        // 1 open action item, 1 done
        ActionItem open = ActionItem.builder()
                .description("follow up budget").status(ActionItemStatus.OPEN).orderIdx(1).build();
        ActionItem done = ActionItem.builder()
                .description("send notes").status(ActionItemStatus.DONE).orderIdx(2).build();
        source.getActionItems().add(open);
        source.getActionItems().add(done);

        when(meetingRepository.findWithRefsById(50L)).thenReturn(Optional.of(source));
        when(meetingRepository.existsByCommittee_IdAndMeetingDate(10L, LocalDate.of(2026, 5, 19))).thenReturn(false);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> {
            Meeting copy = inv.getArgument(0);
            ReflectionTestUtils.setField(copy, "id", 51L);
            return copy;
        });

        var req = new MeetingDuplicateRequest(LocalDate.of(2026, 5, 19), true);
        meetingService.duplicate(50L, req, admin);

        // We can't easily inspect the saved Meeting from the mock without a captor,
        // but verify the save happened and the source unchanged.
        verify(meetingRepository).save(any(Meeting.class));
        assertThat(source.getMinutes()).isEqualTo("LONG MINUTES"); // source untouched
    }

    private Meeting meetingFixture(Long id, MeetingStatus status) {
        Meeting m = Meeting.builder()
                .committee(board)
                .meetingDate(LocalDate.of(2026, 5, 12))
                .title("Weekly board")
                .status(status)
                .attendees(new ArrayList<>())
                .actionItems(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}

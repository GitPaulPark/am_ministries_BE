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
import com.msc.church.member.dto.MemberUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Canonical service test. Future modules' service tests should use this shape:
 * <ul>
 *   <li>Mockito {@code @InjectMocks} on the service, all collaborators mocked</li>
 *   <li>One {@code @Test} per behaviour with a {@code @DisplayName} that reads as a
 *       sentence about the contract</li>
 *   <li>Build entities through their {@code @Builder}; set generated ids via
 *       {@link ReflectionTestUtils} so the rest of the test code is plain</li>
 *   <li>Cover happy path + every custom exception thrown by the service</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock CellRepository cellRepository;
    @Mock CellMembershipService cellMembershipService;
    @Mock MemberMapper memberMapper;

    @InjectMocks MemberService memberService;

    private AuthenticatedUser admin;
    private AuthenticatedUser pastor;
    private AuthenticatedUser member42;

    @BeforeEach
    void setUp() {
        admin    = new AuthenticatedUser(1L, "admin@msc.local", null, Role.ADMIN, true);
        pastor   = new AuthenticatedUser(2L, "pastor@msc.local", 99L, Role.PASTOR, true);
        member42 = new AuthenticatedUser(3L, "kim@msc.local", 42L, Role.MEMBER, true);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: persists the member, sets primary cell, returns detail")
    void create_happyPath() {
        var req = new MemberCreateRequest(
                "한바울", "Paul Han", "paul@example.com", "010-1234-5678",
                "Pastor", LocalDate.of(1985, 3, 1), "M", LocalDate.of(2018, 9, 1), 5L);

        when(memberRepository.existsByEmail("paul@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 100L);
            return m;
        });
        when(memberRepository.findWithMembershipsById(100L)).thenReturn(Optional.empty());
        var detail = stubDetail(100L, "한바울", true);
        when(memberMapper.toDetail(any(Member.class), eq(true))).thenReturn(detail);

        MemberDetail result = memberService.create(req, pastor);

        assertThat(result).isEqualTo(detail);
        verify(cellMembershipService, times(1)).setPrimary(any(Member.class), eq(5L));
    }

    @Test
    @DisplayName("create: rejects duplicate email with DuplicateEmailException")
    void create_duplicateEmail() {
        var req = new MemberCreateRequest(
                "박민수", null, "duplicate@example.com", null,
                null, null, null, null, null);

        when(memberRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.create(req, admin))
                .isInstanceOf(DuplicateEmailException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        verify(memberRepository, never()).save(any());
        verify(cellMembershipService, never()).setPrimary(any(), any());
    }

    @Test
    @DisplayName("create: skips primary cell wiring when primaryCellId is null")
    void create_noPrimaryCell() {
        var req = new MemberCreateRequest(
                "이지은", null, null, null, null, null, null, null, null);

        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 101L);
            return m;
        });
        when(memberRepository.findWithMembershipsById(101L)).thenReturn(Optional.empty());
        when(memberMapper.toDetail(any(Member.class), anyBoolean())).thenReturn(stubDetail(101L, "이지은", true));

        memberService.create(req, admin);

        verify(cellMembershipService, never()).setPrimary(any(), any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: applies changes and re-resolves memberships in detail")
    void update_happyPath() {
        Member existing = activeMember(50L, "old@example.com");
        when(memberRepository.findWithMembershipsById(50L)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("new@example.com", 50L)).thenReturn(false);
        when(memberMapper.toDetail(any(), eq(true))).thenReturn(stubDetail(50L, "변경됨", true));

        var req = new MemberUpdateRequest(
                "변경됨", "Changed", "new@example.com", "010-9999-9999",
                "Dcn.", LocalDate.of(1990, 1, 1), "F", LocalDate.of(2020, 1, 1),
                MemberStatus.ACTIVE, false, null, 7L, "ko", "pastor note");

        memberService.update(50L, req, admin);

        assertThat(existing.getNameKr()).isEqualTo("변경됨");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getPhone()).isEqualTo("010-9999-9999");
        assertThat(existing.getNotes()).isEqualTo("pastor note");
        verify(cellMembershipService).setPrimary(existing, 7L);
    }

    @Test
    @DisplayName("update: rejects baptized=true without baptizedAt")
    void update_baptizedRequiresDate() {
        Member existing = activeMember(60L, null);
        when(memberRepository.findWithMembershipsById(60L)).thenReturn(Optional.of(existing));

        var req = new MemberUpdateRequest(
                "한바울", null, null, null, null, null, null, null,
                MemberStatus.ACTIVE, true, null, null, null, null);

        assertThatThrownBy(() -> memberService.update(60L, req, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("update: rejects taking another member's email")
    void update_duplicateEmail() {
        Member existing = activeMember(70L, "me@example.com");
        when(memberRepository.findWithMembershipsById(70L)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("taken@example.com", 70L)).thenReturn(true);

        var req = new MemberUpdateRequest(
                "한바울", null, "taken@example.com", null, null, null, null, null,
                MemberStatus.ACTIVE, false, null, null, null, null);

        assertThatThrownBy(() -> memberService.update(70L, req, admin))
                .isInstanceOf(DuplicateEmailException.class);
    }

    // ---------- selfUpdate ----------

    @Test
    @DisplayName("selfUpdate: member can edit their own phone/email/locale")
    void selfUpdate_happyPath() {
        Member existing = activeMember(42L, "old@example.com");
        when(memberRepository.findWithMembershipsById(42L)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("kim-new@example.com", 42L)).thenReturn(false);
        when(memberMapper.toDetail(any(), eq(false))).thenReturn(stubDetail(42L, "Kim", false));

        var req = new MemberSelfUpdateRequest("kim-new@example.com", "010-1111-2222", "en");
        memberService.selfUpdate(42L, req, member42);

        assertThat(existing.getEmail()).isEqualTo("kim-new@example.com");
        assertThat(existing.getPhone()).isEqualTo("010-1111-2222");
        assertThat(existing.getPreferredLocale()).isEqualTo("en");
    }

    @Test
    @DisplayName("selfUpdate: forbidden when targeting a different member id")
    void selfUpdate_crossSelfForbidden() {
        var req = new MemberSelfUpdateRequest(null, "010-3333-4444", null);

        assertThatThrownBy(() -> memberService.selfUpdate(99L, req, member42))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

        verify(memberRepository, never()).findWithMembershipsById(any());
    }

    // ---------- get ----------

    @Test
    @DisplayName("get: throws MemberNotFoundException for missing id")
    void get_notFound() {
        when(memberRepository.findWithMembershipsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.get(999L, admin))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("get: MEMBER may read self")
    void get_memberSelfAllowed() {
        Member m = activeMember(42L, null);
        when(memberRepository.findWithMembershipsById(42L)).thenReturn(Optional.of(m));
        when(memberMapper.toDetail(eq(m), eq(false))).thenReturn(stubDetail(42L, "Kim", false));

        memberService.get(42L, member42);

        verify(memberMapper).toDetail(m, false);
    }

    @Test
    @DisplayName("get: MEMBER cannot read another member")
    void get_memberCrossSelfForbidden() {
        Member m = activeMember(99L, null);
        when(memberRepository.findWithMembershipsById(99L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> memberService.get(99L, member42))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("get: PASTOR sees pastor notes (includeNotes=true)")
    void get_pastorSeesNotes() {
        Member m = activeMember(50L, null);
        m.setNotes("private");
        when(memberRepository.findWithMembershipsById(50L)).thenReturn(Optional.of(m));
        when(memberMapper.toDetail(eq(m), eq(true))).thenReturn(stubDetail(50L, "x", true));

        memberService.get(50L, pastor);

        verify(memberMapper).toDetail(m, true);
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: soft-deletes via repository.delete")
    void delete_happyPath() {
        Member m = activeMember(80L, null);
        when(memberRepository.findById(80L)).thenReturn(Optional.of(m));
        when(cellRepository.existsByLeader_Id(80L)).thenReturn(false);

        memberService.delete(80L);

        verify(memberRepository).delete(m);
    }

    @Test
    @DisplayName("delete: refuses when the member leads a cell (CONFLICT)")
    void delete_blocksLeader() {
        Member m = activeMember(81L, null);
        when(memberRepository.findById(81L)).thenReturn(Optional.of(m));
        when(cellRepository.existsByLeader_Id(81L)).thenReturn(true);

        assertThatThrownBy(() -> memberService.delete(81L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    @DisplayName("delete: throws MemberNotFoundException when missing")
    void delete_notFound() {
        when(memberRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.delete(404L))
                .isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- helpers ----------

    private static Member activeMember(Long id, String email) {
        Member m = Member.builder()
                .nameKr("기본")
                .email(email)
                .baptized(false)
                .status(MemberStatus.ACTIVE)
                .memberships(List.of())
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static MemberDetail stubDetail(Long id, String nameKr, boolean withNotes) {
        return new MemberDetail(
                id, nameKr, null, null, null, null, null, null,
                false, null, null, MemberStatus.ACTIVE, null, List.of(),
                withNotes ? "n" : null);
    }
}

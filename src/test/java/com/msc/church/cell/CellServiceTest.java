package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.dto.CellCreateRequest;
import com.msc.church.cell.dto.CellMembershipCreateRequest;
import com.msc.church.cell.dto.CellUpdateRequest;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CellServiceTest {

    @Mock CellRepository cellRepository;
    @Mock CellMembershipRepository membershipRepository;
    @Mock MemberRepository memberRepository;
    @Mock CellMapper cellMapper;

    @InjectMocks CellService cellService;

    private AuthenticatedUser admin;
    private AuthenticatedUser leaderOfCellC;
    private Member someMember;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "admin@msc.local", null, Role.ADMIN, true);
        leaderOfCellC = new AuthenticatedUser(2L, "lead@msc.local", 50L, Role.LEADER, true);
        someMember = Member.builder().nameKr("리더").baptized(false).status(MemberStatus.ACTIVE).build();
        ReflectionTestUtils.setField(someMember, "id", 50L);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: rejects duplicate code")
    void create_dupCode() {
        when(cellRepository.findByCode("C")).thenReturn(Optional.of(cellOf(1L, "C", null)));
        var req = new CellCreateRequest("C", "셀 C", null, CellType.REGULAR,
                null, null, null, null, null);
        assertThatThrownBy(() -> cellService.create(req, admin))
                .isInstanceOf(DuplicateCellCodeException.class);
        verify(cellRepository, never()).save(any(Cell.class));
    }

    @Test
    @DisplayName("create: persists with leader resolved")
    void create_happyPath() {
        when(cellRepository.findByCode("D")).thenReturn(Optional.empty());
        when(memberRepository.findById(50L)).thenReturn(Optional.of(someMember));
        when(cellRepository.save(any(Cell.class))).thenAnswer(inv -> {
            Cell c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 200L);
            return c;
        });
        when(membershipRepository.findByCell_IdAndActiveTrue(200L)).thenReturn(List.of());

        var req = new CellCreateRequest("D", "셀 D", "Cell D", CellType.REGULAR,
                50L, "Sunday", "15:30", "Grace 202", "desc");
        cellService.create(req, admin);

        verify(cellRepository).save(any(Cell.class));
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: LEADER editing a cell they don't lead → 403")
    void update_leaderForbidden() {
        Cell cell = cellOf(10L, "G", someMember /* leader id 50 */);
        // The leader is member id 50; the caller has memberId 50, but suppose the cell's leader is someone else:
        ReflectionTestUtils.setField(someMember, "id", 99L); // ≠ caller's 50
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));

        var req = new CellUpdateRequest("name", null, CellType.REGULAR, null,
                null, null, null, null, true);
        assertThatThrownBy(() -> cellService.update(10L, req, leaderOfCellC))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("update: LEADER editing own cell — only meeting fields persisted")
    void update_leaderLimited() {
        // cell led by member 50 (matches leaderOfCellC.memberId)
        Cell cell = cellOf(10L, "G", someMember);
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(membershipRepository.findByCell_IdAndActiveTrue(10L)).thenReturn(List.of());

        var req = new CellUpdateRequest("새 이름!", "DON'T", CellType.MISSION /* shouldn't apply */,
                null, "Sunday", "15:30", "New Hall", "new desc", false);
        cellService.update(10L, req, leaderOfCellC);

        // Only meeting-related fields changed:
        assertThat(cell.getMeetingDay()).isEqualTo("Sunday");
        assertThat(cell.getMeetingTime()).isEqualTo("15:30");
        assertThat(cell.getMeetingLocation()).isEqualTo("New Hall");
        assertThat(cell.getDescription()).isEqualTo("new desc");
        // Untouched:
        assertThat(cell.getNameKr()).isEqualTo("셀 G");
        assertThat(cell.getType()).isEqualTo(CellType.REGULAR);
        assertThat(cell.isActive()).isTrue();
    }

    @Test
    @DisplayName("update: ADMIN can change everything")
    void update_adminFull() {
        Cell cell = cellOf(10L, "G", someMember);
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(membershipRepository.findByCell_IdAndActiveTrue(10L)).thenReturn(List.of());

        var req = new CellUpdateRequest("새 이름", "New", CellType.MISSION,
                null, "Mon", "20:00", "Online", "desc2", false);
        cellService.update(10L, req, admin);

        assertThat(cell.getNameKr()).isEqualTo("새 이름");
        assertThat(cell.getType()).isEqualTo(CellType.MISSION);
        assertThat(cell.isActive()).isFalse();
    }

    // ---------- deactivate ----------

    @Test
    @DisplayName("deactivate: refuses if any active primary memberships still reference it")
    void deactivate_blockedWhenPrimariesExist() {
        Cell cell = cellOf(10L, "G", someMember);
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(membershipRepository.existsByCell_IdAndPrimaryTrueAndActiveTrue(10L)).thenReturn(true);

        assertThatThrownBy(() -> cellService.deactivate(10L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        assertThat(cell.isActive()).isTrue();
    }

    @Test
    @DisplayName("deactivate: flips active=false when no primary memberships block")
    void deactivate_happyPath() {
        Cell cell = cellOf(10L, "G", someMember);
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(membershipRepository.existsByCell_IdAndPrimaryTrueAndActiveTrue(10L)).thenReturn(false);

        cellService.deactivate(10L, admin);

        assertThat(cell.isActive()).isFalse();
    }

    // ---------- getMyPrimaryCell ----------

    @Test
    @DisplayName("getMyPrimaryCell: 404 when caller has no member link")
    void myCell_noMember() {
        var caller = new AuthenticatedUser(99L, "noprofile@msc.local", null, Role.MEMBER, true);
        assertThatThrownBy(() -> cellService.getMyPrimaryCell(caller))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("getMyPrimaryCell: returns the user's primary cell with roster")
    void myCell_happy() {
        Cell cell = cellOf(11L, "C", null);
        CellMembership cm = CellMembership.builder()
                .cell(cell).primary(true).active(true).build();
        ReflectionTestUtils.setField(cm, "id", 1L);
        when(membershipRepository.findByMember_IdAndPrimaryTrue(50L)).thenReturn(Optional.of(cm));
        when(membershipRepository.findByCell_IdAndActiveTrue(11L)).thenReturn(List.of());

        cellService.getMyPrimaryCell(leaderOfCellC);

        verify(cellMapper).toDetail(cell, List.of());
    }

    // ---------- helpers ----------

    private Cell cellOf(Long id, String code, Member leader) {
        Cell c = Cell.builder()
                .code(code)
                .nameKr("셀 " + code)
                .nameEn("Cell " + code)
                .type(CellType.REGULAR)
                .leader(leader)
                .active(true)
                .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}

package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.dto.TransferApproveRequest;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CellTransferSuggestionServiceTest {

    @Mock CellTransferSuggestionRepository repository;
    @Mock CellRepository cellRepository;
    @Mock CellMembershipRepository membershipRepository;

    @InjectMocks CellTransferSuggestionService service;

    private AuthenticatedUser admin;
    private Cell nec;
    private Cell cellC;
    private Member newcomer;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a", null, Role.ADMIN, true);
        nec = cellOf(5L, "Nec", CellType.NEWCOMER);
        cellC = cellOf(1L, "C", CellType.REGULAR);
        newcomer = Member.builder().nameKr("새신자").baptized(false).status(MemberStatus.ACTIVE).joinedAt(LocalDate.now().minusWeeks(10)).build();
        ReflectionTestUtils.setField(newcomer, "id", 100L);
    }

    @Test
    @DisplayName("approve: transitions newcomer membership and sets suggestion APPROVED")
    void approve_atomicTransition() {
        CellTransferSuggestion s = CellTransferSuggestion.builder()
                .member(newcomer).fromCell(nec).status(TransferSuggestionStatus.PENDING).build();
        ReflectionTestUtils.setField(s, "id", 9L);

        CellMembership necMembership = CellMembership.builder()
                .member(newcomer).cell(nec).primary(true).active(true).joinedAt(LocalDate.now().minusWeeks(10)).build();
        ReflectionTestUtils.setField(necMembership, "id", 50L);

        when(repository.findById(9L)).thenReturn(Optional.of(s));
        when(cellRepository.findById(1L)).thenReturn(Optional.of(cellC));
        when(membershipRepository.findByMember_IdAndActiveTrue(100L)).thenReturn(List.of(necMembership));

        service.approve(9L, new TransferApproveRequest(1L, "approved"), admin);

        // Source membership deactivated:
        assertThat(necMembership.isActive()).isFalse();
        assertThat(necMembership.getLeftAt()).isEqualTo(LocalDate.now());
        assertThat(necMembership.isPrimary()).isFalse();
        // Target membership saved:
        verify(membershipRepository).save(any(CellMembership.class));
        // Suggestion finalised:
        assertThat(s.getStatus()).isEqualTo(TransferSuggestionStatus.APPROVED);
        assertThat(s.getToCell()).isEqualTo(cellC);
        assertThat(s.getApprovedByUserId()).isEqualTo(admin.id());
    }

    @Test
    @DisplayName("approve: refuses non-pending suggestions (CONFLICT)")
    void approve_alreadyApproved() {
        CellTransferSuggestion s = CellTransferSuggestion.builder()
                .member(newcomer).fromCell(nec).status(TransferSuggestionStatus.APPROVED).build();
        ReflectionTestUtils.setField(s, "id", 9L);
        when(repository.findById(9L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.approve(9L, new TransferApproveRequest(1L, null), admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("scanForGraduations: creates a suggestion for a newcomer past the threshold with no pending suggestion")
    void scan_creates() {
        when(cellRepository.findByActiveTrue(any(Sort.class))).thenReturn(List.of(nec));
        CellMembership necMembership = CellMembership.builder()
                .member(newcomer).cell(nec).primary(true).active(true)
                .joinedAt(LocalDate.now().minusWeeks(10)).build();
        when(membershipRepository.findActivePrimaryByCellId(5L)).thenReturn(List.of(necMembership));
        when(repository.countPendingForMember(100L)).thenReturn(0L);

        int created = service.scanForGraduations(8);

        assertThat(created).isEqualTo(1);
        verify(repository, times(1)).save(any(CellTransferSuggestion.class));
    }

    @Test
    @DisplayName("scanForGraduations: skips members with an existing pending suggestion")
    void scan_skipsExistingPending() {
        when(cellRepository.findByActiveTrue(any(Sort.class))).thenReturn(List.of(nec));
        CellMembership necMembership = CellMembership.builder()
                .member(newcomer).cell(nec).primary(true).active(true)
                .joinedAt(LocalDate.now().minusWeeks(10)).build();
        when(membershipRepository.findActivePrimaryByCellId(5L)).thenReturn(List.of(necMembership));
        when(repository.countPendingForMember(100L)).thenReturn(1L);

        int created = service.scanForGraduations(8);

        assertThat(created).isZero();
        verify(repository, never()).save(any(CellTransferSuggestion.class));
    }

    @Test
    @DisplayName("scanForGraduations: leaves under-threshold newcomers alone")
    void scan_underThreshold() {
        when(cellRepository.findByActiveTrue(any(Sort.class))).thenReturn(List.of(nec));
        CellMembership necMembership = CellMembership.builder()
                .member(newcomer).cell(nec).primary(true).active(true)
                .joinedAt(LocalDate.now().minusWeeks(2)).build();
        when(membershipRepository.findActivePrimaryByCellId(5L)).thenReturn(List.of(necMembership));

        int created = service.scanForGraduations(8);

        assertThat(created).isZero();
        verify(repository, never()).save(any(CellTransferSuggestion.class));
    }

    private Cell cellOf(Long id, String code, CellType type) {
        Cell c = Cell.builder().code(code).nameKr("셀 " + code).nameEn("Cell " + code)
                .type(type).active(true).build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}

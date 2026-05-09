package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.cell.dto.CellMembershipCreateRequest;
import com.msc.church.cell.dto.CellMembershipUpdateRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CellMembershipServiceTest {

    @Mock CellRepository cellRepository;
    @Mock CellMembershipRepository membershipRepository;
    @Mock MemberRepository memberRepository;
    @Mock CellMapper cellMapper;

    @InjectMocks CellMembershipService service;

    private AuthenticatedUser admin;
    private Cell cell;
    private Member member;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a", null, Role.ADMIN, true);
        cell = Cell.builder().code("C").nameKr("셀 C").type(CellType.REGULAR).active(true).build();
        ReflectionTestUtils.setField(cell, "id", 10L);
        member = Member.builder().nameKr("X").baptized(false).status(MemberStatus.ACTIVE).build();
        ReflectionTestUtils.setField(member, "id", 100L);
    }

    @Test
    @DisplayName("addMembership: happy path")
    void add_happy() {
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByMember_IdAndCell_IdAndActiveTrue(100L, 10L)).thenReturn(false);
        when(membershipRepository.save(any(CellMembership.class))).thenAnswer(inv -> {
            CellMembership cm = inv.getArgument(0);
            ReflectionTestUtils.setField(cm, "id", 1L);
            return cm;
        });

        var req = new CellMembershipCreateRequest(100L, false, "VOCALIST", LocalDate.now());
        service.addMembership(10L, req, admin);

        verify(membershipRepository).save(any(CellMembership.class));
    }

    @Test
    @DisplayName("addMembership: rejects when an active membership already exists for (member, cell)")
    void add_duplicate() {
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByMember_IdAndCell_IdAndActiveTrue(100L, 10L)).thenReturn(true);

        var req = new CellMembershipCreateRequest(100L, false, null, null);
        assertThatThrownBy(() -> service.addMembership(10L, req, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(membershipRepository, never()).save(any(CellMembership.class));
    }

    @Test
    @DisplayName("addMembership: with isPrimary=true demotes prior primary in the same transaction")
    void add_primaryDemotesOld() {
        when(cellRepository.findById(10L)).thenReturn(Optional.of(cell));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByMember_IdAndCell_IdAndActiveTrue(100L, 10L)).thenReturn(false);
        when(membershipRepository.save(any(CellMembership.class))).thenAnswer(inv -> {
            CellMembership cm = inv.getArgument(0);
            ReflectionTestUtils.setField(cm, "id", 2L);
            return cm;
        });

        var req = new CellMembershipCreateRequest(100L, true, "MEMBER", null);
        service.addMembership(10L, req, admin);

        verify(membershipRepository, times(1)).clearPrimaryFor(100L);
    }

    @Test
    @DisplayName("removeMembership: sets active=false, leftAt=today, primary=false")
    void remove_happy() {
        CellMembership cm = CellMembership.builder()
                .cell(cell).member(member).primary(true).active(true).build();
        ReflectionTestUtils.setField(cm, "id", 5L);
        when(membershipRepository.findById(5L)).thenReturn(Optional.of(cm));

        service.removeMembership(5L, admin);

        assertThat(cm.isActive()).isFalse();
        assertThat(cm.getLeftAt()).isEqualTo(LocalDate.now());
        assertThat(cm.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("updateMembership: setting isPrimary=true clears previous primary, sets this one")
    void update_setPrimary() {
        CellMembership cm = CellMembership.builder()
                .cell(cell).member(member).primary(false).active(true).build();
        ReflectionTestUtils.setField(cm, "id", 5L);
        when(membershipRepository.findById(5L)).thenReturn(Optional.of(cm));

        service.updateMembership(5L, new CellMembershipUpdateRequest(true, "LEADER", null), admin);

        verify(membershipRepository).clearPrimaryFor(100L);
        assertThat(cm.isPrimary()).isTrue();
        assertThat(cm.getRole()).isEqualTo("LEADER");
    }
}

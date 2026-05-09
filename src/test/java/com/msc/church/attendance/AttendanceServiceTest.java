package com.msc.church.attendance;

import com.msc.church.attendance.dto.CellAttendanceEntry;
import com.msc.church.attendance.dto.CellAttendanceUpsertRequest;
import com.msc.church.attendance.dto.ServiceAttendanceEntry;
import com.msc.church.attendance.dto.ServiceAttendanceUpsertRequest;
import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.Bulletin;
import com.msc.church.bulletin.BulletinRepository;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellRepository;
import com.msc.church.cell.CellType;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock ServiceAttendanceRepository serviceRepository;
    @Mock BulletinRepository bulletinRepository;
    @Mock CellRepository cellRepository;

    @InjectMocks AttendanceService attendanceService;

    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a", null, Role.ADMIN, true);
    }

    @Test
    @DisplayName("upsertService: inserts a fresh row when none exists")
    void upsertService_insert() {
        when(serviceRepository.findByServiceDateAndServiceTypeAndAgeGroup(
                LocalDate.of(2026, 5, 17), "EWS", "ADULT")).thenReturn(Optional.empty());
        when(serviceRepository.save(any(ServiceAttendance.class))).thenAnswer(inv -> {
            ServiceAttendance r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1L);
            return r;
        });

        var req = new ServiceAttendanceUpsertRequest(
                LocalDate.of(2026, 5, 17), "EWS",
                List.of(new ServiceAttendanceEntry(AgeGroup.ADULT, 65)));
        var rows = attendanceService.upsertService(req, admin);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).count()).isEqualTo(65);
    }

    @Test
    @DisplayName("upsertService: updates existing row with new count")
    void upsertService_update() {
        ServiceAttendance existing = ServiceAttendance.builder()
                .serviceDate(LocalDate.of(2026, 5, 17)).serviceType("EWS")
                .ageGroup("ADULT").count(60).build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        when(serviceRepository.findByServiceDateAndServiceTypeAndAgeGroup(
                LocalDate.of(2026, 5, 17), "EWS", "ADULT")).thenReturn(Optional.of(existing));
        when(serviceRepository.save(any(ServiceAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new ServiceAttendanceUpsertRequest(
                LocalDate.of(2026, 5, 17), "EWS",
                List.of(new ServiceAttendanceEntry(AgeGroup.ADULT, 70)));
        attendanceService.upsertService(req, admin);

        assertThat(existing.getCount()).isEqualTo(70);
        verify(serviceRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("upsertCell: rejects when no bulletin exists for that date")
    void upsertCell_noBulletin() {
        when(bulletinRepository.findByServiceDate(LocalDate.of(2026, 5, 17)))
                .thenReturn(Optional.empty());

        var req = new CellAttendanceUpsertRequest(
                LocalDate.of(2026, 5, 17),
                List.of(new CellAttendanceEntry(1L, 7)));

        assertThatThrownBy(() -> attendanceService.upsertCell(req, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BULLETIN_NOT_FOUND);
    }

    @Test
    @DisplayName("upsertCell: LEADER refused for cells they don't lead")
    void upsertCell_leaderForbidden() {
        Bulletin b = Bulletin.builder()
                .serviceDate(LocalDate.of(2026, 5, 17))
                .cellAttendance(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(b, "id", 1L);
        when(bulletinRepository.findByServiceDate(LocalDate.of(2026, 5, 17)))
                .thenReturn(Optional.of(b));
        // Caller is LEADER with memberId=42 but leads no cells.
        when(cellRepository.findByLeader_Id(42L)).thenReturn(List.of());

        var leader = new AuthenticatedUser(2L, "l", 42L, Role.LEADER, true);
        var req = new CellAttendanceUpsertRequest(
                LocalDate.of(2026, 5, 17),
                List.of(new CellAttendanceEntry(99L, 7)));
        assertThatThrownBy(() -> attendanceService.upsertCell(req, leader))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("upsertCell: ADMIN can write any cell, attaches to existing bulletin")
    void upsertCell_admin() {
        Cell cellC = Cell.builder().code("C").nameKr("셀 C").type(CellType.REGULAR).active(true).build();
        ReflectionTestUtils.setField(cellC, "id", 1L);
        Bulletin b = Bulletin.builder()
                .serviceDate(LocalDate.of(2026, 5, 17))
                .cellAttendance(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(b, "id", 1L);
        when(bulletinRepository.findByServiceDate(LocalDate.of(2026, 5, 17)))
                .thenReturn(Optional.of(b));
        when(cellRepository.findById(1L)).thenReturn(Optional.of(cellC));

        // listCell after the upsert returns nothing for now (mocked), but we can verify
        // the in-memory bulletin gained a cellAttendance row.
        when(bulletinRepository.findByServiceDateBetween(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var req = new CellAttendanceUpsertRequest(
                LocalDate.of(2026, 5, 17),
                List.of(new CellAttendanceEntry(1L, 7)));
        attendanceService.upsertCell(req, admin);

        assertThat(b.getCellAttendance()).hasSize(1);
        assertThat(b.getCellAttendance().get(0).getAttendanceCount()).isEqualTo(7);
    }
}

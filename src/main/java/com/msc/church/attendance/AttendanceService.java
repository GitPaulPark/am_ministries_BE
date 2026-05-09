package com.msc.church.attendance;

import com.msc.church.attendance.dto.CellAttendanceEntry;
import com.msc.church.attendance.dto.CellAttendanceRecord;
import com.msc.church.attendance.dto.CellAttendanceUpsertRequest;
import com.msc.church.attendance.dto.DashboardResponse;
import com.msc.church.attendance.dto.DashboardResponse.CellPoint;
import com.msc.church.attendance.dto.DashboardResponse.CellSeries;
import com.msc.church.attendance.dto.DashboardResponse.TotalsPoint;
import com.msc.church.attendance.dto.ServiceAttendanceEntry;
import com.msc.church.attendance.dto.ServiceAttendanceRecord;
import com.msc.church.attendance.dto.ServiceAttendanceUpsertRequest;
import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.Bulletin;
import com.msc.church.bulletin.BulletinCellAttendance;
import com.msc.church.bulletin.BulletinRepository;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance counts at two granularities:
 * <ul>
 *   <li><b>Service-level</b> — owned by this module's {@code service_attendance}
 *       table; upserts on the (date, type, ageGroup) unique key.</li>
 *   <li><b>Cell-level</b> — reads/writes the bulletin module's
 *       {@code bulletin_cell_attendance} rows so the bulletin admin form and the
 *       attendance entry form edit the same data.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final ServiceAttendanceRepository serviceRepository;
    private final BulletinRepository bulletinRepository;
    private final CellRepository cellRepository;

    // ---------- service-level ----------

    @Transactional(readOnly = true)
    public List<ServiceAttendanceRecord> listService(LocalDate from, LocalDate to, String serviceType) {
        LocalDate fromUse = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toUse = to != null ? to : LocalDate.of(9999, 12, 31);
        List<ServiceAttendance> rows = (serviceType == null || serviceType.isBlank())
                ? serviceRepository.findByServiceDateBetweenOrderByServiceDateAsc(fromUse, toUse)
                : serviceRepository.findByServiceDateBetweenAndServiceTypeOrderByServiceDateAsc(
                        fromUse, toUse, serviceType);
        return rows.stream()
                .map(r -> new ServiceAttendanceRecord(
                        r.getServiceDate(), r.getServiceType(), r.getAgeGroup(), r.getCount()))
                .toList();
    }

    @Transactional
    public List<ServiceAttendanceRecord> upsertService(ServiceAttendanceUpsertRequest request,
                                                       AuthenticatedUser caller) {
        List<ServiceAttendance> saved = new ArrayList<>(request.entries().size());
        for (ServiceAttendanceEntry e : request.entries()) {
            ServiceAttendance row = serviceRepository
                    .findByServiceDateAndServiceTypeAndAgeGroup(
                            request.serviceDate(), request.serviceType(), e.ageGroup().name())
                    .orElseGet(() -> ServiceAttendance.builder()
                            .serviceDate(request.serviceDate())
                            .serviceType(request.serviceType())
                            .ageGroup(e.ageGroup().name())
                            .build());
            row.setCount(e.count());
            saved.add(serviceRepository.save(row));
        }
        log.info("Service attendance upserted: date={} type={} entries={} byUser={}",
                request.serviceDate(), request.serviceType(), saved.size(),
                caller == null ? null : caller.id());
        return saved.stream()
                .map(r -> new ServiceAttendanceRecord(
                        r.getServiceDate(), r.getServiceType(), r.getAgeGroup(), r.getCount()))
                .toList();
    }

    // ---------- cell-level ----------

    @Transactional(readOnly = true)
    public List<CellAttendanceRecord> listCell(LocalDate from, LocalDate to, Long cellId,
                                               AuthenticatedUser caller) {
        LocalDate fromUse = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toUse = to != null ? to : LocalDate.of(9999, 12, 31);
        List<Bulletin> bulletins = bulletinRepository
                .findByServiceDateBetween(fromUse, toUse,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        List<Long> ledIds = (caller != null && caller.role() == Role.LEADER)
                ? cellRepository.findByLeader_Id(caller.memberId()).stream()
                        .map(Cell::getId).toList()
                : null;

        List<CellAttendanceRecord> out = new ArrayList<>();
        for (Bulletin b : bulletins) {
            for (BulletinCellAttendance ca : b.getCellAttendance()) {
                if (cellId != null && !ca.getCell().getId().equals(cellId)) continue;
                if (ledIds != null && !ledIds.contains(ca.getCell().getId())) continue;
                Cell c = ca.getCell();
                out.add(new CellAttendanceRecord(
                        b.getServiceDate(), c.getId(), c.getCode(),
                        c.getNameKr(), c.getNameEn(), ca.getAttendanceCount()));
            }
        }
        out.sort(Comparator.comparing(CellAttendanceRecord::serviceDate)
                .thenComparing(CellAttendanceRecord::cellCode));
        return out;
    }

    @Transactional
    public List<CellAttendanceRecord> upsertCell(CellAttendanceUpsertRequest request,
                                                 AuthenticatedUser caller) {
        Bulletin bulletin = bulletinRepository.findByServiceDate(request.serviceDate())
                .orElseThrow(() -> new BusinessException(ErrorCode.BULLETIN_NOT_FOUND,
                        request.serviceDate()));

        // LEADER scope: refuse writes for any cell the caller doesn't lead.
        List<Long> ledIds = (caller != null && caller.role() == Role.LEADER)
                ? cellRepository.findByLeader_Id(caller.memberId()).stream()
                        .map(Cell::getId).toList()
                : null;

        Map<Long, BulletinCellAttendance> existing = new HashMap<>();
        for (BulletinCellAttendance ca : bulletin.getCellAttendance()) {
            existing.put(ca.getCell().getId(), ca);
        }

        for (CellAttendanceEntry e : request.entries()) {
            if (ledIds != null && !ledIds.contains(e.cellId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            BulletinCellAttendance row = existing.get(e.cellId());
            if (row != null) {
                row.setAttendanceCount(e.count());
            } else {
                Cell cell = cellRepository.findById(e.cellId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CELL_NOT_FOUND));
                BulletinCellAttendance fresh = BulletinCellAttendance.builder()
                        .bulletin(bulletin)
                        .cell(cell)
                        .attendanceCount(e.count())
                        .build();
                bulletin.getCellAttendance().add(fresh);
            }
        }
        log.info("Cell attendance upserted: date={} entries={} byUser={}",
                request.serviceDate(), request.entries().size(),
                caller == null ? null : caller.id());

        return listCell(request.serviceDate(), request.serviceDate(), null, caller);
    }

    // ---------- dashboard ----------

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(LocalDate from, LocalDate to, AuthenticatedUser caller) {
        LocalDate fromUse = from != null ? from : LocalDate.now().minusMonths(6);
        LocalDate toUse = to != null ? to : LocalDate.now();

        List<ServiceAttendance> serviceRows = serviceRepository
                .findByServiceDateBetweenOrderByServiceDateAsc(fromUse, toUse);

        // Group by (date, type) → totals + per-age map.
        Map<String, TotalsAccumulator> acc = new HashMap<>();
        for (ServiceAttendance r : serviceRows) {
            String k = r.getServiceDate() + "|" + r.getServiceType();
            acc.computeIfAbsent(k, key -> new TotalsAccumulator(r.getServiceDate(), r.getServiceType()))
                    .add(r.getAgeGroup(), r.getCount());
        }
        List<TotalsPoint> totals = acc.values().stream()
                .map(TotalsAccumulator::toPoint)
                .sorted(Comparator
                        .comparing(TotalsPoint::serviceDate)
                        .thenComparing(TotalsPoint::serviceType))
                .toList();

        // Cells: re-use listCell; group server-side by cell.
        List<CellAttendanceRecord> cellRows = listCell(fromUse, toUse, null, caller);
        Map<Long, CellSeriesAccumulator> cellAcc = new HashMap<>();
        for (CellAttendanceRecord r : cellRows) {
            cellAcc.computeIfAbsent(r.cellId(),
                            k -> new CellSeriesAccumulator(r.cellId(), r.cellCode(), r.cellNameKr()))
                    .add(r.serviceDate(), r.count());
        }
        List<CellSeries> byCell = cellAcc.values().stream()
                .map(CellSeriesAccumulator::toSeries)
                .sorted(Comparator.comparing(CellSeries::cellCode))
                .toList();

        return new DashboardResponse(totals, byCell);
    }

    // ---------- accumulators ----------

    private static class TotalsAccumulator {
        final LocalDate date;
        final String type;
        int total = 0;
        final Map<String, Integer> byAge = new HashMap<>();

        TotalsAccumulator(LocalDate date, String type) {
            this.date = date;
            this.type = type;
        }

        void add(String ageGroup, int count) {
            total += count;
            byAge.merge(ageGroup, count, Integer::sum);
        }

        TotalsPoint toPoint() {
            return new TotalsPoint(date, type, total, Map.copyOf(byAge));
        }
    }

    private static class CellSeriesAccumulator {
        final Long cellId;
        final String cellCode;
        final String cellNameKr;
        final List<CellPoint> series = new ArrayList<>();

        CellSeriesAccumulator(Long cellId, String cellCode, String cellNameKr) {
            this.cellId = cellId;
            this.cellCode = cellCode;
            this.cellNameKr = cellNameKr;
        }

        void add(LocalDate date, int count) {
            series.add(new CellPoint(date, count));
        }

        CellSeries toSeries() {
            series.sort(Comparator.comparing(CellPoint::serviceDate));
            return new CellSeries(cellId, cellCode, cellNameKr, List.copyOf(series));
        }
    }
}

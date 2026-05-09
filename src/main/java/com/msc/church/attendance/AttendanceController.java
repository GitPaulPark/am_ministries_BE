package com.msc.church.attendance;

import com.msc.church.attendance.dto.CellAttendanceRecord;
import com.msc.church.attendance.dto.CellAttendanceUpsertRequest;
import com.msc.church.attendance.dto.DashboardResponse;
import com.msc.church.attendance.dto.ServiceAttendanceRecord;
import com.msc.church.attendance.dto.ServiceAttendanceUpsertRequest;
import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/service")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<List<ServiceAttendanceRecord>> listService(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String serviceType) {
        return ApiResponse.ok(attendanceService.listService(from, to, serviceType));
    }

    @PostMapping("/service")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<List<ServiceAttendanceRecord>> upsertService(
            @Valid @RequestBody ServiceAttendanceUpsertRequest request) {
        return ApiResponse.ok(attendanceService.upsertService(request, SecurityUtil.currentUser()));
    }

    @GetMapping("/cells")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<List<CellAttendanceRecord>> listCells(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long cellId) {
        return ApiResponse.ok(attendanceService.listCell(from, to, cellId, SecurityUtil.currentUser()));
    }

    @PostMapping("/cells")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<List<CellAttendanceRecord>> upsertCells(
            @Valid @RequestBody CellAttendanceUpsertRequest request) {
        return ApiResponse.ok(attendanceService.upsertCell(request, SecurityUtil.currentUser()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.dashboard(from, to, SecurityUtil.currentUser()));
    }
}

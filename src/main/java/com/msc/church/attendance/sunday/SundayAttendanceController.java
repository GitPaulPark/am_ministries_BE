package com.msc.church.attendance.sunday;

import com.msc.church.attendance.sunday.dto.SundayAttendanceDto;
import com.msc.church.attendance.sunday.dto.SundayAttendanceRequest;
import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sunday cell attendance roster check-ins. Auth lives in
 * {@link SundayAttendanceService}; controller only enforces that the caller is
 * authenticated. Cell-leader scoping happens at the service layer.
 */
@RestController
@RequestMapping("/api/v1/cells/{cellId}/sunday-attendance")
@RequiredArgsConstructor
public class SundayAttendanceController {

    private final SundayAttendanceService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SundayAttendanceDto>> list(@PathVariable Long cellId) {
        return ApiResponse.ok(service.listByCell(cellId, SecurityUtil.currentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SundayAttendanceDto> get(@PathVariable Long cellId,
                                                @PathVariable Long id) {
        return ApiResponse.ok(service.get(id, SecurityUtil.currentUser()));
    }

    /** Upsert by (cellId, serviceDate). */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SundayAttendanceDto> upsert(@PathVariable Long cellId,
                                                   @Valid @RequestBody SundayAttendanceRequest req) {
        return ApiResponse.ok(service.upsert(cellId, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long cellId, @PathVariable Long id) {
        service.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

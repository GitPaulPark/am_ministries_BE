package com.msc.church.attendance.dept;

import com.msc.church.attendance.dept.dto.DepartmentAttendanceDto;
import com.msc.church.attendance.dept.dto.DepartmentAttendanceRequest;
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
 * Department-level (committee) attendance roster check-ins. Fine-grained
 * access decisions live in {@link DepartmentAttendanceService}: every endpoint
 * is reachable by any authenticated user, but the service rejects callers who
 * are not staff / HQ board / committee members or officers.
 */
@RestController
@RequestMapping("/api/v1/committees/{committeeId}/attendance")
@RequiredArgsConstructor
public class DepartmentAttendanceController {

    private final DepartmentAttendanceService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DepartmentAttendanceDto>> list(@PathVariable Long committeeId) {
        return ApiResponse.ok(service.listByCommittee(committeeId, SecurityUtil.currentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DepartmentAttendanceDto> get(@PathVariable Long committeeId,
                                                    @PathVariable Long id) {
        return ApiResponse.ok(service.get(id, SecurityUtil.currentUser()));
    }

    /** Upsert by (committeeId, eventDate, eventLabel). */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DepartmentAttendanceDto> upsert(@PathVariable Long committeeId,
                                                       @Valid @RequestBody DepartmentAttendanceRequest req) {
        return ApiResponse.ok(service.upsert(committeeId, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long committeeId, @PathVariable Long id) {
        service.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

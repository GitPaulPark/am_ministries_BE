package com.msc.church.attendance.dept;

import com.msc.church.attendance.dept.dto.DropoffDto;
import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Personal slice of department attendance — dashboard-relevant queries scoped
 * to the current user.
 */
@RestController
@RequestMapping("/api/v1/me/dept-attendance")
@RequiredArgsConstructor
public class MyDeptAttendanceController {

    private final DepartmentAttendanceService service;

    @GetMapping("/dropoffs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DropoffDto>> dropoffs() {
        return ApiResponse.ok(service.findDropoffs(SecurityUtil.currentUser()));
    }
}

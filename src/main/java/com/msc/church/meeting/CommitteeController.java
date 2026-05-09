package com.msc.church.meeting;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.meeting.dto.CommitteeCreateRequest;
import com.msc.church.meeting.dto.CommitteeDetail;
import com.msc.church.meeting.dto.CommitteeMembershipRequest;
import com.msc.church.meeting.dto.CommitteeMembershipResponse;
import com.msc.church.meeting.dto.CommitteeSummary;
import com.msc.church.meeting.dto.CommitteeUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/committees")
@RequiredArgsConstructor
public class CommitteeController {

    private final CommitteeService committeeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CommitteeSummary>> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(committeeService.list(includeInactive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CommitteeDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(committeeService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CommitteeDetail> create(@Valid @RequestBody CommitteeCreateRequest req) {
        return ApiResponse.ok(committeeService.create(req, SecurityUtil.currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<CommitteeDetail> update(@PathVariable Long id,
                                               @Valid @RequestBody CommitteeUpdateRequest req) {
        return ApiResponse.ok(committeeService.update(id, req, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/memberships")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<CommitteeMembershipResponse> addMembership(
            @PathVariable Long id,
            @Valid @RequestBody CommitteeMembershipRequest req) {
        return ApiResponse.ok(committeeService.addMembership(id, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}/memberships/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<Void> removeMembership(@PathVariable Long id,
                                              @PathVariable Long membershipId) {
        committeeService.removeMembership(id, membershipId, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

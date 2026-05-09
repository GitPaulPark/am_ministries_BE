package com.msc.church.cell;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.cell.dto.CellCreateRequest;
import com.msc.church.cell.dto.CellDetail;
import com.msc.church.cell.dto.CellMembershipCreateRequest;
import com.msc.church.cell.dto.CellMembershipResponse;
import com.msc.church.cell.dto.CellMembershipUpdateRequest;
import com.msc.church.cell.dto.CellSummary;
import com.msc.church.cell.dto.CellUpdateRequest;
import com.msc.church.common.ApiResponse;
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
@RequestMapping("/api/v1/cells")
@RequiredArgsConstructor
public class CellController {

    private final CellService cellService;
    private final CellMembershipService membershipService;

    // ---------- Cell CRUD ----------

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CellSummary>> list(
            @RequestParam(required = false) CellType type,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(cellService.list(type, includeInactive));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CellDetail> myCell() {
        return ApiResponse.ok(cellService.getMyPrimaryCell(SecurityUtil.currentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CellDetail> get(@PathVariable Long id,
                                       @RequestParam(required = false, defaultValue = "false") boolean includeInactiveRoster) {
        return ApiResponse.ok(cellService.get(id, includeInactiveRoster));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<CellDetail> create(@Valid @RequestBody CellCreateRequest request) {
        return ApiResponse.ok(cellService.create(request, SecurityUtil.currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<CellDetail> update(@PathVariable Long id,
                                          @Valid @RequestBody CellUpdateRequest request) {
        return ApiResponse.ok(cellService.update(id, request, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        cellService.deactivate(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }

    // ---------- Memberships ----------

    @PostMapping("/{cellId}/memberships")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<CellMembershipResponse> addMembership(@PathVariable Long cellId,
                                                             @Valid @RequestBody CellMembershipCreateRequest request) {
        return ApiResponse.ok(membershipService.addMembership(cellId, request, SecurityUtil.currentUser()));
    }

    @PutMapping("/{cellId}/memberships/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<CellMembershipResponse> updateMembership(
            @PathVariable Long cellId,
            @PathVariable Long membershipId,
            @Valid @RequestBody CellMembershipUpdateRequest request) {
        return ApiResponse.ok(membershipService.updateMembership(membershipId, request, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{cellId}/memberships/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<Void> removeMembership(@PathVariable Long cellId,
                                              @PathVariable Long membershipId) {
        membershipService.removeMembership(membershipId, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

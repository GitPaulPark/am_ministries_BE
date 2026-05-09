package com.msc.church.meeting;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.meeting.dto.ActionItemRequest;
import com.msc.church.meeting.dto.ActionItemResponse;
import com.msc.church.meeting.dto.ActionItemStatusRequest;
import com.msc.church.meeting.dto.MeetingCreateRequest;
import com.msc.church.meeting.dto.MeetingDetail;
import com.msc.church.meeting.dto.MeetingDuplicateRequest;
import com.msc.church.meeting.dto.MeetingSummary;
import com.msc.church.meeting.dto.MeetingUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final ActionItemService actionItemService;

    @GetMapping("/committees/{committeeId}/meetings")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<MeetingSummary>> list(@PathVariable Long committeeId,
                                                  @RequestParam(required = false) MeetingStatus status,
                                                  Pageable pageable) {
        return ApiResponse.ok(meetingService.list(committeeId, status, pageable, SecurityUtil.currentUser()));
    }

    @GetMapping("/meetings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.get(id, SecurityUtil.currentUser()));
    }

    @PostMapping("/meetings")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MeetingDetail> create(@Valid @RequestBody MeetingCreateRequest req) {
        return ApiResponse.ok(meetingService.create(req, SecurityUtil.currentUser()));
    }

    @PutMapping("/meetings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MeetingDetail> update(@PathVariable Long id,
                                             @Valid @RequestBody MeetingUpdateRequest req) {
        return ApiResponse.ok(meetingService.update(id, req, SecurityUtil.currentUser()));
    }

    @PostMapping("/meetings/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MeetingDetail> duplicate(@PathVariable Long id,
                                                @Valid @RequestBody MeetingDuplicateRequest req) {
        return ApiResponse.ok(meetingService.duplicate(id, req, SecurityUtil.currentUser()));
    }

    @PostMapping("/meetings/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MeetingDetail> publish(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.publish(id, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/meetings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        meetingService.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }

    // ---------- Action items ----------

    @PostMapping("/meetings/{meetingId}/action-items")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<ActionItemResponse> createActionItem(@PathVariable Long meetingId,
                                                            @Valid @RequestBody ActionItemRequest req) {
        return ApiResponse.ok(actionItemService.create(meetingId, req, SecurityUtil.currentUser()));
    }

    @PutMapping("/action-items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<ActionItemResponse> updateActionItem(@PathVariable Long id,
                                                            @Valid @RequestBody ActionItemRequest req) {
        return ApiResponse.ok(actionItemService.update(id, req, SecurityUtil.currentUser()));
    }

    @PatchMapping("/action-items/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ActionItemResponse> setStatus(@PathVariable Long id,
                                                     @Valid @RequestBody ActionItemStatusRequest req) {
        return ApiResponse.ok(actionItemService.setStatus(id, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/action-items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<Void> deleteActionItem(@PathVariable Long id) {
        actionItemService.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }

    @GetMapping("/action-items/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ActionItemResponse>> myOpen() {
        return ApiResponse.ok(actionItemService.myOpen(SecurityUtil.currentUser()));
    }
}

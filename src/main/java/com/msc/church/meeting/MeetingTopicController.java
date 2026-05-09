package com.msc.church.meeting;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.meeting.dto.MeetingTopicCreateRequest;
import com.msc.church.meeting.dto.MeetingTopicResponse;
import com.msc.church.meeting.dto.MeetingTopicStatusRequest;
import com.msc.church.meeting.dto.MeetingTopicUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeetingTopicController {

    private final MeetingTopicService topicService;

    @PostMapping("/meetings/{meetingId}/topics")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingTopicResponse> create(@PathVariable Long meetingId,
                                                    @Valid @RequestBody MeetingTopicCreateRequest req) {
        return ApiResponse.ok(topicService.create(meetingId, req, SecurityUtil.currentUser()));
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingTopicResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody MeetingTopicUpdateRequest req) {
        return ApiResponse.ok(topicService.update(id, req, SecurityUtil.currentUser()));
    }

    @PatchMapping("/topics/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingTopicResponse> setStatus(@PathVariable Long id,
                                                       @Valid @RequestBody MeetingTopicStatusRequest req) {
        return ApiResponse.ok(topicService.setStatus(id, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/topics/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        topicService.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

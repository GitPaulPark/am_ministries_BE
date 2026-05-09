package com.msc.church.meeting.ai;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.meeting.ai.dto.MeetingProcessingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Audio + AI-processing endpoints. Lives in the {@code ai} package to keep all
 * AI-coupled code together; the URL space stays consistent with
 * {@link com.msc.church.meeting.MeetingController}'s {@code /api/v1/meetings/{id}}.
 */
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}")
@RequiredArgsConstructor
public class MeetingAudioController {

    private final MeetingAudioService audioService;

    /**
     * Upload an .m4a (iPhone Voice Memo) or .mp3, kicks off async transcription +
     * Claude analysis. Returns the snapshot of the just-queued processing state.
     */
    @PostMapping(path = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingProcessingResponse> upload(
            @PathVariable Long meetingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) throws IOException {
        return ApiResponse.ok(audioService.upload(meetingId, file, language, SecurityUtil.currentUser()));
    }

    /** Stream the stored audio file back to authorized callers (no public URL). */
    @GetMapping("/audio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable Long meetingId) {
        Resource resource = audioService.streamAudio(meetingId, SecurityUtil.currentUser());
        String filename = resource.getFilename() != null ? resource.getFilename() : "audio";
        MediaType type = filename.endsWith(".mp3") ? MediaType.parseMediaType("audio/mpeg")
                                                   : MediaType.parseMediaType("audio/mp4");
        return ResponseEntity.ok()
                .contentType(type)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    /** Polled by the frontend while processing is running. */
    @GetMapping("/processing")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeetingProcessingResponse> status(@PathVariable Long meetingId) {
        return ApiResponse.ok(audioService.getProcessingStatus(meetingId, SecurityUtil.currentUser()));
    }
}

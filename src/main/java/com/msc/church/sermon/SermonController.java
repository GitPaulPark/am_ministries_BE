package com.msc.church.sermon;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.sermon.dto.ParagraphBulkReplaceRequest;
import com.msc.church.sermon.dto.ParagraphDto;
import com.msc.church.sermon.dto.SermonAudioResponse;
import com.msc.church.sermon.dto.SermonCreateRequest;
import com.msc.church.sermon.dto.SermonDetail;
import com.msc.church.sermon.dto.SermonSummary;
import com.msc.church.sermon.dto.SermonTranscriptResponse;
import com.msc.church.sermon.dto.SermonUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/sermons")
@RequiredArgsConstructor
public class SermonController {

    private final SermonService sermonService;
    private final SermonTranscriptService transcriptService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<SermonSummary>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String scripture,
                                                 @RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Long preacherId,
                                                 Pageable pageable) {
        return ApiResponse.ok(sermonService.search(q, scripture, year, preacherId, pageable, SecurityUtil.currentUser()));
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SermonDetail> latest() {
        return ApiResponse.ok(sermonService.latest());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SermonDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(sermonService.get(id, SecurityUtil.currentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<SermonDetail> create(@Valid @RequestBody SermonCreateRequest request) {
        return ApiResponse.ok(sermonService.create(request, SecurityUtil.currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<SermonDetail> update(@PathVariable Long id,
                                            @Valid @RequestBody SermonUpdateRequest request) {
        return ApiResponse.ok(sermonService.update(id, request, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<SermonDetail> publish(@PathVariable Long id) {
        return ApiResponse.ok(sermonService.publish(id, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/upload-audio")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<SermonAudioResponse> uploadAudio(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(sermonService.uploadAudio(id, file, SecurityUtil.currentUser()));
    }

    /**
     * Upload a bilingual transcript PDF — parsed into sermon_paragraphs and surfaced
     * via the SermonDetail.transcript field. Replaces any prior parsed paragraphs.
     */
    @PostMapping("/{id}/transcript-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<SermonTranscriptResponse> uploadTranscriptPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(transcriptService.uploadPdf(id, file, SecurityUtil.currentUser()));
    }

    /** Raw paragraph list for the admin transcript editor. */
    @GetMapping("/{id}/paragraphs")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<java.util.List<ParagraphDto>> listParagraphs(@PathVariable Long id) {
        return ApiResponse.ok(transcriptService.listParagraphs(id, SecurityUtil.currentUser()));
    }

    /** Bulk-replace the paragraph list — diff-driven update. */
    @PutMapping("/{id}/paragraphs")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<java.util.List<ParagraphDto>> replaceParagraphs(
            @PathVariable Long id,
            @Valid @RequestBody ParagraphBulkReplaceRequest req) {
        return ApiResponse.ok(transcriptService.bulkReplaceParagraphs(id, req, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sermonService.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

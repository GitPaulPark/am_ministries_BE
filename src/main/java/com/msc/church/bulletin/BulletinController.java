package com.msc.church.bulletin;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.bulletin.dto.BulletinCreateRequest;
import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.bulletin.dto.BulletinDuplicateRequest;
import com.msc.church.bulletin.dto.BulletinPdfResponse;
import com.msc.church.bulletin.dto.BulletinSummary;
import com.msc.church.bulletin.dto.BulletinUpdateRequest;
import com.msc.church.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/bulletins")
@RequiredArgsConstructor
public class BulletinController {

    private final BulletinService bulletinService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<BulletinSummary>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean published,
            Pageable pageable) {
        return ApiResponse.ok(bulletinService.list(from, to, published, pageable, SecurityUtil.currentUser()));
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BulletinDetail> current() {
        return ApiResponse.ok(bulletinService.current(SecurityUtil.currentUser()));
    }

    @GetMapping("/by-date/{date}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BulletinDetail> byDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(bulletinService.getByDate(date, SecurityUtil.currentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BulletinDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(bulletinService.get(id, SecurityUtil.currentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<BulletinDetail> create(@Valid @RequestBody BulletinCreateRequest request) {
        return ApiResponse.ok(bulletinService.create(request, SecurityUtil.currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<BulletinDetail> update(@PathVariable Long id,
                                              @Valid @RequestBody BulletinUpdateRequest request) {
        return ApiResponse.ok(bulletinService.update(id, request, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<BulletinDetail> duplicate(@PathVariable Long id,
                                                 @Valid @RequestBody BulletinDuplicateRequest request) {
        return ApiResponse.ok(bulletinService.duplicate(id, request, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<BulletinDetail> publish(@PathVariable Long id) {
        return ApiResponse.ok(bulletinService.publish(id, SecurityUtil.currentUser()));
    }

    /** Upload a weekly-bulletin PDF — makes a bulletin publishable without the
     *  structured liturgy/scripture/presider fields. Overwrites any prior upload. */
    @PostMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<BulletinPdfResponse> uploadPdf(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new BulletinPdfResponse(
                bulletinService.uploadPdf(id, file, SecurityUtil.currentUser())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bulletinService.delete(id, SecurityUtil.currentUser());
        return ApiResponse.ok();
    }
}

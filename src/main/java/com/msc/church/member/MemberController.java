package com.msc.church.member;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.common.ApiResponse;
import com.msc.church.member.dto.MemberCreateRequest;
import com.msc.church.member.dto.MemberDetail;
import com.msc.church.member.dto.MemberImportResult;
import com.msc.church.member.dto.MemberSelfUpdateRequest;
import com.msc.church.member.dto.MemberSummary;
import com.msc.church.member.dto.MemberUpdateRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberImportService memberImportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR','LEADER')")
    public ApiResponse<Page<MemberSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long cellId,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String roleLabel,
            Pageable pageable) {
        var filter = new MemberListFilter(q, cellId, status, roleLabel);
        return ApiResponse.ok(memberService.list(filter, pageable, SecurityUtil.currentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MemberDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(memberService.get(id, SecurityUtil.currentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MemberDetail> create(@Valid @RequestBody MemberCreateRequest request) {
        return ApiResponse.ok(memberService.create(request, SecurityUtil.currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<MemberDetail> update(@PathVariable Long id,
                                            @Valid @RequestBody MemberUpdateRequest request) {
        return ApiResponse.ok(memberService.update(id, request, SecurityUtil.currentUser()));
    }

    @PatchMapping("/{id}/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MemberDetail> selfUpdate(@PathVariable Long id,
                                                @Valid @RequestBody MemberSelfUpdateRequest request) {
        return ApiResponse.ok(memberService.selfUpdate(id, request, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MemberImportResult> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(memberImportService.importCsv(file));
    }
}

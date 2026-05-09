package com.msc.church.cell;

import com.msc.church.auth.SecurityUtil;
import com.msc.church.cell.dto.TransferApproveRequest;
import com.msc.church.cell.dto.TransferDismissRequest;
import com.msc.church.cell.dto.TransferSuggestionResponse;
import com.msc.church.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cells/transfer-suggestions")
@RequiredArgsConstructor
public class CellTransferSuggestionController {

    private final CellTransferSuggestionService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<List<TransferSuggestionResponse>> listPending() {
        return ApiResponse.ok(service.listPending());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<TransferSuggestionResponse> approve(@PathVariable Long id,
                                                           @Valid @RequestBody TransferApproveRequest request) {
        return ApiResponse.ok(service.approve(id, request, SecurityUtil.currentUser()));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','PASTOR')")
    public ApiResponse<TransferSuggestionResponse> dismiss(@PathVariable Long id,
                                                           @RequestBody(required = false) TransferDismissRequest request) {
        return ApiResponse.ok(service.dismiss(id, request, SecurityUtil.currentUser()));
    }
}

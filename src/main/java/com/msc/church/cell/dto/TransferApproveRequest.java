package com.msc.church.cell.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferApproveRequest(
        @NotNull Long toCellId,
        @Size(max = 1000) String notes
) {
}

package com.msc.church.cell.dto;

import jakarta.validation.constraints.Size;

public record TransferDismissRequest(
        @Size(max = 1000) String notes
) {
}

package com.msc.church.cell.dto;

import com.msc.church.cell.TransferSuggestionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransferSuggestionResponse(
        Long id,
        CellMemberRef member,
        LocalDate memberJoinedAt,
        CellRefSlim fromCell,
        CellRefSlim toCell,
        LocalDateTime suggestedAt,
        TransferSuggestionStatus status,
        String notes
) {
    public record CellRefSlim(Long id, String code, String nameKr, String nameEn) {
    }
}

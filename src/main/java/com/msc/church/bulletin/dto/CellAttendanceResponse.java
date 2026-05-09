package com.msc.church.bulletin.dto;

import com.msc.church.cell.CellType;

public record CellAttendanceResponse(
        Long id,
        Long cellId,
        String cellCode,
        String cellNameKr,
        String cellNameEn,
        CellType cellType,
        Integer attendanceCount
) {
}

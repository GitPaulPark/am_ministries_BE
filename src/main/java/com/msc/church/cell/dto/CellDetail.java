package com.msc.church.cell.dto;

import com.msc.church.cell.CellType;

import java.util.List;

public record CellDetail(
        Long id,
        String code,
        String nameKr,
        String nameEn,
        CellType type,
        CellMemberRef leader,
        String meetingDay,
        String meetingTime,
        String meetingLocation,
        String description,
        boolean active,
        List<CellRosterEntry> members
) {
}

package com.msc.church.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        List<TotalsPoint> totals,
        List<CellSeries> byCell
) {
    public record TotalsPoint(
            LocalDate serviceDate,
            String serviceType,
            int total,
            Map<String, Integer> byAgeGroup
    ) {
    }

    public record CellSeries(
            Long cellId,
            String cellCode,
            String cellNameKr,
            List<CellPoint> series
    ) {
    }

    public record CellPoint(LocalDate serviceDate, int count) {
    }
}

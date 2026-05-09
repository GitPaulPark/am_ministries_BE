package com.msc.church.cell;

import com.msc.church.cell.dto.CellListItem;
import com.msc.church.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only list endpoint for V1. Full CRUD and the cell-detail roster ship in Week 5.
 */
@RestController
@RequestMapping("/api/v1/cells")
@RequiredArgsConstructor
public class CellController {

    private final CellRepository cellRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CellListItem>> list(@RequestParam(required = false) CellType type) {
        Sort sort = Sort.by(Sort.Order.asc("code"));
        List<Cell> cells = type == null
                ? cellRepository.findByActiveTrue(sort)
                : cellRepository.findByActiveTrueAndType(type, sort);
        List<CellListItem> items = cells.stream()
                .map(c -> new CellListItem(
                        c.getId(), c.getCode(), c.getNameKr(), c.getNameEn(),
                        c.getType(), c.isActive()))
                .toList();
        return ApiResponse.ok(items);
    }
}

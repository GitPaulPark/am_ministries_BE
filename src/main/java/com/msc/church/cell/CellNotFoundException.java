package com.msc.church.cell;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class CellNotFoundException extends NotFoundException {
    public CellNotFoundException(Long id) {
        super(ErrorCode.CELL_NOT_FOUND, id);
    }
    public CellNotFoundException(String identifier) {
        super(ErrorCode.CELL_NOT_FOUND, identifier);
    }
}

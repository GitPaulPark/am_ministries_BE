package com.msc.church.cell;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class CellMembershipNotFoundException extends NotFoundException {
    public CellMembershipNotFoundException(Long id) {
        super(ErrorCode.NOT_FOUND, id);
    }
}

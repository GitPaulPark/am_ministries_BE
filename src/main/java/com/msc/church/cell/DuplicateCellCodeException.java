package com.msc.church.cell;

import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;

public class DuplicateCellCodeException extends DuplicateException {
    public DuplicateCellCodeException(String code) {
        super(ErrorCode.DUPLICATE_CELL_CODE, code);
    }
}

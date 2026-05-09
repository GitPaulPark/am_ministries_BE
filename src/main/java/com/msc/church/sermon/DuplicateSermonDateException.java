package com.msc.church.sermon;

import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;

import java.time.LocalDate;

public class DuplicateSermonDateException extends DuplicateException {
    public DuplicateSermonDateException(LocalDate date) {
        super(ErrorCode.DUPLICATE_SERMON_DATE, date);
    }
}

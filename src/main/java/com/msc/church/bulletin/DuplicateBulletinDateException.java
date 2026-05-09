package com.msc.church.bulletin;

import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;

import java.time.LocalDate;

public class DuplicateBulletinDateException extends DuplicateException {
    public DuplicateBulletinDateException(LocalDate date) {
        super(ErrorCode.DUPLICATE_BULLETIN_DATE, date);
    }
}

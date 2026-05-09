package com.msc.church.member;

import com.msc.church.common.DuplicateException;
import com.msc.church.common.ErrorCode;

public class DuplicateEmailException extends DuplicateException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, email);
    }
}

package com.msc.church.member;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class MemberNotFoundException extends NotFoundException {

    public MemberNotFoundException(Long id) {
        super(ErrorCode.MEMBER_NOT_FOUND, id);
    }
}

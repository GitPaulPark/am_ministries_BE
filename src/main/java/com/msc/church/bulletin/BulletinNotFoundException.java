package com.msc.church.bulletin;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class BulletinNotFoundException extends NotFoundException {
    public BulletinNotFoundException(Long id) {
        super(ErrorCode.BULLETIN_NOT_FOUND, id);
    }

    public BulletinNotFoundException(String identifier) {
        super(ErrorCode.BULLETIN_NOT_FOUND, identifier);
    }
}

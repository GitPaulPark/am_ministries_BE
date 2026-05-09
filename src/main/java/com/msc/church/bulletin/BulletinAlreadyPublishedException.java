package com.msc.church.bulletin;

import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;

public class BulletinAlreadyPublishedException extends BusinessException {
    public BulletinAlreadyPublishedException(Long id) {
        super(ErrorCode.BULLETIN_ALREADY_PUBLISHED, id);
    }
}

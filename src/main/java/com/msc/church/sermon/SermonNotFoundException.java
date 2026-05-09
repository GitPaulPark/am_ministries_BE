package com.msc.church.sermon;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class SermonNotFoundException extends NotFoundException {
    public SermonNotFoundException(Long id) { super(ErrorCode.SERMON_NOT_FOUND, id); }
    public SermonNotFoundException(String tag) { super(ErrorCode.SERMON_NOT_FOUND, tag); }
}

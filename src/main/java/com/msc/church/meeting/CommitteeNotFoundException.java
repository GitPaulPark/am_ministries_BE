package com.msc.church.meeting;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class CommitteeNotFoundException extends NotFoundException {
    public CommitteeNotFoundException(Long id) { super(ErrorCode.COMMITTEE_NOT_FOUND, id); }
}

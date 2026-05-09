package com.msc.church.meeting;

import com.msc.church.common.ErrorCode;
import com.msc.church.common.NotFoundException;

public class MeetingNotFoundException extends NotFoundException {
    public MeetingNotFoundException(Long id) { super(ErrorCode.MEETING_NOT_FOUND, id); }
}

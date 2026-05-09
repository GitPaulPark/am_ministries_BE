package com.msc.church.meeting;

import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;

public class MeetingTopicNotFoundException extends BusinessException {

    public MeetingTopicNotFoundException(Long id) {
        super(ErrorCode.MEETING_TOPIC_NOT_FOUND, id);
    }
}

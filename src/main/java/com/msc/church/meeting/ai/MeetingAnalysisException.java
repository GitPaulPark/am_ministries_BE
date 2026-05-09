package com.msc.church.meeting.ai;

/** Wraps Claude analysis failures (parse errors, malformed JSON, API errors). */
public class MeetingAnalysisException extends RuntimeException {

    public MeetingAnalysisException(String message) {
        super(message);
    }

    public MeetingAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}

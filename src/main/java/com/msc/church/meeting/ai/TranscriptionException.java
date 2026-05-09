package com.msc.church.meeting.ai;

/**
 * Wraps transcription failures (provider-side, network, decoding). The orchestrator
 * surfaces this to {@link com.msc.church.meeting.MeetingProcessingJob#getErrorMessage()}.
 */
public class TranscriptionException extends RuntimeException {

    public TranscriptionException(String message) {
        super(message);
    }

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

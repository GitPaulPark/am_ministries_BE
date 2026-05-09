package com.msc.church.meeting;

/**
 * Lifecycle of an AI run for a meeting (audio → transcript → topics).
 *
 * <p>Stored on both {@link Meeting#getProcessingStatus()} (current state) and
 * {@link MeetingProcessingJob#getStatus()} (audit trail per attempt).
 */
public enum MeetingProcessingStatus {
    /** No audio uploaded yet. The default. */
    NONE,
    /** Audio saved, async pipeline scheduled. */
    QUEUED,
    /** Whisper transcription in progress. */
    TRANSCRIBING,
    /** Claude analysis in progress. */
    ANALYZING,
    /** Topics + action items populated. */
    COMPLETE,
    /** See {@code processing_error} for the cause. */
    FAILED
}

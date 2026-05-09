package com.msc.church.meeting.ai;

/**
 * Transcription result returned by a {@link TranscriptionService}.
 *
 * @param text         full transcript (Korean, free-form)
 * @param durationSec  audio duration in seconds (provider-supplied; may be null)
 * @param provider     provider tag for audit (e.g. {@code "groq"})
 * @param model        provider-specific model id (e.g. {@code "whisper-large-v3"})
 * @param wallSeconds  wall-clock time the transcription took
 */
public record Transcript(
        String text,
        Integer durationSec,
        String provider,
        String model,
        Integer wallSeconds) {
}

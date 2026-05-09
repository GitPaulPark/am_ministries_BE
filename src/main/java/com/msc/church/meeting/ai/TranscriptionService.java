package com.msc.church.meeting.ai;

import java.nio.file.Path;

/**
 * Pluggable speech-to-text. Default implementation is {@link GroqTranscriptionService}
 * (free-tier OpenAI-compatible Whisper). Swap providers via configuration without
 * touching call sites.
 */
public interface TranscriptionService {

    /**
     * @param audioFile     local path to an .m4a or .mp3 file
     * @param languageCode  ISO-639-1 ({@code "ko"}, {@code "en"}); null for auto-detect
     * @return populated transcript
     * @throws TranscriptionException on provider error or transport failure
     */
    Transcript transcribe(Path audioFile, String languageCode) throws TranscriptionException;
}

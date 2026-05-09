package com.msc.church.meeting.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Fallback bean used when no real transcription provider is configured. Throws on
 * every call so the orchestrator can record a clear "not configured" error against
 * the {@link com.msc.church.meeting.MeetingProcessingJob}.
 */
@Service
@ConditionalOnMissingBean(value = TranscriptionService.class, ignored = DisabledTranscriptionService.class)
public class DisabledTranscriptionService implements TranscriptionService {

    @Override
    public Transcript transcribe(Path audioFile, String languageCode) {
        throw new TranscriptionException(
                "Transcription provider is not configured. Set msc.ai.groq.api-key (GROQ_API_KEY) "
                + "or wire a different TranscriptionService bean to enable AI meeting processing.");
    }
}

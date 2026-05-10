package com.msc.church.meeting.ai;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Fallback bean used when no real transcription provider is configured. Throws on
 * every call so the orchestrator can record a clear "not configured" error against
 * the {@link com.msc.church.meeting.MeetingProcessingJob}.
 *
 * <p>{@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}
 * doesn't reliably match against another component-scanned {@code @Service} (it's
 * really meant for {@code @Bean} methods inside {@code @Configuration} classes), so
 * this conditional explicitly mirrors {@link GroqApiKeyPresentCondition}: register
 * the disabled fallback iff the Groq key is blank.
 */
@Service
@Conditional(DisabledTranscriptionService.OnGroqApiKeyAbsent.class)
public class DisabledTranscriptionService implements TranscriptionService {

    static class OnGroqApiKeyAbsent implements ConfigurationCondition {
        @Override public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String key = context.getEnvironment().getProperty("msc.ai.groq.api-key", "");
            return key == null || key.isBlank();
        }
    }

    @Override
    public Transcript transcribe(Path audioFile, String languageCode) {
        throw new TranscriptionException(
                "Transcription provider is not configured. Set msc.ai.groq.api-key (GROQ_API_KEY) "
                + "or wire a different TranscriptionService bean to enable AI meeting processing.");
    }
}

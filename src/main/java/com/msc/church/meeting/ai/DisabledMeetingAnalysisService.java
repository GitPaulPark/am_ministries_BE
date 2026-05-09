package com.msc.church.meeting.ai;

import com.anthropic.client.AnthropicClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Fallback when no {@link AnthropicClient} bean is registered (i.e.
 * {@code msc.ai.anthropic.api-key} is unset). Throws on every call so the
 * orchestrator records a clear "not configured" message on the
 * {@link com.msc.church.meeting.MeetingProcessingJob}.
 */
@Service
@ConditionalOnMissingBean(AnthropicClient.class)
public class DisabledMeetingAnalysisService implements MeetingAnalysisService {

    @Override
    public MeetingAnalysisResult analyze(MeetingAnalysisInput input) {
        throw new MeetingAnalysisException(
                "Anthropic client is not configured. Set msc.ai.anthropic.api-key (ANTHROPIC_API_KEY) "
                + "to enable AI meeting analysis.");
    }
}

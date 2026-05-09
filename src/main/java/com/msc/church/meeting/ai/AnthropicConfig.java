package com.msc.church.meeting.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Anthropic SDK client. Created once at startup (per the SDK's "don't create more than
 * one client" guidance — it owns connection + thread pools). Only registered when
 * {@code msc.ai.anthropic.api-key} resolves to a non-empty value — necessary because
 * the placeholder {@code ${ANTHROPIC_API_KEY:}} resolves to an empty string when the
 * env var is unset, which would still satisfy a plain {@code @ConditionalOnProperty}.
 */
@Configuration
@Conditional(AnthropicConfig.OnNonBlankApiKey.class)
public class AnthropicConfig {

    static class OnNonBlankApiKey implements ConfigurationCondition {
        @Override public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String v = context.getEnvironment().getProperty("msc.ai.anthropic.api-key", "");
            return v != null && !v.isBlank();
        }
    }

    @Bean
    public AnthropicClient anthropicClient(AiProperties props) {
        // The SDK reads ANTHROPIC_API_KEY from env via fromEnv(); if the user binds the
        // property via msc.ai.anthropic.api-key (config-file or other env name) we pass it
        // explicitly. Either path works for the same key.
        return AnthropicOkHttpClient.builder()
                .apiKey(props.getAnthropic().getApiKey())
                .build();
    }
}

package com.msc.church.meeting.ai;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when {@code msc.ai.groq.api-key} resolves to a non-empty value.
 *
 * <p>Plain {@code @ConditionalOnProperty(name = "msc.ai.groq.api-key")} would also
 * match the empty string the env-var placeholder ({@code ${GROQ_API_KEY:}}) yields
 * when the variable is unset, leading to a bean configured with a useless key.
 */
class GroqApiKeyPresentCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("msc.ai.groq.api-key", "");
        return key != null && !key.isBlank();
    }
}

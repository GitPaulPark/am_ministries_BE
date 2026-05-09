package com.msc.church.meeting.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * AI feature configuration. Bind via {@code msc.ai.*} in {@code application.yml}.
 *
 * <pre>
 * msc:
 *   ai:
 *     enabled: true                       # master toggle (default: false)
 *     uploads-dir: ./uploads              # where audio files land
 *     max-audio-mb: 200                   # rejects oversized uploads
 *     prior-meetings-context: 8           # weeks of history fed to Claude
 *     groq:
 *       api-key: ${GROQ_API_KEY:}
 *       base-url: https://api.groq.com/openai/v1
 *       model: whisper-large-v3
 *       timeout-seconds: 600
 *     anthropic:
 *       api-key: ${ANTHROPIC_API_KEY:}
 *       model: claude-opus-4-7
 *       max-tokens: 16000
 * </pre>
 */
@ConfigurationProperties(prefix = "msc.ai")
public class AiProperties {

    /** Master toggle for the AI meeting pipeline. */
    private boolean enabled = false;

    /** Disk location for uploaded audio (a sibling of sermons/, members/ etc.). */
    private String uploadsDir = "./uploads";

    /** Max audio size in megabytes; oversized uploads are rejected. */
    private int maxAudioMb = 200;

    /** Number of prior meeting summaries (per committee, newest first) fed to Claude. */
    private int priorMeetingsContext = 8;

    /** Default audio language passed to the transcription provider (ISO-639-1). */
    private String defaultLanguage = "ko";

    @NestedConfigurationProperty
    private Groq groq = new Groq();

    @NestedConfigurationProperty
    private Anthropic anthropic = new Anthropic();

    public static class Groq {
        private String apiKey = "";
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String model = "whisper-large-v3";
        private int timeoutSeconds = 600;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Anthropic {
        private String apiKey = "";
        private String model = "claude-opus-4-7";
        private int maxTokens = 16000;
        private String effort = "high";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public String getEffort() { return effort; }
        public void setEffort(String effort) { this.effort = effort; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUploadsDir() { return uploadsDir; }
    public void setUploadsDir(String uploadsDir) { this.uploadsDir = uploadsDir; }
    public int getMaxAudioMb() { return maxAudioMb; }
    public void setMaxAudioMb(int maxAudioMb) { this.maxAudioMb = maxAudioMb; }
    public int getPriorMeetingsContext() { return priorMeetingsContext; }
    public void setPriorMeetingsContext(int priorMeetingsContext) {
        this.priorMeetingsContext = priorMeetingsContext;
    }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public Groq getGroq() { return groq; }
    public void setGroq(Groq groq) { this.groq = groq; }
    public Anthropic getAnthropic() { return anthropic; }
    public void setAnthropic(Anthropic anthropic) { this.anthropic = anthropic; }
}

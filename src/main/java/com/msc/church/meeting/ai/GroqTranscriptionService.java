package com.msc.church.meeting.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Speech-to-text via Groq's free-tier OpenAI-compatible Whisper endpoint.
 *
 * <p>Bean is only instantiated when {@code msc.ai.groq.api-key} is set, so a missing
 * key cleanly disables the AI feature rather than crashing at startup. The
 * orchestrator falls back to {@link DisabledTranscriptionService} in that case.
 */
@Slf4j
@Service
@Conditional(GroqApiKeyPresentCondition.class)
public class GroqTranscriptionService implements TranscriptionService {

    private static final String ENDPOINT = "/audio/transcriptions";

    private final AiProperties props;
    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();

    public GroqTranscriptionService(AiProperties props) {
        this.props = props;
        // Groq returns the full transcript only after the audio finishes processing,
        // so the read timeout has to be generous. JDK HttpClient lets us scope this
        // per-request without polluting the global RestClient defaults.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.client = RestClient.builder()
                .baseUrl(props.getGroq().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getGroq().getApiKey())
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(http))
                .build();
    }

    @Override
    public Transcript transcribe(Path audioFile, String languageCode) {
        if (!Files.exists(audioFile)) {
            throw new TranscriptionException("Audio file not found: " + audioFile);
        }

        long start = System.currentTimeMillis();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(audioFile.toFile()));
        form.add("model", props.getGroq().getModel());
        form.add("response_format", "verbose_json");
        form.add("temperature", "0");
        if (languageCode != null && !languageCode.isBlank()) {
            form.add("language", languageCode);
        }

        try {
            String body = client.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = json.readTree(body);
            String text = root.path("text").asText();
            Integer duration = root.has("duration") ? (int) Math.round(root.get("duration").asDouble()) : null;
            int wallSec = (int) ((System.currentTimeMillis() - start) / 1000);

            log.info("Groq transcription complete: file={} duration={}s wall={}s textLen={}",
                    audioFile.getFileName(), duration, wallSec, text.length());

            return new Transcript(text, duration, "groq", props.getGroq().getModel(), wallSec);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.warn("Groq transcription HTTP error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TranscriptionException("Groq returned " + e.getStatusCode() + ": "
                    + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TranscriptionException("Groq transcription failed: " + e.getMessage(), e);
        }
    }
}

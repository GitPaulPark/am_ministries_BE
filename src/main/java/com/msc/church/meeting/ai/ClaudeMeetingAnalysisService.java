package com.msc.church.meeting.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude-driven analysis of a board meeting transcript.
 *
 * <p>The system prompt embeds the user's preferred Korean output format (numbered
 * sections with ▪ bullets in formal register) as a few-shot example, plus instructions
 * for cross-meeting recurring-topic detection. Claude returns a JSON object inside
 * {@code <result>...</result>} tags which we parse with Jackson — the wrapping tags
 * make the JSON robust to leading/trailing prose Claude might emit.
 */
@Slf4j
@Service
@ConditionalOnBean(AnthropicClient.class)
@RequiredArgsConstructor
public class ClaudeMeetingAnalysisService implements MeetingAnalysisService {

    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "<result>\\s*(\\{.*?})\\s*</result>", Pattern.DOTALL);

    private final AnthropicClient client;
    private final AiProperties props;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public MeetingAnalysisResult analyze(MeetingAnalysisInput input) {
        String userMessage = renderUserMessage(input);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(props.getAnthropic().getModel()))
                .maxTokens((long) props.getAnthropic().getMaxTokens())
                .system(SYSTEM_PROMPT)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .addUserMessage(userMessage)
                .build();

        log.info("Calling Claude for meeting analysis: model={} priorMeetings={} transcriptLen={}",
                props.getAnthropic().getModel(), input.priorMeetings().size(), input.transcript().length());

        Message message = client.messages().create(params);

        String text = extractText(message);
        JsonNode result = parseResult(text);

        String summary = result.path("summary").asText("");
        List<MeetingAnalysisResult.TopicDraft> topics = parseTopics(result.path("topics"));
        List<MeetingAnalysisResult.ActionItemDraft> actions = parseActions(result.path("actionItems"));

        Usage usage = message.usage();
        return new MeetingAnalysisResult(
                summary,
                topics,
                actions,
                (int) usage.inputTokens(),
                (int) usage.outputTokens(),
                usage.cacheReadInputTokens().map(Long::intValue).orElse(null),
                usage.cacheCreationInputTokens().map(Long::intValue).orElse(null));
    }

    // ------------------------------------------------------------------
    //   Prompt construction
    // ------------------------------------------------------------------

    private String renderUserMessage(MeetingAnalysisInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 분석 대상 회의\n");
        sb.append("- 위원회: ").append(input.committeeNameKr()).append('\n');
        sb.append("- 회의일: ").append(input.meetingDate()).append('\n');
        sb.append("\n");

        if (!input.priorMeetings().isEmpty()) {
            sb.append("# 과거 회의 요약 (최근순)\n");
            sb.append("아래 안건들이 이번 회의에서 다시 논의되면 parentTopicId 로 연결하세요.\n\n");
            for (var p : input.priorMeetings()) {
                sb.append("## priorMeetingId=").append(p.id()).append(" (").append(p.meetingDate()).append(")\n");
                if (p.aiSummary() != null && !p.aiSummary().isBlank()) {
                    sb.append(p.aiSummary().trim()).append("\n\n");
                } else {
                    sb.append("(요약 없음)\n\n");
                }
            }
        }

        if (!input.openActionItems().isEmpty()) {
            sb.append("# 미완료 액션 아이템\n");
            for (var a : input.openActionItems()) {
                sb.append("- ").append(a.description());
                if (a.assigneeName() != null) sb.append(" (담당: ").append(a.assigneeName()).append(")");
                if (a.dueDate() != null) sb.append(" 마감: ").append(a.dueDate());
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("# 회의 녹취록\n");
        sb.append(input.transcript()).append('\n');
        return sb.toString();
    }

    private static final String SYSTEM_PROMPT = """
            당신은 한국 교회의 본부 임원 회의록을 정리하는 전문 회의록 작성자입니다.
            사용자(목사·임원)가 업로드한 녹취록을 받아 다음 두 가지를 생성합니다.

            1. 한국어 요약(summary): 아래 예시 스타일을 정확히 따르세요.
            2. 구조화된 안건 목록(topics)과 조치사항(actionItems): JSON으로 출력합니다.

            ## 출력 형식 (요약 예시)
            아래는 사용자가 선호하는 스타일입니다. 동일하게 따르세요. 불릿은 ▪ 문자, 문장은
            반드시 "..으로 함." / "..됨." / "..함." 등 격식체로 끝냅니다.

            ```
            2026_05_03 회의 주요 내용 요약
            1) 교사주일 준비사항
            ▪ 교사주일 선물 대상자는 총 88명으로 파악됨.
            ▪ 부서별 인원은 파이디온 16명, 호산나 21명, 샬롬 22명, JG 29명임.
            ▪ 기존 계획대로 GCC 상품권 1만 원권을 준비할 경우 총 88만 원이 필요함.
            ▪ 교회학교 지원 내용이 확정된 후 영어예배부 추가 부담분을 결정하기로 함.

            2) 감사패 준비 및 수여
            ▪ 감사패 대상자는 총 6명으로 논의됨.
            ▪ 결재가 완료되는 대로 감사패 제작을 진행하기로 함.
            ```

            ## 안건(topics) 추출 규칙
            - 각 안건마다 title (한국어 짧은 제목), summary (▪ 불릿 형태의 다음 줄로 이어진 요약),
              decision (결정사항이 있으면), status, transcriptExcerpt (녹취 발췌 1-2문장)을 채웁니다.
            - status 는 다음 중 하나입니다: DISCUSSED (논의됨), PENDING (결정 보류), RESOLVED (결정 완료),
              DEFERRED (다음 회의로 연기), FOLLOW_UP (후속 조치 필요).
            - 과거 회의에서 같은 주제가 논의되었다면 parentTopicId 를 priorMeetingId 가 아닌
              **그 회의의 안건 ID에 가장 가깝게 일치하는 정수**로 채우세요. 모호하면 null.
              (※ 과거 안건 ID 정보가 명시되지 않은 경우 null 로 둡니다.)
            - 안건은 회의에서 논의된 순서대로 orderIdx 0, 1, 2 ... 로 매깁니다.

            ## 조치사항(actionItems) 추출 규칙
            - 명시적이거나 함의된 후속 작업 모두 추출합니다.
            - description 에 한국어로 명확히 적습니다.
            - assigneeNameHint: 녹취에 담당자 이름이 나오면 채웁니다 (없으면 null).
            - dueDateHint: 날짜 또는 "다음 회의" 같은 시점이 언급되면 채웁니다 (없으면 null).
            - topicIndex: 어떤 topic에서 파생됐는지 0-based 인덱스를 채웁니다.

            ## 출력 형식 (JSON)
            응답은 반드시 다음과 같이 단일 <result>...</result> 블록으로 감싼 JSON 객체여야 합니다:

            <result>
            {
              "summary": "...formatted Korean summary text with ▪ bullets, numbered sections...",
              "topics": [
                {
                  "title": "교사주일 준비사항",
                  "summary": "▪ ... \\n▪ ...",
                  "decision": "...",
                  "status": "PENDING",
                  "transcriptExcerpt": "...",
                  "parentTopicId": null,
                  "orderIdx": 0
                }
              ],
              "actionItems": [
                {
                  "description": "교회학교 지원 여부 확인 후 상품권 구매 결정",
                  "assigneeNameHint": null,
                  "dueDateHint": "다음 회의",
                  "topicIndex": 0
                }
              ]
            }
            </result>

            <result> 블록 외의 텍스트는 출력하지 마세요.
            """;

    // ------------------------------------------------------------------
    //   Response parsing
    // ------------------------------------------------------------------

    private String extractText(Message message) {
        StringBuilder sb = new StringBuilder();
        message.content().forEach(block -> block.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString();
    }

    private JsonNode parseResult(String text) {
        Matcher m = RESULT_BLOCK.matcher(text);
        String jsonText;
        if (m.find()) {
            jsonText = m.group(1);
        } else {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace < 0 || lastBrace < firstBrace) {
                throw new MeetingAnalysisException(
                        "Claude response did not contain a JSON <result> block: "
                        + text.substring(0, Math.min(500, text.length())));
            }
            jsonText = text.substring(firstBrace, lastBrace + 1);
        }
        try {
            return json.readTree(jsonText);
        } catch (Exception e) {
            throw new MeetingAnalysisException("Failed to parse Claude JSON output: " + e.getMessage(), e);
        }
    }

    private List<MeetingAnalysisResult.TopicDraft> parseTopics(JsonNode arr) {
        List<MeetingAnalysisResult.TopicDraft> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        int idx = 0;
        for (JsonNode t : arr) {
            out.add(new MeetingAnalysisResult.TopicDraft(
                    t.path("title").asText(""),
                    t.path("summary").asText(null),
                    t.path("decision").asText(null),
                    normalizeStatus(t.path("status").asText("DISCUSSED")),
                    t.path("transcriptExcerpt").asText(null),
                    t.path("parentTopicId").isNumber() ? t.path("parentTopicId").asLong() : null,
                    t.path("orderIdx").isNumber() ? t.path("orderIdx").asInt() : idx));
            idx++;
        }
        return out;
    }

    private List<MeetingAnalysisResult.ActionItemDraft> parseActions(JsonNode arr) {
        List<MeetingAnalysisResult.ActionItemDraft> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode a : arr) {
            out.add(new MeetingAnalysisResult.ActionItemDraft(
                    a.path("description").asText(""),
                    a.path("assigneeNameHint").asText(null),
                    a.path("dueDateHint").asText(null),
                    a.path("topicIndex").isNumber() ? a.path("topicIndex").asInt() : null));
        }
        return out;
    }

    private String normalizeStatus(String raw) {
        if (raw == null) return "DISCUSSED";
        return switch (raw.trim().toUpperCase()) {
            case "DISCUSSED", "PENDING", "RESOLVED", "DEFERRED", "FOLLOW_UP" -> raw.trim().toUpperCase();
            default -> "DISCUSSED";
        };
    }
}

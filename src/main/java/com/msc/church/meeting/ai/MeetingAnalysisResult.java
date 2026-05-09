package com.msc.church.meeting.ai;

import java.util.List;

/**
 * Output of one Claude run for a meeting. The orchestrator persists {@code summary}
 * to {@link com.msc.church.meeting.Meeting#getAiSummary()}, then materializes
 * {@code topics} and {@code actionItems} as rows linked to the meeting.
 *
 * @param summary           formatted ▪-bullet Korean rollup matching the user's preferred style
 * @param topics            ordered topics; cross-meeting links via {@code parentTopicId}
 * @param actionItems       AI-extracted action items
 * @param inputTokens       usage telemetry from Claude
 * @param outputTokens      usage telemetry
 * @param cacheReadTokens   prompt-cache reads (when caching is on)
 * @param cacheWriteTokens  prompt-cache writes
 */
public record MeetingAnalysisResult(
        String summary,
        List<TopicDraft> topics,
        List<ActionItemDraft> actionItems,
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens,
        Integer cacheWriteTokens) {

    /**
     * @param parentTopicId  null unless the topic is a continuation of one from a prior
     *                       meeting; matches an id from {@link MeetingAnalysisInput#priorMeetings()}
     */
    public record TopicDraft(
            String title,
            String summary,
            String decision,
            String status,
            String transcriptExcerpt,
            Long parentTopicId,
            Integer orderIdx) {}

    public record ActionItemDraft(
            String description,
            String assigneeNameHint,
            String dueDateHint,
            Integer topicIndex) {}
}

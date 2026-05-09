package com.msc.church.meeting.ai;

/**
 * Pluggable LLM analysis of a meeting transcript. Default impl is
 * {@link ClaudeMeetingAnalysisService} (Anthropic). When no provider is wired,
 * {@link DisabledMeetingAnalysisService} throws a configuration-error message
 * the orchestrator records on the {@link com.msc.church.meeting.MeetingProcessingJob}.
 */
public interface MeetingAnalysisService {

    MeetingAnalysisResult analyze(MeetingAnalysisInput input);
}

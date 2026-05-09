package com.msc.church.meeting.ai;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything {@link MeetingAnalysisService} needs to produce the structured
 * topics + action items for one meeting.
 *
 * @param meetingDate        the date of the meeting being analyzed
 * @param committeeNameKr    Korean name of the committee
 * @param transcript         full Whisper transcript
 * @param priorMeetings      prior meetings in the same committee (newest first), each
 *                           carrying its date + AI summary so Claude can flag recurring
 *                           topics by date
 * @param openActionItems    still-open action items from prior meetings
 */
public record MeetingAnalysisInput(
        LocalDate meetingDate,
        String committeeNameKr,
        String transcript,
        List<PriorMeeting> priorMeetings,
        List<OpenActionItem> openActionItems) {

    public record PriorMeeting(Long id, LocalDate meetingDate, String aiSummary) {}

    public record OpenActionItem(Long id, String description, String assigneeName, LocalDate dueDate) {}
}

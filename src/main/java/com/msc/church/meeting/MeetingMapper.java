package com.msc.church.meeting;

import com.msc.church.cell.Cell;
import com.msc.church.meeting.dto.ActionItemResponse;
import com.msc.church.meeting.dto.CommitteeDetail;
import com.msc.church.meeting.dto.CommitteeMembershipResponse;
import com.msc.church.meeting.dto.CommitteeRef;
import com.msc.church.meeting.dto.CommitteeSummary;
import com.msc.church.meeting.dto.MeetingAttendeeResponse;
import com.msc.church.meeting.dto.MeetingDetail;
import com.msc.church.meeting.dto.MeetingSummary;
import com.msc.church.meeting.dto.MeetingTopicResponse;
import com.msc.church.meeting.dto.MemberRef;
import com.msc.church.member.Member;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written mapper. Matches the pattern from {@link com.msc.church.bulletin.BulletinMapper}
 * for two-arg toSummary (where memberCount comes from a separate query).
 */
@Component
public class MeetingMapper {

    // ---------- Committee ----------

    public CommitteeSummary toCommitteeSummary(Committee c, long memberCount) {
        if (c == null) return null;
        return new CommitteeSummary(
                c.getId(), c.getCode(), c.getNameKr(), c.getNameEn(),
                memberCount, c.isActive());
    }

    public CommitteeDetail toCommitteeDetail(Committee c, List<CommitteeMembershipResponse> members) {
        if (c == null) return null;
        return new CommitteeDetail(
                c.getId(), c.getCode(), c.getNameKr(), c.getNameEn(),
                c.getDescription(), c.isActive(),
                members == null ? List.of() : members);
    }

    public CommitteeMembershipResponse toCommitteeMembership(CommitteeMembership cm) {
        if (cm == null) return null;
        Member m = cm.getMember();
        return new CommitteeMembershipResponse(
                cm.getId(), cm.getCommittee().getId(),
                m == null ? null : m.getId(),
                m == null ? null : m.getNameKr(),
                m == null ? null : m.getNameEn(),
                cm.getRole(), cm.getJoinedAt(), cm.isActive());
    }

    public CommitteeRef toCommitteeRef(Committee c) {
        if (c == null) return null;
        return new CommitteeRef(c.getId(), c.getCode(), c.getNameKr(), c.getNameEn());
    }

    // ---------- Meeting ----------

    public MeetingSummary toMeetingSummary(Meeting m, int actionItemCount, int openActionItemCount) {
        if (m == null) return null;
        return new MeetingSummary(
                m.getId(), toCommitteeRef(m.getCommittee()),
                m.getMeetingDate(), m.getTitle(),
                memberRef(m.getPresider()),
                m.getStatus(),
                actionItemCount, openActionItemCount);
    }

    public MeetingDetail toMeetingDetail(Meeting m) {
        if (m == null) return null;
        List<MeetingAttendeeResponse> atts = m.getAttendees().stream()
                .map(a -> new MeetingAttendeeResponse(
                        a.getId(),
                        a.getMember() == null ? null : a.getMember().getId(),
                        a.getMember() == null ? null : a.getMember().getNameKr(),
                        a.getMember() == null ? null : a.getMember().getNameEn(),
                        a.isAttended()))
                .toList();
        List<ActionItemResponse> items = m.getActionItems().stream()
                .map(a -> toActionItemResponse(a, m))
                .toList();
        List<MeetingTopicResponse> topics = m.getTopics().stream()
                .map(this::toTopicResponse)
                .toList();
        return new MeetingDetail(
                m.getId(), toCommitteeRef(m.getCommittee()),
                m.getMeetingDate(), m.getTitle(),
                memberRef(m.getPresider()),
                m.getAgenda(), m.getMinutes(),
                m.getStatus(), m.getPublishedAt(),
                atts, items,
                topics,
                m.getAiSummary(),
                m.getAudioUrl(),
                m.getAudioDurationSec(),
                m.getAudioSizeBytes(),
                m.getProcessingStatus(),
                m.getProcessingError(),
                m.getProcessedAt());
    }

    public MeetingTopicResponse toTopicResponse(MeetingTopic t) {
        if (t == null) return null;
        MeetingTopic parent = t.getParentTopic();
        Meeting parentMeeting = parent == null ? null : parent.getMeeting();
        return new MeetingTopicResponse(
                t.getId(),
                t.getMeeting() == null ? null : t.getMeeting().getId(),
                parent == null ? null : parent.getId(),
                parentMeeting == null ? null : parentMeeting.getId(),
                parentMeeting == null ? null : parentMeeting.getMeetingDate(),
                t.getTitle(),
                t.getSummary(),
                t.getDecision(),
                t.getStatus(),
                t.getComment(),
                t.getTranscriptExcerpt(),
                t.getOrderIdx(),
                t.isAiGenerated());
    }

    public ActionItemResponse toActionItemResponse(ActionItem a, Meeting m) {
        if (a == null) return null;
        Meeting use = m != null ? m : a.getMeeting();
        return new ActionItemResponse(
                a.getId(),
                use == null ? null : use.getId(),
                use == null ? null : use.getMeetingDate(),
                use == null ? null : toCommitteeRef(use.getCommittee()),
                a.getDescription(),
                memberRef(a.getAssignee()),
                a.getDueDate(),
                a.getStatus(),
                a.getCompletedAt(),
                a.getNotes(),
                a.getOrderIdx());
    }

    public MemberRef memberRef(Member m) {
        if (m == null) return null;
        return new MemberRef(m.getId(), m.getNameKr(), m.getNameEn());
    }
}

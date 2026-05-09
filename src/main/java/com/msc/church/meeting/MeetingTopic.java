package com.msc.church.meeting;

import com.msc.church.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A first-class agenda/discussion item on a meeting.
 *
 * <p>When AI processing populates a meeting, topics are the structured
 * representation of the agenda — each one editable independently with its own
 * status and free-form pastor comment. {@code parentTopic} self-references a
 * topic from a prior meeting to track recurring discussions ("이 안건은 5/3
 * 회의에서도 논의됨").
 */
@Entity
@Table(name = "meeting_topics")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingTopic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    /** Cross-meeting recurring-topic link; null for one-off topics. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_topic_id")
    private MeetingTopic parentTopic;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "decision", columnDefinition = "LONGTEXT")
    private String decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingTopicStatus status;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "transcript_excerpt", columnDefinition = "TEXT")
    private String transcriptExcerpt;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;
}

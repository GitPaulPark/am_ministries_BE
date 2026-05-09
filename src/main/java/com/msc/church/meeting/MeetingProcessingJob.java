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

import java.time.LocalDateTime;

/**
 * Audit row per AI processing attempt for a meeting. One row is created when
 * audio is uploaded; the same row is updated as the pipeline advances. Failed
 * runs leave the row in {@code FAILED} with an {@code errorMessage}; a retry
 * inserts a fresh row.
 */
@Entity
@Table(name = "meeting_processing_jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingProcessingJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingProcessingStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "audio_seconds")
    private Integer audioSeconds;

    @Column(name = "transcription_provider", length = 40)
    private String transcriptionProvider;

    @Column(name = "transcription_model", length = 80)
    private String transcriptionModel;

    @Column(name = "transcription_seconds")
    private Integer transcriptionSeconds;

    @Column(name = "analysis_provider", length = 40)
    private String analysisProvider;

    @Column(name = "analysis_model", length = 80)
    private String analysisModel;

    @Column(name = "analysis_input_tokens")
    private Integer analysisInputTokens;

    @Column(name = "analysis_output_tokens")
    private Integer analysisOutputTokens;

    @Column(name = "analysis_cache_read_tokens")
    private Integer analysisCacheReadTokens;

    @Column(name = "analysis_cache_write_tokens")
    private Integer analysisCacheWriteTokens;

    @Column(name = "triggered_by_user_id")
    private Long triggeredByUserId;
}

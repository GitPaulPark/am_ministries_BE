package com.msc.church.sermon;

import com.msc.church.bulletin.Bulletin;
import com.msc.church.common.BaseEntity;
import com.msc.church.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sermon archive entry. Transcripts are LONGTEXT (~ unlimited); reflection questions
 * are stored as a JSON string and parsed at the service layer (V1 simplicity per spec).
 */
@Entity
@Table(name = "sermons")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Sermon extends BaseEntity {

    @Column(name = "sermon_date", nullable = false, unique = true)
    private LocalDate sermonDate;

    @Column(name = "title_kr", length = 255)
    private String titleKr;

    @Column(name = "title_en", length = 255)
    private String titleEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preacher_member_id")
    private Member preacher;

    @Column(name = "preacher_name_label", length = 100)
    private String preacherNameLabel;

    @Column(name = "scripture_ref", nullable = false, length = 100)
    private String scriptureRef;

    @Column(name = "scripture_text_kr", columnDefinition = "TEXT")
    private String scriptureTextKr;

    @Column(name = "scripture_text_en", columnDefinition = "TEXT")
    private String scriptureTextEn;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "transcript_pdf_url", length = 500)
    private String transcriptPdfUrl;

    /**
     * Null until a PDF has been parsed; true if the parser detected a single
     * language (so the reader hides the language toggle); false when both KR
     * and EN paragraphs are present.
     */
    @Column(name = "transcript_single_lang")
    private Boolean transcriptSingleLang;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "transcript_kr", columnDefinition = "LONGTEXT")
    private String transcriptKr;

    @Column(name = "transcript_en", columnDefinition = "LONGTEXT")
    private String transcriptEn;

    @Column(name = "theme", length = 255)
    private String theme;

    /** JSON array of strings, e.g. {@code ["Q1?","Q2?"]}. Parsed at service layer. */
    @Column(name = "cell_reflection_questions", columnDefinition = "JSON")
    private String cellReflectionQuestionsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulletin_id")
    private Bulletin linkedBulletin;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}

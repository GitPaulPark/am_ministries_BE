package com.msc.church.sermon;

import com.msc.church.common.BaseEntity;
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

/**
 * One paragraph of a parsed bilingual sermon transcript. See V4 migration for
 * the column comments. The reader collapses adjacent rows that share a
 * {@code pairKey} into a single block in "show both" mode.
 */
@Entity
@Table(name = "sermon_paragraphs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SermonParagraph extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sermon_id", nullable = false)
    private Sermon sermon;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "section_idx")
    private Integer sectionIdx;

    @Column(name = "section_title_kr", length = 500)
    private String sectionTitleKr;

    @Column(name = "section_title_en", length = 500)
    private String sectionTitleEn;

    /** paragraph | scripture | section_heading | preacher_meta */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** kr | en */
    @Column(name = "language", nullable = false, length = 2)
    private String language;

    @Column(name = "text", columnDefinition = "LONGTEXT", nullable = false)
    private String text;

    @Column(name = "scripture_ref", length = 120)
    private String scriptureRef;

    @Column(name = "pair_key", length = 40)
    private String pairKey;
}

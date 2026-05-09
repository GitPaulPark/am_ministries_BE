package com.msc.church.bulletin;

import com.msc.church.common.BaseEntity;
import com.msc.church.member.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekly bulletin — the parent of four order-preserving child collections. Children
 * are owned (cascade ALL + orphanRemoval) so the V1 update strategy of
 * "delete-and-reinsert from the request payload" works just by replacing the
 * collection contents.
 */
@Entity
@Table(name = "bulletins")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Bulletin extends BaseEntity {

    @Column(name = "service_date", nullable = false, unique = true)
    private LocalDate serviceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presider_member_id")
    private Member presider;

    @Column(name = "theme", length = 255)
    private String theme;

    @Column(name = "song_of_week_title", length = 255)
    private String songOfWeekTitle;

    @Column(name = "song_of_week_lyrics", columnDefinition = "TEXT")
    private String songOfWeekLyrics;

    @Column(name = "memory_verse_ref", length = 50)
    private String memoryVerseRef;

    @Column(name = "memory_verse_text_kr", columnDefinition = "TEXT")
    private String memoryVerseTextKr;

    @Column(name = "memory_verse_text_en", columnDefinition = "TEXT")
    private String memoryVerseTextEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_week_prayer_member_id")
    private Member nextWeekPrayer;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<BulletinLiturgyRole> liturgyRoles = new ArrayList<>();

    @OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<BulletinAnnouncement> announcements = new ArrayList<>();

    @OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("category ASC, orderIdx ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<BulletinPrayerItem> prayerItems = new ArrayList<>();

    @OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<BulletinCellAttendance> cellAttendance = new ArrayList<>();

    /**
     * Replaces the liturgy roles collection in-place so the orphanRemoval +
     * mappedBy wiring deletes everything not in {@code newRoles}. Each new row gets
     * its back-reference set to {@code this}.
     */
    public void replaceLiturgyRoles(List<BulletinLiturgyRole> newRoles) {
        this.liturgyRoles.clear();
        if (newRoles != null) {
            for (BulletinLiturgyRole r : newRoles) {
                r.setBulletin(this);
                this.liturgyRoles.add(r);
            }
        }
    }

    public void replaceAnnouncements(List<BulletinAnnouncement> newAnnouncements) {
        this.announcements.clear();
        if (newAnnouncements != null) {
            for (BulletinAnnouncement a : newAnnouncements) {
                a.setBulletin(this);
                this.announcements.add(a);
            }
        }
    }

    public void replacePrayerItems(List<BulletinPrayerItem> newItems) {
        this.prayerItems.clear();
        if (newItems != null) {
            for (BulletinPrayerItem p : newItems) {
                p.setBulletin(this);
                this.prayerItems.add(p);
            }
        }
    }

    public void replaceCellAttendance(List<BulletinCellAttendance> newRows) {
        this.cellAttendance.clear();
        if (newRows != null) {
            for (BulletinCellAttendance c : newRows) {
                c.setBulletin(this);
                this.cellAttendance.add(c);
            }
        }
    }
}

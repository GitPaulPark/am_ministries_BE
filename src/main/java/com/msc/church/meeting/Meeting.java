package com.msc.church.meeting;

import com.msc.church.common.BaseEntity;
import com.msc.church.member.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Meeting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    private Committee committee;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presider_member_id")
    private Member presider;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "agenda", columnDefinition = "LONGTEXT")
    private String agenda;

    @Column(name = "minutes", columnDefinition = "LONGTEXT")
    private String minutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<MeetingAttendee> attendees = new ArrayList<>();

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ActionItem> actionItems = new ArrayList<>();

    public void replaceAttendees(List<MeetingAttendee> next) {
        this.attendees.clear();
        if (next != null) {
            for (MeetingAttendee a : next) { a.setMeeting(this); this.attendees.add(a); }
        }
    }
}

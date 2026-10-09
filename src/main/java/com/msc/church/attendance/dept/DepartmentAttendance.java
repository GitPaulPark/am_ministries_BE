package com.msc.church.attendance.dept;

import com.msc.church.common.BaseEntity;
import com.msc.church.meeting.Committee;
import com.msc.church.meeting.Meeting;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One attendance "event" for a committee/department on a date. Multiple events
 * can share a date when distinguished by {@link #eventLabel} (e.g. morning vs
 * evening practice). Locked 30 days after creation; admins can override.
 */
@Entity
@Table(name = "department_attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_da",
                columnNames = {"committee_id", "event_date", "event_label"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DepartmentAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    private Committee committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_label", nullable = false, length = 100)
    @Builder.Default
    private String eventLabel = "";

    @Column(name = "entry_mode", nullable = false, length = 20)
    @Builder.Default
    private String entryMode = "AFTER";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "attendance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DepartmentAttendanceEntry> entries = new ArrayList<>();
}

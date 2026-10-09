package com.msc.church.attendance.sunday;

import com.msc.church.cell.Cell;
import com.msc.church.common.BaseEntity;
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
 * Sunday cell attendance check-in. Mirrors {@code DepartmentAttendance} but
 * scoped by cell. One row per (cell × service_date). Locked 30 days after
 * creation; only ADMIN can edit past the lock.
 */
@Entity
@Table(name = "sunday_attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_sa_cell_date",
                columnNames = {"cell_id", "service_date"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SundayAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cell_id", nullable = false)
    private Cell cell;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

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
    private List<SundayAttendanceEntry> entries = new ArrayList<>();
}

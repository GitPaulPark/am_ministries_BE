package com.msc.church.attendance.sunday;

import com.msc.church.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "sunday_attendance_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SundayAttendanceEntry {

    @EmbeddedId
    private Pk id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("attendanceId")
    @JoinColumn(name = "sunday_attendance_id", nullable = false)
    private SundayAttendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("memberId")
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "note", length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        @Column(name = "sunday_attendance_id")
        private Long attendanceId;
        @Column(name = "member_id")
        private Long memberId;
    }
}

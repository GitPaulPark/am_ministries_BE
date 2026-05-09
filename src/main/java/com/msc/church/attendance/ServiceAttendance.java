package com.msc.church.attendance;

import com.msc.church.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Service-level attendance row. One per (service_date, service_type, age_group);
 * the unique constraint is enforced at the DB. Upserts go through
 * {@link AttendanceService#upsertService}.
 */
@Entity
@Table(name = "service_attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sa", columnNames = {"service_date", "service_type", "age_group"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ServiceAttendance extends BaseEntity {

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    /** Free-form per spec — typical values are "EWS", "KWS", "CHRISTMAS", "EASTER". */
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @Column(name = "age_group", nullable = false, length = 50)
    private String ageGroup;

    @Column(name = "count", nullable = false)
    private Integer count;
}

package com.msc.church.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceAttendanceRepository extends JpaRepository<ServiceAttendance, Long> {

    Optional<ServiceAttendance> findByServiceDateAndServiceTypeAndAgeGroup(
            LocalDate serviceDate, String serviceType, String ageGroup);

    List<ServiceAttendance> findByServiceDateBetweenOrderByServiceDateAsc(
            LocalDate from, LocalDate to);

    List<ServiceAttendance> findByServiceDateBetweenAndServiceTypeOrderByServiceDateAsc(
            LocalDate from, LocalDate to, String serviceType);
}

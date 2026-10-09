package com.msc.church.attendance.sunday;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SundayAttendanceRepository extends JpaRepository<SundayAttendance, Long> {

    @EntityGraph(attributePaths = {"cell", "entries", "entries.member"})
    Optional<SundayAttendance> findWithEntriesById(Long id);

    Optional<SundayAttendance> findByCell_IdAndServiceDate(Long cellId, LocalDate serviceDate);

    List<SundayAttendance> findByCell_IdOrderByServiceDateDesc(Long cellId);
}

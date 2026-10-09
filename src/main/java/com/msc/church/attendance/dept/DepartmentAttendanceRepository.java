package com.msc.church.attendance.dept;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepartmentAttendanceRepository extends JpaRepository<DepartmentAttendance, Long> {

    @EntityGraph(attributePaths = {"committee", "entries", "entries.member"})
    Optional<DepartmentAttendance> findWithEntriesById(Long id);

    Optional<DepartmentAttendance> findByCommittee_IdAndEventDateAndEventLabel(
            Long committeeId, LocalDate eventDate, String eventLabel);

    List<DepartmentAttendance> findByCommittee_IdOrderByEventDateDesc(Long committeeId);
}

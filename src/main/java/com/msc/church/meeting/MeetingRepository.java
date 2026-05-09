package com.msc.church.meeting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    boolean existsByCommittee_IdAndMeetingDate(Long committeeId, LocalDate meetingDate);

    Optional<Meeting> findByCommittee_IdAndMeetingDate(Long committeeId, LocalDate meetingDate);

    /** {@link MeetingService} loads child collections lazily — same MultipleBagFetch
     *  reasoning as the bulletin module. */
    @EntityGraph(attributePaths = {"committee", "presider"})
    Optional<Meeting> findWithRefsById(Long id);

    Page<Meeting> findByCommittee_IdOrderByMeetingDateDesc(Long committeeId, Pageable pageable);

    Page<Meeting> findByCommittee_IdAndStatusOrderByMeetingDateDesc(
            Long committeeId, MeetingStatus status, Pageable pageable);

    /** Prior meetings for cross-meeting recurring-topic context (newest first). */
    Page<Meeting> findByCommittee_IdAndMeetingDateLessThanOrderByMeetingDateDesc(
            Long committeeId, LocalDate meetingDate, Pageable pageable);
}

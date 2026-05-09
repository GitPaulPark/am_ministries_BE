package com.msc.church.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingProcessingJobRepository extends JpaRepository<MeetingProcessingJob, Long> {

    List<MeetingProcessingJob> findByMeeting_IdOrderByCreatedAtDesc(Long meetingId);

    Optional<MeetingProcessingJob> findFirstByMeeting_IdOrderByCreatedAtDesc(Long meetingId);
}

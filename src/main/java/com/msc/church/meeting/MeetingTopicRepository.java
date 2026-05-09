package com.msc.church.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingTopicRepository extends JpaRepository<MeetingTopic, Long> {

    List<MeetingTopic> findByMeeting_IdOrderByOrderIdxAscIdAsc(Long meetingId);
}

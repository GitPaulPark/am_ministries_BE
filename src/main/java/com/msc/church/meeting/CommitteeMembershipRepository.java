package com.msc.church.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeMembershipRepository extends JpaRepository<CommitteeMembership, Long> {

    List<CommitteeMembership> findByCommittee_IdAndActiveTrue(Long committeeId);

    List<CommitteeMembership> findByMember_IdAndActiveTrue(Long memberId);

    Optional<CommitteeMembership> findByCommittee_IdAndMember_Id(Long committeeId, Long memberId);

    boolean existsByCommittee_IdAndMember_IdAndActiveTrue(Long committeeId, Long memberId);
}

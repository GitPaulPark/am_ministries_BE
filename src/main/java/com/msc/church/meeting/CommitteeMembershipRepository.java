package com.msc.church.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeMembershipRepository extends JpaRepository<CommitteeMembership, Long> {

    List<CommitteeMembership> findByCommittee_IdAndActiveTrue(Long committeeId);

    List<CommitteeMembership> findByMember_IdAndActiveTrue(Long memberId);

    Optional<CommitteeMembership> findByCommittee_IdAndMember_Id(Long committeeId, Long memberId);

    boolean existsByCommittee_IdAndMember_IdAndActiveTrue(Long committeeId, Long memberId);

    /** True when the given member belongs to the HQ board (or any committee code, in fact). */
    boolean existsByCommittee_CodeAndMember_IdAndActiveTrue(String committeeCode, Long memberId);

    /** True when the member is an active CHAIR or SECRETARY of the given committee. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT (COUNT(cm) > 0) FROM CommitteeMembership cm
             WHERE cm.committee.id = :committeeId
               AND cm.member.id    = :memberId
               AND cm.active       = true
               AND cm.role        IN (com.msc.church.meeting.CommitteeRole.CHAIR,
                                      com.msc.church.meeting.CommitteeRole.SECRETARY)
            """)
    boolean isCommitteeOfficer(@org.springframework.data.repository.query.Param("committeeId") Long committeeId,
                               @org.springframework.data.repository.query.Param("memberId") Long memberId);
}

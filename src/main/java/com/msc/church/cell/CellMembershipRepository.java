package com.msc.church.cell;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CellMembershipRepository extends JpaRepository<CellMembership, Long> {

    Optional<CellMembership> findByMember_IdAndPrimaryTrue(Long memberId);

    List<CellMembership> findByMember_IdAndActiveTrue(Long memberId);

    @Modifying
    @Query("UPDATE CellMembership cm SET cm.primary = false " +
           "WHERE cm.member.id = :memberId AND cm.primary = true")
    int clearPrimaryFor(@Param("memberId") Long memberId);
}

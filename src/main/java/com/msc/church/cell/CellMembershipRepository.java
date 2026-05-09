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

    /** Roster of a cell (active members only). */
    List<CellMembership> findByCell_IdAndActiveTrue(Long cellId);

    /** Roster including former members (admin views). */
    List<CellMembership> findByCell_Id(Long cellId);

    long countByCell_IdAndActiveTrue(Long cellId);

    boolean existsByMember_IdAndCell_IdAndActiveTrue(Long memberId, Long cellId);

    /**
     * "Are there any *primary* memberships still attached to this cell?". Used to
     * block deactivation of a cell that still owns primary references.
     */
    boolean existsByCell_IdAndPrimaryTrueAndActiveTrue(Long cellId);

    /** Newcomer-graduation scheduler scope: active memberships in a NEWCOMER cell. */
    @Query("SELECT cm FROM CellMembership cm WHERE cm.cell.id = :cellId "
            + "AND cm.active = true AND cm.primary = true")
    List<CellMembership> findActivePrimaryByCellId(@Param("cellId") Long cellId);

    @Modifying
    @Query("UPDATE CellMembership cm SET cm.primary = false " +
           "WHERE cm.member.id = :memberId AND cm.primary = true")
    int clearPrimaryFor(@Param("memberId") Long memberId);
}

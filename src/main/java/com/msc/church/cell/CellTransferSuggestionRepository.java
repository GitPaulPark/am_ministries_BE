package com.msc.church.cell;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CellTransferSuggestionRepository extends JpaRepository<CellTransferSuggestion, Long> {

    List<CellTransferSuggestion> findByStatusOrderBySuggestedAtAsc(TransferSuggestionStatus status);

    @Query("SELECT COUNT(s) FROM CellTransferSuggestion s "
            + "WHERE s.member.id = :memberId AND s.status = com.msc.church.cell.TransferSuggestionStatus.PENDING")
    long countPendingForMember(@Param("memberId") Long memberId);
}

package com.msc.church.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long> {

    /** Open action items for one assignee, sorted by due date (nulls last) then created. */
    @Query("""
            SELECT a FROM ActionItem a
            WHERE a.assignee.id = :memberId
              AND a.status = com.msc.church.meeting.ActionItemStatus.OPEN
            ORDER BY CASE WHEN a.dueDate IS NULL THEN 1 ELSE 0 END, a.dueDate ASC, a.createdAt ASC
            """)
    List<ActionItem> findOpenForAssignee(@Param("memberId") Long memberId);

    long countByAssignee_IdAndStatus(Long memberId, ActionItemStatus status);
}

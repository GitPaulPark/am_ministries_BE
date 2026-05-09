package com.msc.church.cell;

import com.msc.church.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Suggested newcomer transfer. The {@link NewcomerGraduationScheduler} creates rows
 * here when a newcomer crosses the configured weeks-in-Nec threshold; pastors approve
 * (which performs the actual move) or dismiss.
 */
@Entity
@Table(name = "cell_transfer_suggestions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CellTransferSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_cell_id", nullable = false)
    private Cell fromCell;

    @CreationTimestamp
    @Column(name = "suggested_at", nullable = false, updatable = false)
    private LocalDateTime suggestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferSuggestionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_cell_id")
    private Cell toCell;

    @Column(name = "approved_by")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}

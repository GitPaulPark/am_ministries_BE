package com.msc.church.bulletin;

import com.msc.church.cell.Cell;
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

/**
 * One row in the order of service. Exactly one of {@code assigneeMember},
 * {@code assigneeCell}, {@code assigneeLabel} is set — the service layer enforces
 * that constraint (the DB schema permits all three to be null, e.g. a Lord's Prayer
 * row read by everyone).
 */
@Entity
@Table(name = "bulletin_liturgy_roles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BulletinLiturgyRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bulletin_id", nullable = false)
    private Bulletin bulletin;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 50)
    private LiturgyRoleType roleType;

    @Column(name = "title", length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_member_id")
    private Member assigneeMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_cell_id")
    private Cell assigneeCell;

    @Column(name = "assignee_label", length = 255)
    private String assigneeLabel;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "standing", nullable = false)
    private boolean standing;
}

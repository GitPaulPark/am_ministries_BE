package com.msc.church.member;

import com.msc.church.cell.CellMembership;
import com.msc.church.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Person record. Soft-deleted on remove (rows preserved for attendance / prayer / past
 * bulletins). The {@code @SQLRestriction} silently filters deleted rows from every JPA
 * query — {@code email} uniqueness checks therefore already mean "unique among
 * non-deleted", as the module spec requires.
 */
@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE members SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Member extends BaseEntity {

    @Column(name = "name_kr", nullable = false, length = 100)
    private String nameKr;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "role_label", length = 50)
    private String roleLabel;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "gender", columnDefinition = "CHAR(1)")
    private String gender;

    @Column(name = "baptized", nullable = false)
    private boolean baptized;

    @Column(name = "baptized_at")
    private LocalDate baptizedAt;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "preferred_locale", length = 10)
    private String preferredLocale;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @Builder.Default
    private List<CellMembership> memberships = new ArrayList<>();
}

package com.msc.church.cell;

import com.msc.church.common.BaseEntity;
import com.msc.church.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cells")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Cell extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name_kr", nullable = false, length = 100)
    private String nameKr;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private CellType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_member_id")
    private Member leader;

    @Column(name = "meeting_day", length = 20)
    private String meetingDay;

    @Column(name = "meeting_time", length = 20)
    private String meetingTime;

    @Column(name = "meeting_location", length = 255)
    private String meetingLocation;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;
}

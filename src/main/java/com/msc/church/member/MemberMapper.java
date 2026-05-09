package com.msc.church.member;

import com.msc.church.cell.CellMembership;
import com.msc.church.member.dto.CellSummaryRef;
import com.msc.church.member.dto.MemberDetail;
import com.msc.church.member.dto.MemberSummary;
import com.msc.church.member.dto.MembershipSummary;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

/**
 * Entity ↔ DTO conversion. The {@code includeNotes} context flag drives pastor-notes
 * redaction — service code passes {@code true} only for ADMIN / PASTOR callers.
 */
@Mapper(componentModel = "spring")
public interface MemberMapper {

    @org.mapstruct.Mapping(target = "primaryCell",
            expression = "java(toPrimaryCellRef(member))")
    MemberSummary toSummary(Member member);

    @org.mapstruct.Mapping(target = "memberships", source = "memberships")
    @org.mapstruct.Mapping(target = "notes", expression = "java(includeNotes ? member.getNotes() : null)")
    MemberDetail toDetail(Member member, @Context boolean includeNotes);

    @org.mapstruct.Mapping(target = "cellId", source = "cell.id")
    @org.mapstruct.Mapping(target = "cellCode", source = "cell.code")
    @org.mapstruct.Mapping(target = "cellNameKr", source = "cell.nameKr")
    @org.mapstruct.Mapping(target = "cellNameEn", source = "cell.nameEn")
    @org.mapstruct.Mapping(target = "cellType", source = "cell.type")
    @org.mapstruct.Mapping(target = "isPrimary", source = "primary")
    MembershipSummary toMembershipSummary(CellMembership membership);

    @Named("primaryCellRef")
    default CellSummaryRef toPrimaryCellRef(Member member) {
        if (member == null || member.getMemberships() == null) return null;
        for (CellMembership cm : member.getMemberships()) {
            if (cm.isPrimary() && cm.isActive() && cm.getCell() != null) {
                var c = cm.getCell();
                return new CellSummaryRef(c.getId(), c.getCode(), c.getNameKr(), c.getNameEn());
            }
        }
        return null;
    }

    /** Filters memberships → MembershipSummaries; reused by toDetail. */
    default List<MembershipSummary> mapMemberships(List<CellMembership> memberships) {
        if (memberships == null) return List.of();
        return memberships.stream().map(this::toMembershipSummary).toList();
    }
}

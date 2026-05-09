package com.msc.church.cell;

import com.msc.church.cell.dto.CellDetail;
import com.msc.church.cell.dto.CellMemberRef;
import com.msc.church.cell.dto.CellMembershipResponse;
import com.msc.church.cell.dto.CellRosterEntry;
import com.msc.church.cell.dto.CellSummary;
import com.msc.church.member.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CellMapper {

    /** Two-arg builds need help: write the summary by hand instead of fighting MapStruct. */
    default CellSummary toSummary(Cell cell, long memberCount) {
        if (cell == null) return null;
        return new CellSummary(
                cell.getId(),
                cell.getCode(),
                cell.getNameKr(),
                cell.getNameEn(),
                cell.getType(),
                toMemberRef(cell.getLeader()),
                memberCount,
                cell.getMeetingDay(),
                cell.getMeetingTime(),
                cell.getMeetingLocation(),
                cell.isActive()
        );
    }

    default CellDetail toDetail(Cell cell, List<CellRosterEntry> roster) {
        if (cell == null) return null;
        return new CellDetail(
                cell.getId(),
                cell.getCode(),
                cell.getNameKr(),
                cell.getNameEn(),
                cell.getType(),
                toMemberRef(cell.getLeader()),
                cell.getMeetingDay(),
                cell.getMeetingTime(),
                cell.getMeetingLocation(),
                cell.getDescription(),
                cell.isActive(),
                roster == null ? List.of() : roster
        );
    }

    @Mapping(target = "membershipId", source = "id")
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "nameKr", source = "member.nameKr")
    @Mapping(target = "nameEn", source = "member.nameEn")
    @Mapping(target = "isPrimary", source = "primary")
    CellRosterEntry toRosterEntry(CellMembership membership);

    @Mapping(target = "cellId", source = "cell.id")
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberNameKr", source = "member.nameKr")
    @Mapping(target = "memberNameEn", source = "member.nameEn")
    @Mapping(target = "isPrimary", source = "primary")
    CellMembershipResponse toMembershipResponse(CellMembership membership);

    @Named("memberRef")
    default CellMemberRef toMemberRef(Member m) {
        if (m == null) return null;
        return new CellMemberRef(m.getId(), m.getNameKr(), m.getNameEn());
    }
}

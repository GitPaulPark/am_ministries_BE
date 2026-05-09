package com.msc.church.bulletin;

import com.msc.church.bulletin.dto.AnnouncementResponse;
import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.bulletin.dto.BulletinSummary;
import com.msc.church.bulletin.dto.CellAttendanceResponse;
import com.msc.church.bulletin.dto.CellRef;
import com.msc.church.bulletin.dto.LiturgyRoleResponse;
import com.msc.church.bulletin.dto.MemberRef;
import com.msc.church.bulletin.dto.PrayerItemResponse;
import com.msc.church.cell.Cell;
import com.msc.church.member.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BulletinMapper {

    @Mapping(target = "presider", source = "presider", qualifiedByName = "memberRef")
    BulletinSummary toSummary(Bulletin bulletin);

    @Mapping(target = "presider", source = "presider", qualifiedByName = "memberRef")
    @Mapping(target = "nextWeekPrayer", source = "nextWeekPrayer", qualifiedByName = "memberRef")
    @Mapping(target = "liturgyRoles", source = "liturgyRoles")
    @Mapping(target = "announcements", source = "announcements")
    @Mapping(target = "prayerItems", source = "prayerItems")
    @Mapping(target = "cellAttendance", source = "cellAttendance")
    BulletinDetail toDetail(Bulletin bulletin);

    @Mapping(target = "assigneeMember", source = "assigneeMember", qualifiedByName = "memberRef")
    @Mapping(target = "assigneeCell", source = "assigneeCell", qualifiedByName = "cellRef")
    LiturgyRoleResponse toLiturgyRoleResponse(BulletinLiturgyRole role);

    AnnouncementResponse toAnnouncementResponse(BulletinAnnouncement a);

    PrayerItemResponse toPrayerItemResponse(BulletinPrayerItem p);

    @Mapping(target = "cellId", source = "cell.id")
    @Mapping(target = "cellCode", source = "cell.code")
    @Mapping(target = "cellNameKr", source = "cell.nameKr")
    @Mapping(target = "cellNameEn", source = "cell.nameEn")
    @Mapping(target = "cellType", source = "cell.type")
    CellAttendanceResponse toCellAttendanceResponse(BulletinCellAttendance a);

    List<LiturgyRoleResponse> toLiturgyRoleResponses(List<BulletinLiturgyRole> roles);

    List<AnnouncementResponse> toAnnouncementResponses(List<BulletinAnnouncement> announcements);

    List<PrayerItemResponse> toPrayerItemResponses(List<BulletinPrayerItem> items);

    List<CellAttendanceResponse> toCellAttendanceResponses(List<BulletinCellAttendance> rows);

    @Named("memberRef")
    default MemberRef memberRef(Member m) {
        if (m == null) return null;
        return new MemberRef(m.getId(), m.getNameKr(), m.getNameEn());
    }

    @Named("cellRef")
    default CellRef cellRef(Cell c) {
        if (c == null) return null;
        return new CellRef(c.getId(), c.getCode(), c.getNameKr(), c.getNameEn(), c.getType());
    }
}

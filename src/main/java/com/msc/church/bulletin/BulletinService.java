package com.msc.church.bulletin;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.dto.AnnouncementRequest;
import com.msc.church.bulletin.dto.BulletinCreateRequest;
import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.bulletin.dto.BulletinDuplicateRequest;
import com.msc.church.bulletin.dto.BulletinSummary;
import com.msc.church.bulletin.dto.BulletinUpdateRequest;
import com.msc.church.bulletin.dto.CellAttendanceRequest;
import com.msc.church.bulletin.dto.LiturgyRoleRequest;
import com.msc.church.bulletin.dto.PrayerItemRequest;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bulletin module service. The non-trivial parts:
 * <ul>
 *   <li>Update strategy: delete-and-reinsert children (V1 simplicity, no FKs point at
 *       child rows). Each call rebuilds the four collections from the request payload.</li>
 *   <li>Duplicate: deep clones all children with new ids, resets {@code published}.</li>
 *   <li>Publish: validates required fields and required liturgy entries before flipping
 *       the flag; failure returns {@link ErrorCode#VALIDATION_FAILED} so the frontend
 *       can show the missing-requirements checklist.</li>
 *   <li>Visibility: non-staff callers (LEADER / MEMBER) only see {@code published=true}
 *       bulletins.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinService {

    /** Liturgy roles that must be present to publish. */
    private static final Set<LiturgyRoleType> REQUIRED_LITURGY = EnumSet.of(
            LiturgyRoleType.LORDS_PRAYER, LiturgyRoleType.SERMON);

    private final BulletinRepository bulletinRepository;
    private final MemberRepository memberRepository;
    private final CellRepository cellRepository;
    private final BulletinMapper bulletinMapper;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<BulletinSummary> list(LocalDate from, LocalDate to, Boolean publishedFilter,
                                      Pageable pageable, AuthenticatedUser caller) {
        boolean staff = isStaff(caller);
        Page<Bulletin> page;

        Boolean publishedToUse = staff ? publishedFilter : Boolean.TRUE;
        LocalDate fromToUse = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toToUse = to != null ? to : LocalDate.of(9999, 12, 31);

        if (publishedToUse == null) {
            page = bulletinRepository.findByServiceDateBetween(fromToUse, toToUse, pageable);
        } else {
            page = bulletinRepository.findByPublishedAndServiceDateBetween(
                    publishedToUse, fromToUse, toToUse, pageable);
        }
        return page.map(bulletinMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public BulletinDetail get(Long id, AuthenticatedUser caller) {
        Bulletin b = bulletinRepository.findWithChildrenById(id)
                .orElseThrow(() -> new BulletinNotFoundException(id));
        enforceVisibility(b, caller);
        return bulletinMapper.toDetail(b);
    }

    @Transactional(readOnly = true)
    public BulletinDetail getByDate(LocalDate date, AuthenticatedUser caller) {
        Bulletin b = bulletinRepository.findWithChildrenByServiceDate(date)
                .orElseThrow(() -> new BulletinNotFoundException(date.toString()));
        enforceVisibility(b, caller);
        return bulletinMapper.toDetail(b);
    }

    @Transactional(readOnly = true)
    public BulletinDetail current(AuthenticatedUser caller) {
        LocalDate cutoff = LocalDate.now().plusDays(1);
        Bulletin b = bulletinRepository
                .findFirstByPublishedTrueAndServiceDateLessThanEqualOrderByServiceDateDesc(cutoff)
                .orElseThrow(() -> new BulletinNotFoundException("current"));
        // current is a public-facing read so the published filter is implicit in the query.
        return bulletinMapper.toDetail(reloadWithChildren(b.getId()));
    }

    // ---------- writes ----------

    @Transactional
    public BulletinDetail create(BulletinCreateRequest request, AuthenticatedUser caller) {
        if (bulletinRepository.existsByServiceDate(request.serviceDate())) {
            throw new DuplicateBulletinDateException(request.serviceDate());
        }
        validateLiturgyAssignees(request.liturgyRoles());

        Bulletin bulletin = Bulletin.builder()
                .serviceDate(request.serviceDate())
                .theme(request.theme())
                .songOfWeekTitle(request.songOfWeekTitle())
                .songOfWeekLyrics(request.songOfWeekLyrics())
                .memoryVerseRef(request.memoryVerseRef())
                .memoryVerseTextKr(request.memoryVerseTextKr())
                .memoryVerseTextEn(request.memoryVerseTextEn())
                .presider(loadMember(request.presiderMemberId()))
                .nextWeekPrayer(loadMember(request.nextWeekPrayerMemberId()))
                .published(false)
                .build();

        bulletin.replaceLiturgyRoles(buildLiturgyRoles(request.liturgyRoles()));
        bulletin.replaceAnnouncements(buildAnnouncements(request.announcements()));
        bulletin.replacePrayerItems(buildPrayerItems(request.prayerItems()));
        bulletin.replaceCellAttendance(buildCellAttendance(request.cellAttendance()));

        Bulletin saved = bulletinRepository.save(bulletin);
        bulletinRepository.flush(); // assign IDENTITY ids to cascaded children before mapping
        log.info("Bulletin created: id={} date={} byUser={}",
                saved.getId(), saved.getServiceDate(), callerId(caller));
        return bulletinMapper.toDetail(saved);
    }

    @Transactional
    public BulletinDetail update(Long id, BulletinUpdateRequest request, AuthenticatedUser caller) {
        Bulletin bulletin = bulletinRepository.findWithChildrenById(id)
                .orElseThrow(() -> new BulletinNotFoundException(id));
        validateLiturgyAssignees(request.liturgyRoles());

        if (!bulletin.getServiceDate().equals(request.serviceDate())
                && bulletinRepository.existsByServiceDate(request.serviceDate())) {
            throw new DuplicateBulletinDateException(request.serviceDate());
        }

        bulletin.setServiceDate(request.serviceDate());
        bulletin.setTheme(request.theme());
        bulletin.setSongOfWeekTitle(request.songOfWeekTitle());
        bulletin.setSongOfWeekLyrics(request.songOfWeekLyrics());
        bulletin.setMemoryVerseRef(request.memoryVerseRef());
        bulletin.setMemoryVerseTextKr(request.memoryVerseTextKr());
        bulletin.setMemoryVerseTextEn(request.memoryVerseTextEn());
        bulletin.setPresider(loadMember(request.presiderMemberId()));
        bulletin.setNextWeekPrayer(loadMember(request.nextWeekPrayerMemberId()));

        bulletin.replaceLiturgyRoles(buildLiturgyRoles(request.liturgyRoles()));
        bulletin.replaceAnnouncements(buildAnnouncements(request.announcements()));
        bulletin.replacePrayerItems(buildPrayerItems(request.prayerItems()));
        bulletin.replaceCellAttendance(buildCellAttendance(request.cellAttendance()));

        bulletinRepository.flush(); // delete-old + insert-new before mapping returns ids
        log.info("Bulletin updated: id={} byUser={}", id, callerId(caller));
        return bulletinMapper.toDetail(bulletin);
    }

    @Transactional
    public BulletinDetail duplicate(Long sourceId, BulletinDuplicateRequest request, AuthenticatedUser caller) {
        Bulletin source = bulletinRepository.findWithChildrenById(sourceId)
                .orElseThrow(() -> new BulletinNotFoundException(sourceId));

        if (bulletinRepository.existsByServiceDate(request.newServiceDate())) {
            throw new DuplicateBulletinDateException(request.newServiceDate());
        }

        Bulletin copy = Bulletin.builder()
                .serviceDate(request.newServiceDate())
                .theme(source.getTheme())
                .songOfWeekTitle(source.getSongOfWeekTitle())
                .songOfWeekLyrics(source.getSongOfWeekLyrics())
                .memoryVerseRef(source.getMemoryVerseRef())
                .memoryVerseTextKr(source.getMemoryVerseTextKr())
                .memoryVerseTextEn(source.getMemoryVerseTextEn())
                .presider(source.getPresider())
                .nextWeekPrayer(source.getNextWeekPrayer())
                .published(false)
                .build();

        // Deep-clone each child with new id (omit id, JPA assigns).
        List<BulletinLiturgyRole> roles = new ArrayList<>();
        for (BulletinLiturgyRole r : source.getLiturgyRoles()) {
            roles.add(BulletinLiturgyRole.builder()
                    .roleType(r.getRoleType())
                    .title(r.getTitle())
                    .assigneeMember(r.getAssigneeMember())
                    .assigneeCell(r.getAssigneeCell())
                    .assigneeLabel(r.getAssigneeLabel())
                    .orderIdx(r.getOrderIdx())
                    .standing(r.isStanding())
                    .build());
        }
        copy.replaceLiturgyRoles(roles);

        List<BulletinAnnouncement> announcements = new ArrayList<>();
        for (BulletinAnnouncement a : source.getAnnouncements()) {
            announcements.add(BulletinAnnouncement.builder()
                    .contentKr(a.getContentKr())
                    .contentEn(a.getContentEn())
                    .orderIdx(a.getOrderIdx())
                    .build());
        }
        copy.replaceAnnouncements(announcements);

        List<BulletinPrayerItem> prayerItems = new ArrayList<>();
        for (BulletinPrayerItem p : source.getPrayerItems()) {
            prayerItems.add(BulletinPrayerItem.builder()
                    .category(p.getCategory())
                    .contentKr(p.getContentKr())
                    .contentEn(p.getContentEn())
                    .orderIdx(p.getOrderIdx())
                    .build());
        }
        copy.replacePrayerItems(prayerItems);

        List<BulletinCellAttendance> cellAttendance = new ArrayList<>();
        for (BulletinCellAttendance c : source.getCellAttendance()) {
            cellAttendance.add(BulletinCellAttendance.builder()
                    .cell(c.getCell())
                    .attendanceCount(c.getAttendanceCount())
                    .build());
        }
        copy.replaceCellAttendance(cellAttendance);

        Bulletin saved = bulletinRepository.save(copy);
        bulletinRepository.flush();
        log.info("Bulletin duplicated: source={} new={} byUser={}",
                sourceId, saved.getId(), callerId(caller));
        return bulletinMapper.toDetail(saved);
    }

    @Transactional
    public BulletinDetail publish(Long id, AuthenticatedUser caller) {
        Bulletin bulletin = bulletinRepository.findWithChildrenById(id)
                .orElseThrow(() -> new BulletinNotFoundException(id));

        if (bulletin.isPublished()) {
            throw new BulletinAlreadyPublishedException(id);
        }

        validatePublishable(bulletin);

        bulletin.setPublished(true);
        bulletin.setPublishedAt(LocalDateTime.now());
        log.info("Bulletin published: id={} byUser={}", id, callerId(caller));
        return bulletinMapper.toDetail(bulletin);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        Bulletin b = bulletinRepository.findById(id)
                .orElseThrow(() -> new BulletinNotFoundException(id));
        if (b.isPublished()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        bulletinRepository.delete(b);
        log.info("Bulletin deleted: id={} byUser={}", id, callerId(caller));
    }

    // ---------- helpers ----------

    private Bulletin reloadWithChildren(Long id) {
        return bulletinRepository.findWithChildrenById(id)
                .orElseThrow(() -> new BulletinNotFoundException(id));
    }

    private void validatePublishable(Bulletin b) {
        if (b.getPresider() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "presider");
        }
        boolean hasScripture = (b.getMemoryVerseRef() != null && !b.getMemoryVerseRef().isBlank());
        if (!hasScripture) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "scripture");
        }
        Set<LiturgyRoleType> present = EnumSet.noneOf(LiturgyRoleType.class);
        for (BulletinLiturgyRole r : b.getLiturgyRoles()) {
            if (r.getRoleType() != null) present.add(r.getRoleType());
        }
        for (LiturgyRoleType required : REQUIRED_LITURGY) {
            if (!present.contains(required)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, required.name());
            }
        }
    }

    private void validateLiturgyAssignees(List<LiturgyRoleRequest> roles) {
        if (roles == null) return;
        for (LiturgyRoleRequest r : roles) {
            int set = 0;
            if (r.assigneeMemberId() != null) set++;
            if (r.assigneeCellId() != null) set++;
            if (r.assigneeLabel() != null && !r.assigneeLabel().isBlank()) set++;
            if (set > 1) {
                // 0 is allowed (e.g. Lord's Prayer); >1 is ambiguous and rejected.
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "liturgyAssignee");
            }
        }
    }

    private List<BulletinLiturgyRole> buildLiturgyRoles(List<LiturgyRoleRequest> requests) {
        if (requests == null) return List.of();
        List<BulletinLiturgyRole> out = new ArrayList<>(requests.size());
        for (LiturgyRoleRequest r : requests) {
            out.add(BulletinLiturgyRole.builder()
                    .roleType(r.roleType())
                    .title(blankToNull(r.title()))
                    .assigneeMember(loadMember(r.assigneeMemberId()))
                    .assigneeCell(loadCell(r.assigneeCellId()))
                    .assigneeLabel(blankToNull(r.assigneeLabel()))
                    .orderIdx(r.orderIdx())
                    .standing(r.standing())
                    .build());
        }
        return out;
    }

    private List<BulletinAnnouncement> buildAnnouncements(List<AnnouncementRequest> requests) {
        if (requests == null) return List.of();
        List<BulletinAnnouncement> out = new ArrayList<>(requests.size());
        for (AnnouncementRequest a : requests) {
            out.add(BulletinAnnouncement.builder()
                    .contentKr(blankToNull(a.contentKr()))
                    .contentEn(blankToNull(a.contentEn()))
                    .orderIdx(a.orderIdx())
                    .build());
        }
        return out;
    }

    private List<BulletinPrayerItem> buildPrayerItems(List<PrayerItemRequest> requests) {
        if (requests == null) return List.of();
        List<BulletinPrayerItem> out = new ArrayList<>(requests.size());
        for (PrayerItemRequest p : requests) {
            out.add(BulletinPrayerItem.builder()
                    .category(p.category())
                    .contentKr(blankToNull(p.contentKr()))
                    .contentEn(blankToNull(p.contentEn()))
                    .orderIdx(p.orderIdx())
                    .build());
        }
        return out;
    }

    private List<BulletinCellAttendance> buildCellAttendance(List<CellAttendanceRequest> requests) {
        if (requests == null) return List.of();
        List<BulletinCellAttendance> out = new ArrayList<>(requests.size());
        for (CellAttendanceRequest c : requests) {
            Cell cell = cellRepository.findById(c.cellId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CELL_NOT_FOUND));
            out.add(BulletinCellAttendance.builder()
                    .cell(cell)
                    .attendanceCount(c.attendanceCount())
                    .build());
        }
        return out;
    }

    private Member loadMember(Long id) {
        if (id == null) return null;
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Cell loadCell(Long id) {
        if (id == null) return null;
        return cellRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CELL_NOT_FOUND));
    }

    private void enforceVisibility(Bulletin b, AuthenticatedUser caller) {
        if (isStaff(caller)) return;
        if (!b.isPublished()) {
            throw new BulletinNotFoundException(b.getId());
        }
    }

    private boolean isStaff(AuthenticatedUser caller) {
        return caller != null && (caller.role() == Role.ADMIN || caller.role() == Role.PASTOR);
    }

    private static Long callerId(AuthenticatedUser caller) {
        return caller == null ? null : caller.id();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

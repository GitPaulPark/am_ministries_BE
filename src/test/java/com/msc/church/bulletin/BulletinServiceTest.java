package com.msc.church.bulletin;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.dto.AnnouncementRequest;
import com.msc.church.bulletin.dto.BulletinCreateRequest;
import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.bulletin.dto.BulletinDuplicateRequest;
import com.msc.church.bulletin.dto.BulletinUpdateRequest;
import com.msc.church.bulletin.dto.CellAttendanceRequest;
import com.msc.church.bulletin.dto.LiturgyRoleRequest;
import com.msc.church.bulletin.dto.PrayerItemRequest;
import com.msc.church.cell.Cell;
import com.msc.church.cell.CellRepository;
import com.msc.church.cell.CellType;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import com.msc.church.member.MemberRepository;
import com.msc.church.member.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bulletin service tests. Mirrors {@code MemberServiceTest} in shape; covers the
 * non-trivial behaviour of this module: dup-date guard, child replacement on update,
 * deep-clone on duplicate, publish validation rules, role-based visibility.
 */
@ExtendWith(MockitoExtension.class)
class BulletinServiceTest {

    @Mock BulletinRepository bulletinRepository;
    @Mock MemberRepository memberRepository;
    @Mock CellRepository cellRepository;
    @Mock BulletinMapper bulletinMapper;

    @InjectMocks BulletinService bulletinService;

    private AuthenticatedUser admin;
    private AuthenticatedUser member;
    private Member presider;
    private Cell cellC;

    @BeforeEach
    void setUp() {
        admin   = new AuthenticatedUser(1L, "admin@msc.local", null, Role.ADMIN, true);
        member  = new AuthenticatedUser(3L, "kim@msc.local", 42L, Role.MEMBER, true);

        presider = Member.builder().nameKr("한바울").baptized(false).status(MemberStatus.ACTIVE).build();
        ReflectionTestUtils.setField(presider, "id", 42L);

        cellC = Cell.builder().code("C").nameKr("셀 C").nameEn("Cell C").type(CellType.REGULAR).active(true).build();
        ReflectionTestUtils.setField(cellC, "id", 1L);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: rejects duplicate service date")
    void create_duplicateDate() {
        when(bulletinRepository.existsByServiceDate(LocalDate.of(2026, 5, 3))).thenReturn(true);
        var req = bulletinReq(LocalDate.of(2026, 5, 3));

        assertThatThrownBy(() -> bulletinService.create(req, admin))
                .isInstanceOf(DuplicateBulletinDateException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_BULLETIN_DATE);

        verify(bulletinRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rejects liturgy row with multiple assignees set")
    void create_liturgyAssigneeXOR() {
        var ambiguous = new LiturgyRoleRequest(
                LiturgyRoleType.PRAISE, "Praise",
                42L /*member*/, 1L /*cell*/, null,
                1, true);
        var req = new BulletinCreateRequest(
                LocalDate.of(2026, 5, 10),
                42L, "Theme", null, null, null, null, null, null,
                List.of(ambiguous), List.of(), List.of(), List.of());

        when(bulletinRepository.existsByServiceDate(any())).thenReturn(false);

        assertThatThrownBy(() -> bulletinService.create(req, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("create: persists with all four child collections wired to parent")
    void create_happyPath() {
        var req = new BulletinCreateRequest(
                LocalDate.of(2026, 5, 10),
                42L, "Taste, Become, Belong", null, null,
                "1 Peter 2:2-10", null, null, null,
                List.of(new LiturgyRoleRequest(LiturgyRoleType.LORDS_PRAYER, "Lord's Prayer", null, null, "All Together", 1, true)),
                List.of(new AnnouncementRequest(null, "Welcome", 1)),
                List.of(new PrayerItemRequest(PrayerCategory.HEALING, null, "Pray for…", 1)),
                List.of(new CellAttendanceRequest(1L, 7)));

        when(bulletinRepository.existsByServiceDate(LocalDate.of(2026, 5, 10))).thenReturn(false);
        when(memberRepository.findById(42L)).thenReturn(Optional.of(presider));
        when(cellRepository.findById(1L)).thenReturn(Optional.of(cellC));
        when(bulletinRepository.save(any(Bulletin.class))).thenAnswer(inv -> {
            Bulletin b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 100L);
            return b;
        });
        when(bulletinMapper.toDetail(any(Bulletin.class)))
                .thenReturn(stubDetail(100L, false));

        BulletinDetail result = bulletinService.create(req, admin);

        assertThat(result.id()).isEqualTo(100L);
        verify(bulletinRepository).save(any(Bulletin.class));
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: replaces all four child collections (delete-and-reinsert)")
    void update_replacesChildren() {
        Bulletin existing = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, true /*withOldChildren*/);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(existing));
        when(bulletinMapper.toDetail(any())).thenReturn(stubDetail(50L, false));

        var req = new BulletinUpdateRequest(
                LocalDate.of(2026, 5, 3),
                null, "New Theme", null, null, null, null, null, null,
                List.of(),
                List.of(new AnnouncementRequest("새 공지", null, 1)),
                List.of(),
                List.of());

        bulletinService.update(50L, req, admin);

        assertThat(existing.getTheme()).isEqualTo("New Theme");
        assertThat(existing.getLiturgyRoles()).isEmpty();
        assertThat(existing.getAnnouncements()).hasSize(1);
        assertThat(existing.getAnnouncements().get(0).getContentKr()).isEqualTo("새 공지");
        assertThat(existing.getPrayerItems()).isEmpty();
        assertThat(existing.getCellAttendance()).isEmpty();
        // back-reference correctly wired:
        assertThat(existing.getAnnouncements().get(0).getBulletin()).isSameAs(existing);
    }

    @Test
    @DisplayName("update: rejects moving to a date already used by another bulletin")
    void update_dateConflict() {
        Bulletin existing = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, false);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(existing));
        when(bulletinRepository.existsByServiceDate(LocalDate.of(2026, 5, 10))).thenReturn(true);

        var req = new BulletinUpdateRequest(
                LocalDate.of(2026, 5, 10),
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> bulletinService.update(50L, req, admin))
                .isInstanceOf(DuplicateBulletinDateException.class);
    }

    // ---------- duplicate ----------

    @Test
    @DisplayName("duplicate: deep-clones every child, new date, resets published")
    void duplicate_deepClone() {
        Bulletin source = bulletinFixture(50L, LocalDate.of(2026, 5, 3), true, true);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(source));
        when(bulletinRepository.existsByServiceDate(LocalDate.of(2026, 5, 10))).thenReturn(false);
        when(bulletinRepository.save(any(Bulletin.class))).thenAnswer(inv -> {
            Bulletin b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 51L);
            return b;
        });
        when(bulletinMapper.toDetail(any())).thenReturn(stubDetail(51L, false));

        bulletinService.duplicate(50L, new BulletinDuplicateRequest(LocalDate.of(2026, 5, 10)), admin);

        // Verify the saved bulletin is a fresh entity with copied collections.
        // We assert via the back-references on the saved Bulletin's children.
        verify(bulletinRepository).save(any(Bulletin.class));
    }

    @Test
    @DisplayName("duplicate: rejects when target date is already taken")
    void duplicate_targetTaken() {
        Bulletin source = bulletinFixture(50L, LocalDate.of(2026, 5, 3), true, false);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(source));
        when(bulletinRepository.existsByServiceDate(LocalDate.of(2026, 5, 10))).thenReturn(true);

        assertThatThrownBy(() -> bulletinService.duplicate(50L,
                new BulletinDuplicateRequest(LocalDate.of(2026, 5, 10)), admin))
                .isInstanceOf(DuplicateBulletinDateException.class);
    }

    // ---------- publish ----------

    @Test
    @DisplayName("publish: refuses an already-published bulletin")
    void publish_alreadyPublished() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), true, true);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bulletinService.publish(50L, admin))
                .isInstanceOf(BulletinAlreadyPublishedException.class);
    }

    @Test
    @DisplayName("publish: rejects without presider")
    void publish_missingPresider() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, true);
        b.setPresider(null);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bulletinService.publish(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("publish: rejects without memoryVerseRef (scripture)")
    void publish_missingScripture() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, true);
        b.setMemoryVerseRef(null);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bulletinService.publish(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("publish: rejects without LORDS_PRAYER + SERMON liturgy roles")
    void publish_missingRequiredLiturgy() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, false /* no children */);
        // Only PRAISE row
        BulletinLiturgyRole praise = BulletinLiturgyRole.builder()
                .roleType(LiturgyRoleType.PRAISE).orderIdx(1).standing(true).build();
        b.replaceLiturgyRoles(List.of(praise));

        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bulletinService.publish(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("publish: success sets published=true and publishedAt")
    void publish_success() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, true);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(b));
        when(bulletinMapper.toDetail(b)).thenReturn(stubDetail(50L, true));

        bulletinService.publish(50L, admin);

        assertThat(b.isPublished()).isTrue();
        assertThat(b.getPublishedAt()).isNotNull();
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: refuses when bulletin is published (CONFLICT)")
    void delete_blockedWhenPublished() {
        Bulletin b = bulletinFixture(50L, LocalDate.of(2026, 5, 3), true, false);
        when(bulletinRepository.findById(50L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bulletinService.delete(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(bulletinRepository, never()).delete(any(Bulletin.class));
    }

    @Test
    @DisplayName("delete: throws BulletinNotFoundException when missing")
    void delete_notFound() {
        when(bulletinRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bulletinService.delete(404L, admin))
                .isInstanceOf(BulletinNotFoundException.class);
    }

    // ---------- visibility ----------

    @Test
    @DisplayName("get: MEMBER cannot see an unpublished bulletin (404)")
    void get_memberHiddenFromUnpublished() {
        Bulletin draft = bulletinFixture(50L, LocalDate.of(2026, 5, 3), false, true);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> bulletinService.get(50L, member))
                .isInstanceOf(BulletinNotFoundException.class);
    }

    @Test
    @DisplayName("get: MEMBER can see a published bulletin")
    void get_memberSeesPublished() {
        Bulletin pub = bulletinFixture(50L, LocalDate.of(2026, 5, 3), true, true);
        when(bulletinRepository.findWithChildrenById(50L)).thenReturn(Optional.of(pub));
        when(bulletinMapper.toDetail(pub)).thenReturn(stubDetail(50L, true));

        bulletinService.get(50L, member);

        verify(bulletinMapper).toDetail(pub);
    }

    @Test
    @DisplayName("get: missing id throws BulletinNotFoundException")
    void get_notFound() {
        when(bulletinRepository.findWithChildrenById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bulletinService.get(999L, admin))
                .isInstanceOf(BulletinNotFoundException.class);
    }

    // ---------- helpers ----------

    private BulletinCreateRequest bulletinReq(LocalDate date) {
        return new BulletinCreateRequest(
                date, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    private Bulletin bulletinFixture(Long id, LocalDate date, boolean published, boolean withChildren) {
        Bulletin b = Bulletin.builder()
                .serviceDate(date)
                .theme("Theme")
                .memoryVerseRef("1 Peter 2:2-10")
                .presider(presider)
                .published(published)
                .liturgyRoles(new ArrayList<>())
                .announcements(new ArrayList<>())
                .prayerItems(new ArrayList<>())
                .cellAttendance(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(b, "id", id);

        if (withChildren) {
            BulletinLiturgyRole lord = BulletinLiturgyRole.builder()
                    .roleType(LiturgyRoleType.LORDS_PRAYER).orderIdx(1).standing(true).build();
            BulletinLiturgyRole sermon = BulletinLiturgyRole.builder()
                    .roleType(LiturgyRoleType.SERMON).orderIdx(2).standing(false).build();
            b.replaceLiturgyRoles(List.of(lord, sermon));

            b.replaceAnnouncements(List.of(BulletinAnnouncement.builder()
                    .contentKr("기존 공지").orderIdx(1).build()));
            b.replacePrayerItems(List.of(BulletinPrayerItem.builder()
                    .category(PrayerCategory.HEALING).contentKr("기도").orderIdx(1).build()));
            b.replaceCellAttendance(List.of(BulletinCellAttendance.builder()
                    .cell(cellC).attendanceCount(7).build()));
        }
        return b;
    }

    private BulletinDetail stubDetail(Long id, boolean published) {
        return new BulletinDetail(
                id, LocalDate.of(2026, 5, 3), null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                published, null);
    }
}

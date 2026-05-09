package com.msc.church.sermon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.bulletin.BulletinRepository;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.MemberRepository;
import com.msc.church.sermon.dto.SermonCreateRequest;
import com.msc.church.sermon.dto.SermonUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SermonServiceTest {

    @Mock SermonRepository sermonRepository;
    @Mock MemberRepository memberRepository;
    @Mock BulletinRepository bulletinRepository;

    // Use a real mapper because the service calls into serialize/parse helpers — easier
    // than maintaining a mock with stubbed JSON behaviour.
    SermonMapper sermonMapper = new SermonMapper(new ObjectMapper());

    @InjectMocks SermonService sermonService;

    private AuthenticatedUser admin;
    private AuthenticatedUser member;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedUser(1L, "a@msc.local", null, Role.ADMIN, true);
        member = new AuthenticatedUser(2L, "m@msc.local", 99L, Role.MEMBER, true);
        // Wire the real mapper into the @InjectMocks-built service:
        ReflectionTestUtils.setField(sermonService, "sermonMapper", sermonMapper);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: rejects duplicate sermon date")
    void create_dupDate() {
        when(sermonRepository.existsBySermonDate(LocalDate.of(2026, 5, 17))).thenReturn(true);
        var req = new SermonCreateRequest(
                LocalDate.of(2026, 5, 17), null, null, null, null,
                "1 Pet 2:5", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> sermonService.create(req, admin))
                .isInstanceOf(DuplicateSermonDateException.class);
        verify(sermonRepository, never()).save(any(Sermon.class));
    }

    @Test
    @DisplayName("create: serialises reflection questions to JSON")
    void create_questionsJson() {
        when(sermonRepository.existsBySermonDate(any())).thenReturn(false);
        when(sermonRepository.save(any(Sermon.class))).thenAnswer(inv -> {
            Sermon s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 7L);
            return s;
        });

        var req = new SermonCreateRequest(
                LocalDate.of(2026, 5, 17), "맛보고", "Taste", null, "Pastor Han (Guest)",
                "1 Peter 2:2-10", null, null, null, null, null, "salvation",
                List.of("Q1?", "Q2?"), null);
        var detail = sermonService.create(req, admin);

        assertThat(detail.cellReflectionQuestions()).containsExactly("Q1?", "Q2?");
        assertThat(detail.published()).isFalse();
    }

    // ---------- publish ----------

    @Test
    @DisplayName("publish: rejects when both titles are blank")
    void publish_missingTitles() {
        Sermon s = sermon(50L, false);
        s.setTitleKr(null); s.setTitleEn(null);
        s.setPreacherNameLabel("Guest");
        when(sermonRepository.findById(50L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> sermonService.publish(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("publish: rejects when no preacher (member or label)")
    void publish_missingPreacher() {
        Sermon s = sermon(50L, false);
        s.setTitleEn("Title"); s.setPreacher(null); s.setPreacherNameLabel(null);
        when(sermonRepository.findById(50L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> sermonService.publish(50L, admin))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("publish: idempotent — already-published returns current state")
    void publish_idempotent() {
        Sermon s = sermon(50L, true);
        when(sermonRepository.findById(50L)).thenReturn(Optional.of(s));
        var before = s.getPublishedAt();
        sermonService.publish(50L, admin);
        // Still published, publishedAt unchanged.
        assertThat(s.isPublished()).isTrue();
        assertThat(s.getPublishedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("publish: success sets flag + timestamp")
    void publish_happy() {
        Sermon s = sermon(50L, false);
        s.setTitleEn("Title"); s.setPreacherNameLabel("Guest");
        when(sermonRepository.findById(50L)).thenReturn(Optional.of(s));

        sermonService.publish(50L, admin);

        assertThat(s.isPublished()).isTrue();
        assertThat(s.getPublishedAt()).isNotNull();
    }

    // ---------- visibility ----------

    @Test
    @DisplayName("get: MEMBER seeing an unpublished sermon → 404")
    void get_memberHiddenFromDraft() {
        Sermon s = sermon(50L, false);
        when(sermonRepository.findWithRefsById(50L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> sermonService.get(50L, member))
                .isInstanceOf(SermonNotFoundException.class);
    }

    @Test
    @DisplayName("search: members are forced to publishedOnly=true regardless of staff filter")
    void search_memberPublishedOnly() {
        when(sermonRepository.search(any(), any(), any(), org.mockito.ArgumentMatchers.eq(true), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        sermonService.search("Taste", null, null,
                org.springframework.data.domain.PageRequest.of(0, 10), member);
        verify(sermonRepository).search(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(true), any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: rejects moving date onto another sermon's date")
    void update_dateConflict() {
        Sermon s = sermon(50L, false);
        when(sermonRepository.findById(50L)).thenReturn(Optional.of(s));
        when(sermonRepository.existsBySermonDate(LocalDate.of(2026, 5, 24))).thenReturn(true);

        var req = new SermonUpdateRequest(
                LocalDate.of(2026, 5, 24), "T", null, null, "Guest",
                "1 Peter 2:2", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> sermonService.update(50L, req, admin))
                .isInstanceOf(DuplicateSermonDateException.class);
    }

    private Sermon sermon(Long id, boolean published) {
        Sermon s = Sermon.builder()
                .sermonDate(LocalDate.of(2026, 5, 17))
                .titleEn("Title")
                .scriptureRef("1 Peter 2:2-10")
                .preacherNameLabel("Guest")
                .published(published)
                .publishedAt(published ? java.time.LocalDateTime.of(2026, 5, 16, 12, 0) : null)
                .build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}

package com.msc.church.publicapi;

import com.msc.church.bulletin.BulletinService;
import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.common.ApiResponse;
import com.msc.church.common.BusinessException;
import com.msc.church.sermon.SermonService;
import com.msc.church.sermon.dto.SermonDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints under {@code /api/v1/public/**} are open to unauthenticated visitors
 * (see {@link com.msc.church.config.SecurityConfig}). They return only published
 * content — drafts and admin-only fields are filtered by the underlying services.
 *
 * <p>Target audience is elderly church members landing on the home page; we keep
 * the surface intentionally tiny: this Sunday's bulletin + the latest sermon.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicHomeController {

    private final BulletinService bulletinService;
    private final SermonService sermonService;

    /** Combined this-week response — single round-trip for the landing page. */
    @GetMapping("/this-week")
    public ApiResponse<PublicThisWeekResponse> thisWeek() {
        BulletinDetail bulletin = swallowNotFound(() -> bulletinService.current(null));
        SermonDetail latestSermon = swallowNotFound(sermonService::latest);
        return ApiResponse.ok(new PublicThisWeekResponse(bulletin, latestSermon));
    }

    /** Caller-pulled bulletin (used when the homepage just wants this card). */
    @GetMapping("/bulletin/current")
    public ApiResponse<BulletinDetail> currentBulletin() {
        return ApiResponse.ok(swallowNotFound(() -> bulletinService.current(null)));
    }

    /** Caller-pulled sermon (used when the homepage just wants this card). */
    @GetMapping("/sermon/latest")
    public ApiResponse<SermonDetail> latestSermon() {
        return ApiResponse.ok(swallowNotFound(sermonService::latest));
    }

    /** A specific Sunday's bulletin — used when the elderly user taps "이번 주 주보". */
    @GetMapping("/bulletin/by-date/{date}")
    public ApiResponse<BulletinDetail> bulletinByDate(
            @org.springframework.web.bind.annotation.PathVariable
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        // Service filters to published-only when caller is null, so this is safe to expose.
        return ApiResponse.ok(swallowNotFound(() -> bulletinService.getByDate(date, null)));
    }

    /** A specific sermon detail — used by the public homepage card. */
    @GetMapping("/sermon/{id}")
    public ApiResponse<SermonDetail> sermon(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return ApiResponse.ok(swallowNotFound(() -> sermonService.get(id, null)));
    }

    /**
     * Newly-launched churches won't have a published bulletin yet — degrade
     * gracefully instead of returning 404 to a homepage card.
     */
    private static <T> T swallowNotFound(java.util.concurrent.Callable<T> call) {
        try { return call.call(); }
        catch (BusinessException e) { return null; }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}

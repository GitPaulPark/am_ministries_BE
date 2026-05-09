package com.msc.church.cell;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mondays at 9 AM (configurable). Walks active primary memberships in any NEWCOMER
 * cell, generating a PENDING transfer suggestion for anyone past the threshold who
 * doesn't already have one. The actual move happens when a pastor approves.
 *
 * <p>Disabled if {@code church.newcomer.scheduler-enabled = false} (e.g. tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewcomerGraduationScheduler {

    private final CellTransferSuggestionService service;

    @Value("${church.newcomer.graduation-after-weeks:8}")
    private int weeksThreshold;

    @Value("${church.newcomer.scheduler-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${church.newcomer.suggestion-cron:0 0 9 ? * MON}")
    public void scan() {
        if (!enabled) return;
        try {
            int created = service.scanForGraduations(weeksThreshold);
            log.debug("Newcomer graduation scan complete: created={}", created);
        } catch (Exception e) {
            log.error("Newcomer graduation scan failed", e);
        }
    }
}

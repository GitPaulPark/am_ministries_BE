package com.msc.church.bulletin.dto;

/**
 * Slim member reference embedded in bulletin responses (presider, next-week prayer,
 * liturgy assignee). Loaded via JOIN regardless of {@code deleted_at} so historical
 * bulletins still display the original name even after a member is soft-deleted.
 */
public record MemberRef(
        Long id,
        String nameKr,
        String nameEn
) {
}

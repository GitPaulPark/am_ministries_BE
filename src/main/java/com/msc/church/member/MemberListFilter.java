package com.msc.church.member;

/**
 * Bag-of-filters for {@code GET /members}. Records bind nicely from query params via
 * Spring's {@code @ModelAttribute}-style resolution, so the controller can take a
 * single argument.
 */
public record MemberListFilter(
        String q,
        Long cellId,
        MemberStatus status,
        String roleLabel
) {
}

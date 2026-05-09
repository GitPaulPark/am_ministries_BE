package com.msc.church.bulletin;

/**
 * Stored as VARCHAR in {@code bulletin_liturgy_roles.role_type}. The order of the
 * values mirrors the typical service flow but {@link BulletinLiturgyRole#orderIdx} is
 * what actually positions a row in the bulletin — this enum is the closed catalogue
 * of role kinds, not the order.
 */
public enum LiturgyRoleType {
    LORDS_PRAYER,
    PRAISE,
    PRAYER,
    SCRIPTURE_READING,
    WELCOME,
    CHORAL_PRAISE,
    SERMON,
    OFFERTORY,
    THANKSGIVING,
    CLOSING_SONG,
    BENEDICTION
}

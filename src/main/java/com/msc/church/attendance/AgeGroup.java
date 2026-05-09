package com.msc.church.attendance;

/**
 * V1 catalogue of age groups. Stored as VARCHAR(50) so future tiers can be added
 * without a schema change — the enum here is just the validated client surface.
 */
public enum AgeGroup {
    ADULT,
    JESUS_GEN,
    SHALOM,
    HOSANNA,
    PAIDION
}

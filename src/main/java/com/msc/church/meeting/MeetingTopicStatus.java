package com.msc.church.meeting;

/**
 * Per-topic lifecycle the user can flip directly on the meeting detail page.
 * Distinct from {@link ActionItemStatus} (OPEN/DONE/CANCELLED) — topics are
 * discussion items, not assignable tasks.
 */
public enum MeetingTopicStatus {
    /** Discussed, no specific decision required. */
    DISCUSSED,
    /** Discussed but a decision is pending. */
    PENDING,
    /** Decision was made and documented. */
    RESOLVED,
    /** Pushed to a later meeting. */
    DEFERRED,
    /** Needs follow-up before next meeting (often spawns an action item). */
    FOLLOW_UP
}

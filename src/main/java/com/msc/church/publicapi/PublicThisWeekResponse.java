package com.msc.church.publicapi;

import com.msc.church.bulletin.dto.BulletinDetail;
import com.msc.church.sermon.dto.SermonDetail;

/**
 * What a non-authenticated visitor sees on the landing page: this Sunday's
 * bulletin (if published) plus the most recent published sermon. Both fields
 * are nullable — the homepage degrades gracefully when content isn't ready yet.
 */
public record PublicThisWeekResponse(
        BulletinDetail bulletin,
        SermonDetail latestSermon) {
}

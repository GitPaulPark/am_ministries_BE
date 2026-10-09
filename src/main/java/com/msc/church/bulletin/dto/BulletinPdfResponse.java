package com.msc.church.bulletin.dto;

/** Response payload for the bulletin PDF upload — the public URL to the stored file. */
public record BulletinPdfResponse(String pdfUrl) {
}

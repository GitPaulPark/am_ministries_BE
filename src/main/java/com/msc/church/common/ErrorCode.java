package com.msc.church.common;

import org.springframework.http.HttpStatus;

/**
 * Canonical error catalogue. The {@code messageKey} resolves against
 * {@code messages_ko.properties} / {@code messages_en.properties} via
 * {@link org.springframework.context.MessageSource}. Never hardcode user-facing strings.
 */
public enum ErrorCode {

    // --- Generic ---
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "error.invalid_request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation_failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "error.unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "error.forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "error.not_found"),
    CONFLICT(HttpStatus.CONFLICT, "error.conflict"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal"),

    // --- Auth ---
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.invalid_credentials"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "error.auth.token_expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "error.auth.token_invalid"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "error.auth.refresh_invalid"),

    // --- Members ---
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.member.not_found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "error.member.duplicate_email"),

    // --- Cells ---
    CELL_NOT_FOUND(HttpStatus.NOT_FOUND, "error.cell.not_found"),
    DUPLICATE_CELL_CODE(HttpStatus.CONFLICT, "error.cell.duplicate_code"),

    // --- Bulletin ---
    BULLETIN_NOT_FOUND(HttpStatus.NOT_FOUND, "error.bulletin.not_found"),
    BULLETIN_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "error.bulletin.already_published"),
    DUPLICATE_BULLETIN_DATE(HttpStatus.CONFLICT, "error.bulletin.duplicate_date"),

    // --- Sermon ---
    SERMON_NOT_FOUND(HttpStatus.NOT_FOUND, "error.sermon.not_found"),
    DUPLICATE_SERMON_DATE(HttpStatus.CONFLICT, "error.sermon.duplicate_date"),

    // --- Meetings ---
    COMMITTEE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.committee.not_found"),
    DUPLICATE_COMMITTEE_CODE(HttpStatus.CONFLICT, "error.committee.duplicate_code"),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "error.meeting.not_found"),
    DUPLICATE_MEETING_DATE(HttpStatus.CONFLICT, "error.meeting.duplicate_date"),
    MEETING_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "error.meeting.already_published"),
    ACTION_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "error.action_item.not_found"),
    MEETING_TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "error.meeting_topic.not_found");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }
}

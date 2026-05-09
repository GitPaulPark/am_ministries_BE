package com.msc.church.common;

/**
 * Generic "entity not found by id" exception. Modules with richer 404 semantics
 * (e.g. {@code MemberNotFoundException}) should extend this and pass the
 * module-specific {@link ErrorCode}.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}

package com.msc.church.common;

import lombok.Getter;

/**
 * Base for all domain-level errors. Carries an {@link ErrorCode} so the
 * {@link GlobalExceptionHandler} can pick the right HTTP status and i18n key.
 * Optional {@code messageArgs} fill placeholders in the resolved message.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] messageArgs;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, new Object[0]);
    }

    public BusinessException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }
}

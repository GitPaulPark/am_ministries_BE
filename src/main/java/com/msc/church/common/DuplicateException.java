package com.msc.church.common;

/**
 * Generic conflict for unique-constraint violations surfaced before they hit the DB
 * (e.g. duplicate email, duplicate cell code).
 */
public class DuplicateException extends BusinessException {

    public DuplicateException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}

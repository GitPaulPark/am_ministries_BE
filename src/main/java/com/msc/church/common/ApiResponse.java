package com.msc.church.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Uniform response envelope. Every controller returns one of these — never raw entities,
 * never {@code ResponseEntity<Map>}. Error responses set {@code success=false},
 * {@code data=null}, {@code message} to a localized string and {@code errorCode} from
 * {@link ErrorCode}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String errorCode,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, message, code.name(), OffsetDateTime.now());
    }
}

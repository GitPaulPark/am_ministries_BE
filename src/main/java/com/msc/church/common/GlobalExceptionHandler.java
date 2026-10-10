package com.msc.church.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Translates exceptions into the {@link ApiResponse} error envelope. Business errors
 * resolve via {@link MessageSource}; unexpected errors return a generic 500 — never
 * leak a stack trace to the client.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        String message = resolve(code.getMessageKey(), ex.getMessageArgs());
        log.warn("BusinessException [{}]: {}", code.name(), message);
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        // The i18n template carries a {0} slot for the field detail — pass it
        // as the first message arg so Korean users see which input failed.
        String message = resolve(ErrorCode.VALIDATION_FAILED.getMessageKey(), detail);
        log.warn("Validation failed: {}", detail);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DataIntegrityViolation: {}", ex.getMostSpecificCause().getMessage());
        ErrorCode code = ErrorCode.CONFLICT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, resolve(code.getMessageKey())));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + " " + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }

    private String resolve(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }
}

package com.game.playforge.api.config;

import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global API exception handling.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        ResultCode resultCode = e.getResultCode();
        String message = (e.getMessage() == null || e.getMessage().isBlank())
                ? resultCode.getMessage()
                : e.getMessage();
        log.warn("Business exception, request={}, code={}, message={}", requestLabel(request), resultCode.getCode(), message);
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(ApiResult.fail(resultCode.getCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse(ResultCode.PARAM_VALIDATION_FAILED.getMessage());
        log.warn("Validation failed, request={}, message={}", requestLabel(request), message);
        return ResponseEntity.status(ResultCode.PARAM_VALIDATION_FAILED.getHttpStatus())
                .body(ApiResult.fail(ResultCode.PARAM_VALIDATION_FAILED.getCode(), message));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request
    ) {
        log.warn("Method not supported, request={}, supportedMethods={}", requestLabel(request), e.getSupportedMethods());
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResult.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("Resource not found, request={}, message={}", requestLabel(request), e.getMessage());
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(ApiResult.fail(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e, HttpServletRequest request) {
        log.debug("Client aborted request, request={}, message={}", requestLabel(request), e.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e, HttpServletRequest request) {
        log.debug("Async request became unusable, request={}, message={}", requestLabel(request), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception, request={}, message={}", requestLabel(request), e.getMessage(), e);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResult.fail(ResultCode.INTERNAL_ERROR));
    }

    private String requestLabel(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return request.getMethod() + " " + request.getRequestURI();
    }
}

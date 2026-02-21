package com.game.playforge.api.config;

import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * <p>
 * 统一捕获业务异常、参数校验异常和未知异常，转换为标准 {@link ApiResult} 响应，
 * 并设置与 {@link ResultCode} 绑定的HTTP状态码。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException e) {
        ResultCode resultCode = e.getResultCode();
        String message = (e.getMessage() == null || e.getMessage().isBlank())
                ? resultCode.getMessage()
                : e.getMessage();
        log.warn("业务异常, code={}, message={}", resultCode.getCode(), message);
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(ApiResult.fail(resultCode.getCode(), message));
    }

    /**
     * 处理参数校验异常
     *
     * @param e 参数校验异常
     * @return 失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse(ResultCode.PARAM_VALIDATION_FAILED.getMessage());
        log.warn("参数校验失败, message={}", message);
        return ResponseEntity.status(ResultCode.PARAM_VALIDATION_FAILED.getHttpStatus())
                .body(ApiResult.fail(ResultCode.PARAM_VALIDATION_FAILED.getCode(), message));
    }

    /**
     * 处理静态资源未找到异常（常见于扫描器探测）
     *
     * @param e 资源未找到异常
     * @return 404响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("资源未找到: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(ApiResult.fail(ResultCode.NOT_FOUND));
    }

    /**
     * 处理客户端断连异常（Broken pipe）
     * <p>
     * 客户端在响应传输中主动断开连接，无需返回响应体（连接已关闭）。
     * </p>
     *
     * @param e 客户端断连异常
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e) {
        log.debug("客户端断开连接: {}", e.getMessage());
    }

    /**
     * 处理异步请求断连异常（Spring 6+）
     * <p>
     * 客户端在异步响应写入过程中断开连接，无需返回响应体。
     * </p>
     *
     * @param e 异步请求不可用异常
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        log.debug("异步请求客户端断开连接: {}", e.getMessage());
    }

    /**
     * 处理未知异常
     *
     * @param e 异常
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("系统异常, message={}", e.getMessage(), e);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResult.fail(ResultCode.INTERNAL_ERROR));
    }
}

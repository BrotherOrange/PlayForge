package com.game.playforge.common.exception;

import com.game.playforge.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 用于在业务逻辑中抛出可预期的错误，携带 {@link ResultCode} 以便统一异常处理器转换为标准API响应。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务结果码
     */
    private final ResultCode resultCode;

    /**
     * 使用ResultCode创建业务异常，message取自ResultCode
     *
     * @param resultCode 业务结果码
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 使用ResultCode和自定义message创建业务异常
     *
     * @param resultCode 业务结果码
     * @param message    自定义异常描述
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}

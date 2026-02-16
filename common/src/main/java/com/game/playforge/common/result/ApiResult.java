package com.game.playforge.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应包装
 * <p>
 * 所有REST接口统一返回 {@code {code, message, data}} 格式。
 * </p>
 *
 * @param <T> 响应数据类型
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    /**
     * 业务结果码，0表示成功
     */
    private int code;

    /**
     * 结果描述信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功的ApiResult
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（无数据）
     *
     * @return 成功的ApiResult
     */
    public static ApiResult<Void> success() {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 失败响应（使用ResultCode）
     *
     * @param resultCode 业务结果码
     * @return 失败的ApiResult
     */
    public static ApiResult<Void> fail(ResultCode resultCode) {
        return new ApiResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败响应（自定义code和message）
     *
     * @param code    结果码
     * @param message 描述信息
     * @return 失败的ApiResult
     */
    public static ApiResult<Void> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}

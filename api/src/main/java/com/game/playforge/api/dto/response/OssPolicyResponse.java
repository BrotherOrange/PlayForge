package com.game.playforge.api.dto.response;

/**
 * OSS上传签名策略响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
public record OssPolicyResponse(
        String host,
        String policy,
        String signature,
        String accessKeyId,
        String key,
        long expire
) {
}

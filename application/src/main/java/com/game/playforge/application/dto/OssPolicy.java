package com.game.playforge.application.dto;

/**
 * OSS上传签名策略
 *
 * @author Richard Zhang
 * @since 1.0
 */
public record OssPolicy(
        String host,
        String policy,
        String signature,
        String accessKeyId,
        String key,
        long expire
) {
}

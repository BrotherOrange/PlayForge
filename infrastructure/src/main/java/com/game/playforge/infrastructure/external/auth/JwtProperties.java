package com.game.playforge.infrastructure.external.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性
 * <p>
 * 从 {@code application.yaml} 中的 {@code auth.jwt} 前缀读取配置。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /**
     * HMAC签名密钥
     */
    private String secret;

    /**
     * AccessToken过期时间（分钟），默认30
     */
    private long accessTokenExpireMinutes = 30;

    /**
     * RefreshToken过期时间（天），默认7
     */
    private long refreshTokenExpireDays = 7;
}

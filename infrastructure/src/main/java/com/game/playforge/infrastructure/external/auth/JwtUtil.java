package com.game.playforge.infrastructure.external.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 * <p>
 * 提供AccessToken的生成、解析和校验能力，基于JJWT实现。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * HMAC签名密钥
     */
    private final SecretKey key;

    /**
     * AccessToken有效期（毫秒）
     */
    private final long accessTokenExpireMillis;

    /**
     * 根据配置初始化签名密钥和有效期
     *
     * @param properties JWT配置属性
     */
    public JwtUtil(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireMillis = properties.getAccessTokenExpireMinutes() * 60 * 1000;
        log.info("JwtUtil初始化完成, accessTokenExpireMinutes={}", properties.getAccessTokenExpireMinutes());
    }

    /**
     * 生成AccessToken
     *
     * @param userId 用户ID
     * @return JWT字符串
     */
    public String generateAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpireMillis);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
        log.debug("生成AccessToken, userId={}", userId);
        return token;
    }

    /**
     * 解析Token中的用户ID
     *
     * @param token JWT字符串
     * @return 用户ID
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.parseLong(claims.getSubject());
        log.debug("解析Token成功, userId={}", userId);
        return userId;
    }

    /**
     * 判断Token是否已过期
     *
     * @param token JWT字符串
     * @return 过期返回true
     */
    public boolean isExpired(String token) {
        try {
            parseUserId(token);
            return false;
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期");
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 判断Token是否有效（签名正确且未过期）
     *
     * @param token JWT字符串
     * @return 有效返回true
     */
    public boolean isValid(String token) {
        try {
            parseUserId(token);
            return true;
        } catch (JwtException e) {
            log.debug("Token无效: {}", e.getMessage());
            return false;
        }
    }
}

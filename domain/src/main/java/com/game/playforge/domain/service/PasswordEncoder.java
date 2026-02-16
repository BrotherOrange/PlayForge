package com.game.playforge.domain.service;

/**
 * 密码编码器接口
 * <p>
 * 提供密码加密和校验能力，具体实现由基础设施层注入。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface PasswordEncoder {

    /**
     * 对原始密码进行加密
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    String encode(String rawPassword);

    /**
     * 校验原始密码与加密密码是否匹配
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 匹配返回true
     */
    boolean matches(String rawPassword, String encodedPassword);
}

package com.game.playforge.infrastructure.external.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.game.playforge.domain.service.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于BCrypt的密码编码器实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        String encoded = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
        log.debug("密码加密完成");
        return encoded;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        boolean matched = BCrypt.verifyer().verify(rawPassword.toCharArray(), encodedPassword).verified;
        log.debug("密码校验完成, matched={}", matched);
        return matched;
    }
}

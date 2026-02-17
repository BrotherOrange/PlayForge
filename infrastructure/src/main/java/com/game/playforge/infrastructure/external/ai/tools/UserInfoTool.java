package com.game.playforge.infrastructure.external.ai.tools;

import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.UserRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户信息查询工具
 * <p>
 * 提供用户信息查询能力，供Agent调用。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component("userInfoTool")
@RequiredArgsConstructor
public class UserInfoTool {

    private final UserRepository userRepository;

    @Tool("根据用户ID查询用户昵称")
    public String getUserNickname(@P("用户ID") Long userId) {
        log.debug("查询用户昵称, userId={}", userId);
        User user = userRepository.findById(userId);
        if (user == null) {
            log.debug("用户不存在, userId={}", userId);
            return "未知用户";
        }
        log.debug("查询用户昵称成功, userId={}, nickname={}", userId, user.getNickname());
        return user.getNickname();
    }
}

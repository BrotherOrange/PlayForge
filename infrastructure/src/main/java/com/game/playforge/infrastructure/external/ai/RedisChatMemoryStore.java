package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.common.constant.AgentConstants;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * 基于Redis的聊天记忆存储
 * <p>
 * 实现LangChain4J的 {@link ChatMemoryStore} 接口，
 * 使用Redis存储Agent会话的聊天记忆。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = AgentConstants.MEMORY_PREFIX + memoryId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("Redis记忆为空, memoryId={}", memoryId);
                return Collections.emptyList();
            }
            List<ChatMessage> messages = messagesFromJson(json);
            log.debug("从Redis加载记忆, memoryId={}, messageCount={}", memoryId, messages.size());
            return messages;
        } catch (Exception e) {
            log.error("从Redis加载记忆失败, memoryId={}", memoryId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = AgentConstants.MEMORY_PREFIX + memoryId;
        try {
            String json = messagesToJson(messages);
            redisTemplate.opsForValue().set(key, json, AgentConstants.MEMORY_TTL_HOURS, TimeUnit.HOURS);
            log.debug("更新Redis记忆, memoryId={}, messageCount={}", memoryId, messages.size());
        } catch (Exception e) {
            log.error("更新Redis记忆失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = AgentConstants.MEMORY_PREFIX + memoryId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.info("删除Redis记忆, memoryId={}, deleted={}", memoryId, Boolean.TRUE.equals(deleted));
        } catch (Exception e) {
            log.error("删除Redis记忆失败, memoryId={}", memoryId, e);
        }
    }
}

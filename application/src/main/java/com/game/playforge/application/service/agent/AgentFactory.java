package com.game.playforge.application.service.agent;

import com.game.playforge.common.constant.AgentConstants;
import com.game.playforge.common.enums.ModelProvider;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.repository.AgentSkillRepository;
import com.game.playforge.infrastructure.external.ai.ModelProviderRegistry;
import com.game.playforge.infrastructure.external.ai.RedisChatMemoryStore;
import com.game.playforge.infrastructure.external.ai.SystemPromptResolver;
import com.game.playforge.infrastructure.external.ai.ToolRegistry;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent工厂
 * <p>
 * 根据Agent定义动态构建LangChain4J AiService代理实例，
 * 组装模型、记忆、提示词和工具。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final ModelProviderRegistry modelProviderRegistry;
    private final SystemPromptResolver systemPromptResolver;
    private final ToolRegistry toolRegistry;
    private final AgentSkillRepository agentSkillRepository;
    private final RedisChatMemoryStore redisChatMemoryStore;

    /**
     * 创建同步聊天Agent代理
     *
     * @param definition Agent定义
     * @param threadId   会话ID（用于绑定记忆）
     * @return 同步聊天代理
     */
    public AgentChatService createAgent(AgentDefinition definition, Long threadId) {
        log.info("创建同步Agent, agent={}, threadId={}", definition.getName(), threadId);

        ModelProvider provider = resolveProvider(definition.getProvider());
        ChatModel chatModel = modelProviderRegistry.getChatModel(provider);
        List<AgentSkill> skills = loadSkills(definition);
        String systemPrompt = systemPromptResolver.resolve(definition, skills);
        MessageWindowChatMemory memory = buildMemory(definition, threadId);
        List<Object> tools = collectTools(definition, skills);

        AiServices<AgentChatService> builder = AiServices.builder(AgentChatService.class)
                .chatModel(chatModel)
                .chatMemory(memory);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.systemMessageProvider(memoryId -> systemPrompt);
        }

        if (!tools.isEmpty()) {
            builder.tools(tools);
        }

        log.info("同步Agent创建完成, agent={}, threadId={}, toolCount={}",
                definition.getName(), threadId, tools.size());
        return builder.build();
    }

    /**
     * 创建流式聊天Agent代理
     *
     * @param definition Agent定义
     * @param threadId   会话ID（用于绑定记忆）
     * @return 流式聊天代理
     */
    public AgentStreamingChatService createStreamingAgent(AgentDefinition definition, Long threadId) {
        log.info("创建流式Agent, agent={}, threadId={}", definition.getName(), threadId);

        ModelProvider provider = resolveProvider(definition.getProvider());
        StreamingChatModel streamingModel = modelProviderRegistry.getStreamingChatModel(provider);
        List<AgentSkill> skills = loadSkills(definition);
        String systemPrompt = systemPromptResolver.resolve(definition, skills);
        MessageWindowChatMemory memory = buildMemory(definition, threadId);
        List<Object> tools = collectTools(definition, skills);

        AiServices<AgentStreamingChatService> builder = AiServices.builder(AgentStreamingChatService.class)
                .streamingChatModel(streamingModel)
                .chatMemory(memory);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.systemMessageProvider(memoryId -> systemPrompt);
        }

        if (!tools.isEmpty()) {
            builder.tools(tools);
        }

        log.info("流式Agent创建完成, agent={}, threadId={}, toolCount={}",
                definition.getName(), threadId, tools.size());
        return builder.build();
    }

    private List<AgentSkill> loadSkills(AgentDefinition definition) {
        if (definition.getSkillIds() == null || definition.getSkillIds().isBlank()) {
            return Collections.emptyList();
        }
        List<Long> skillIds = Arrays.stream(definition.getSkillIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        return agentSkillRepository.findByIds(skillIds);
    }

    private MessageWindowChatMemory buildMemory(AgentDefinition definition, Long threadId) {
        int windowSize = definition.getMemoryWindowSize() != null
                ? definition.getMemoryWindowSize()
                : AgentConstants.DEFAULT_MEMORY_WINDOW_SIZE;
        return MessageWindowChatMemory.builder()
                .id(threadId)
                .maxMessages(windowSize)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    private List<Object> collectTools(AgentDefinition definition, List<AgentSkill> skills) {
        List<String> toolNames = new ArrayList<>();

        if (definition.getToolNames() != null && !definition.getToolNames().isBlank()) {
            toolNames.addAll(Arrays.asList(definition.getToolNames().split(",")));
        }

        for (AgentSkill skill : skills) {
            if (skill.getToolNames() != null && !skill.getToolNames().isBlank()) {
                toolNames.addAll(Arrays.asList(skill.getToolNames().split(",")));
            }
        }

        return toolRegistry.getToolBeans(toolNames.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList()));
    }

    private ModelProvider resolveProvider(String provider) {
        try {
            return ModelProvider.valueOf(provider.toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AGENT_PROVIDER_UNAVAILABLE, "不支持的模型供应商");
        }
    }
}

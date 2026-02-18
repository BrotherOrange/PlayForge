package com.game.playforge.application.service.agent;

import com.game.playforge.common.constant.AgentConstants;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.enums.ModelProvider;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.infrastructure.external.ai.AgentTypeRegistry;
import com.game.playforge.infrastructure.external.ai.ModelProviderRegistry;
import com.game.playforge.infrastructure.external.ai.RedisChatMemoryStore;
import com.game.playforge.infrastructure.external.ai.SkillRegistry;
import com.game.playforge.infrastructure.external.ai.SkillRegistry.SkillDescriptor;
import com.game.playforge.infrastructure.external.ai.SystemPromptResolver;
import com.game.playforge.infrastructure.external.ai.ToolRegistry;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Agent工厂
 * <p>
 * 根据Agent定义动态构建LangChain4J AiService代理实例，
 * 组装模型、记忆、提示词和工具。
 * 技能采用目录模式：system prompt中只注入轻量级目录，
 * LLM通过调用loadSkill工具按需加载完整内容。
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
    private final SkillRegistry skillRegistry;
    private final AgentTypeRegistry agentTypeRegistry;
    private final RedisChatMemoryStore redisChatMemoryStore;

    /**
     * 创建同步聊天Agent代理
     *
     * @param definition Agent定义
     * @param threadId   会话ID（用于绑定记忆）
     * @param userId     用户ID（用于判断是否注入子Agent工具，null表示不注入）
     * @param extraTools 额外工具实例（如SubAgentTool）
     * @return 同步聊天代理
     */
    public AgentChatService createAgent(AgentDefinition definition, Long threadId,
                                        Long userId, List<Object> extraTools) {
        return createAgent(definition, threadId, userId, extraTools, null);
    }

    /**
     * 创建同步聊天Agent代理（带响应拦截器）
     * <p>
     * responseInterceptor 会在每一轮LLM调用返回时被回调，
     * 包括工具调用链中的中间轮次，可用于实时推送每轮回答。
     * </p>
     */
    public AgentChatService createAgent(AgentDefinition definition, Long threadId,
                                        Long userId, List<Object> extraTools,
                                        Consumer<ChatResponse> responseInterceptor) {
        log.info("创建同步Agent, agent={}, threadId={}", definition.getName(), threadId);

        ModelProvider provider = resolveProvider(definition.getProvider());
        ChatModel chatModel = modelProviderRegistry.getChatModel(provider);
        ChatRequestParameters scopedParameters = buildScopedRequestParameters(definition, provider);
        Map<Object, Object> requestAttributes = buildRequestAttributes(threadId);
        ChatModel effectiveChatModel = new AgentScopedChatModel(
                chatModel, scopedParameters, requestAttributes, responseInterceptor);
        List<String> skillNameList = parseSkillNames(definition);
        boolean hasSubAgentTool = hasSubAgentTool(definition);
        String additionalContext = buildAdditionalContext(skillNameList, hasSubAgentTool);
        String systemPrompt = systemPromptResolver.resolve(definition, additionalContext);
        MessageWindowChatMemory memory = buildMemory(definition, threadId);
        List<Object> tools = collectTools(definition, skillNameList, extraTools);

        AiServices<AgentChatService> builder = AiServices.builder(AgentChatService.class)
                .chatModel(effectiveChatModel)
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
     * @param userId     用户ID（用于判断是否注入子Agent工具，null表示不注入）
     * @param extraTools 额外工具实例（如SubAgentTool）
     * @return 流式聊天代理
     */
    public AgentStreamingChatService createStreamingAgent(AgentDefinition definition, Long threadId,
                                                          Long userId, List<Object> extraTools) {
        log.info("创建流式Agent, agent={}, threadId={}", definition.getName(), threadId);

        ModelProvider provider = resolveProvider(definition.getProvider());
        StreamingChatModel streamingModel = modelProviderRegistry.getStreamingChatModel(provider);
        ChatRequestParameters scopedParameters = buildScopedRequestParameters(definition, provider);
        Map<Object, Object> requestAttributes = buildRequestAttributes(threadId);
        StreamingChatModel effectiveStreamingModel =
                new AgentScopedStreamingChatModel(streamingModel, scopedParameters, requestAttributes);
        List<String> skillNameList = parseSkillNames(definition);
        boolean hasSubAgentTool = hasSubAgentTool(definition);
        String additionalContext = buildAdditionalContext(skillNameList, hasSubAgentTool);
        String systemPrompt = systemPromptResolver.resolve(definition, additionalContext);
        MessageWindowChatMemory memory = buildMemory(definition, threadId);
        List<Object> tools = collectTools(definition, skillNameList, extraTools);

        AiServices<AgentStreamingChatService> builder = AiServices.builder(AgentStreamingChatService.class)
                .streamingChatModel(effectiveStreamingModel)
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

    private boolean hasSubAgentTool(AgentDefinition definition) {
        if (definition.getToolNames() == null || definition.getToolNames().isBlank()) {
            return false;
        }
        return Arrays.stream(definition.getToolNames().split(","))
                .map(String::trim)
                .anyMatch("subAgentTool"::equals);
    }

    private String buildAdditionalContext(List<String> skillNameList, boolean hasSubAgentTool) {
        StringBuilder sb = new StringBuilder();

        // 技能目录
        String skillCatalog = skillRegistry.getSkillCatalog(skillNameList);
        if (skillCatalog != null && !skillCatalog.isBlank()) {
            sb.append(skillCatalog);
        }

        // Agent类型目录（仅Lead Agent有subAgentTool时注入）
        if (hasSubAgentTool) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(agentTypeRegistry.getTypeCatalog());
        }

        return sb.toString();
    }

    private List<String> parseSkillNames(AgentDefinition definition) {
        if (definition.getSkillNames() == null || definition.getSkillNames().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(definition.getSkillNames().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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

    private List<Object> collectTools(AgentDefinition definition, List<String> skillNameList,
                                      List<Object> extraTools) {
        Set<String> toolNames = new LinkedHashSet<>();

        // 1. Agent自身配置的工具
        if (definition.getToolNames() != null && !definition.getToolNames().isBlank()) {
            Arrays.stream(definition.getToolNames().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(toolNames::add);
        }

        // 2. 移除subAgentTool（不在ToolRegistry中，由extraTools提供）
        toolNames.remove("subAgentTool");

        // 3. 如果有技能配置，自动加入 skillLoaderTool
        List<SkillDescriptor> skills = skillRegistry.getSkills(skillNameList);
        if (!skills.isEmpty()) {
            toolNames.add("skillLoaderTool");
        }

        // 4. 收集各技能引用的外部工具
        for (SkillDescriptor skill : skills) {
            toolNames.addAll(skill.toolNames());
        }

        // 5. 从 ToolRegistry 获取工具 Bean
        List<Object> tools = new ArrayList<>(toolRegistry.getToolBeans(new ArrayList<>(toolNames)));

        // 6. 加入技能自身的 @Tool Bean（selfToolBean）
        for (SkillDescriptor skill : skills) {
            if (skill.selfToolBean() != null) {
                tools.add(skill.selfToolBean());
            }
        }

        // 7. 加入额外工具（如SubAgentTool实例）
        if (extraTools != null) {
            tools.addAll(extraTools);
        }

        return tools;
    }

    private ChatRequestParameters buildScopedRequestParameters(AgentDefinition definition, ModelProvider provider) {
        var builder = ChatRequestParameters.builder()
                .modelName(definition.getModelName());

        if (definition.getTemperature() != null) {
            builder.temperature(definition.getTemperature());
        }

        Integer resolvedMaxTokens = resolveMaxOutputTokens(definition, provider);
        // GPT-5.x requires max_completion_tokens; keep OpenAI token cap at model-level custom config
        // to avoid sending deprecated max_tokens.
        if (resolvedMaxTokens != null && provider != ModelProvider.OPENAI) {
            builder.maxOutputTokens(resolvedMaxTokens);
        }

        return builder.build();
    }

    private Integer resolveMaxOutputTokens(AgentDefinition definition, ModelProvider provider) {
        Integer configured = definition.getMaxTokens();
        if (configured != null && configured <= 0) {
            log.warn("检测到非法maxTokens, agent={}, maxTokens={}, 将忽略并使用默认值",
                    definition.getName(), configured);
            configured = null;
        }

        if (provider == ModelProvider.ANTHROPIC) {
            int fallback = AgentConstants.DEFAULT_MAX_OUTPUT_TOKENS;
            int desired = configured != null ? configured : fallback;
            int capped = Math.min(desired, AgentConstants.ANTHROPIC_SAFE_MAX_OUTPUT_TOKENS);
            if (desired != capped) {
                log.info("Anthropic输出Token上限已收敛, agent={}, desired={}, capped={}",
                        definition.getName(), desired, capped);
            }
            return capped;
        }

        return configured;
    }

    private Map<Object, Object> buildRequestAttributes(Long threadId) {
        Map<Object, Object> attributes = new HashMap<>();
        String traceId = MDC.get(AuthConstants.TRACE_ID_MDC_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = "thread-" + threadId;
        }
        attributes.put(AuthConstants.TRACE_ID_MDC_KEY, traceId);
        attributes.put("threadId", threadId);
        return attributes;
    }

    private ModelProvider resolveProvider(String provider) {
        try {
            return ModelProvider.valueOf(provider.toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AGENT_PROVIDER_UNAVAILABLE, "不支持的模型供应商");
        }
    }
}

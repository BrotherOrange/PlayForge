package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统提示词解析器
 * <p>
 * 根据Agent定义解析最终的系统提示词，支持内联文本和文件引用两种方式，
 * 并将关联技能的提示词片段拼接到基础提示词后面。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemPromptResolver {

    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

    /**
     * 解析Agent的完整系统提示词
     *
     * @param agent  Agent定义
     * @param skills 关联的技能列表
     * @return 完整的系统提示词
     */
    public String resolve(AgentDefinition agent, List<AgentSkill> skills) {
        // 优先使用内联提示词
        String basePrompt;
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            basePrompt = agent.getSystemPrompt();
            log.debug("使用内联系统提示词, agent={}", agent.getName());
        } else if (agent.getSystemPromptRef() != null && !agent.getSystemPromptRef().isBlank()) {
            basePrompt = loadPromptFromFile(agent.getSystemPromptRef());
            log.debug("使用文件系统提示词, agent={}, ref={}", agent.getName(), agent.getSystemPromptRef());
        } else {
            basePrompt = "";
            log.warn("Agent未配置系统提示词, agent={}", agent.getName());
        }

        // 拼接技能提示词片段
        if (skills != null && !skills.isEmpty()) {
            StringBuilder sb = new StringBuilder(basePrompt);
            for (AgentSkill skill : skills) {
                if (skill.getPromptFragment() != null && !skill.getPromptFragment().isBlank()) {
                    sb.append("\n\n").append(skill.getPromptFragment());
                }
            }
            return sb.toString();
        }

        return basePrompt;
    }

    private String loadPromptFromFile(String ref) {
        return promptCache.computeIfAbsent(ref, key -> {
            String location = "classpath:prompts/" + key;
            try (InputStream is = resourceLoader.getResource(location).getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.info("加载提示词文件成功, ref={}", key);
                return content;
            } catch (IOException e) {
                log.error("加载提示词文件失败, ref={}", key, e);
                return "";
            }
        });
    }
}

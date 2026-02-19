package com.game.playforge.infrastructure.external.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Agent类型注册中心
 * <p>
 * 管理预定义的Agent类型（8种游戏策划角色 + default），
 * 提供类型目录供Lead Agent的system prompt使用。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTypeRegistry {

    private final ResourceLoader resourceLoader;
    private final Map<String, AgentTypeDescriptor> types = new LinkedHashMap<>();

    public record AgentTypeDescriptor(
            String name,
            String description,
            String promptContent,
            List<String> defaultTools,
            List<String> defaultSkills
    ) {}

    @PostConstruct
    public void init() {
        register("leadDesigner", "Lead Designer — vision controller, pipeline orchestrator, global supervisor",
                "agents/lead-designer.txt", List.of(), List.of());
        register("systemDesigner", "Systems Designer (Phase 2) — gameplay loops, progression, economy mechanisms",
                "agents/system-designer.txt", List.of(), List.of());
        register("balancingDesigner", "Balancing Designer (Phase 3) — formulas, curves, probability, economy tuning",
                "agents/balancing-designer.txt", List.of(), List.of());
        register("levelDesigner", "Level Designer (Phase 2) — level topology, encounter pacing, spatial flow",
                "agents/level-designer.txt", List.of(), List.of());
        register("narrativeDesigner", "Narrative Designer (Phase 3) — story, characters, world-building, dialogue",
                "agents/narrative-designer.txt", List.of(), List.of());
        register("combatDesigner", "Combat Designer (Phase 2) — combat architecture, skills, enemy AI, combat feel",
                "agents/combat-designer.txt", List.of(), List.of());
        register("technicalDesigner", "Technical Designer (Phase X) — technical gateway, feasibility review, implementation planning",
                "agents/technical-designer.txt", List.of(), List.of());
        register("juniorDesigner", "Execution Planner (Phase 4) — engine-ready data output, config tables, specifications",
                "agents/junior-designer.txt", List.of(), List.of());
        register("default", "Default agent — blank agent with no preset prompt",
                null, List.of(), List.of());

        log.info("AgentTypeRegistry初始化完成, 已注册类型: {}", types.keySet());
    }

    private void register(String name, String description, String promptRef,
                          List<String> defaultTools, List<String> defaultSkills) {
        String promptContent = promptRef != null ? loadPromptFile(promptRef) : "";
        types.put(name, new AgentTypeDescriptor(name, description, promptContent, defaultTools, defaultSkills));
        log.info("注册AgentType: {} - {}", name, description);
    }

    private String loadPromptFile(String ref) {
        String location = "classpath:prompts/" + ref;
        try (InputStream is = resourceLoader.getResource(location).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载AgentType提示词文件失败, ref={}", ref, e);
            return "";
        }
    }

    /**
     * 获取指定类型的描述符
     */
    public AgentTypeDescriptor getType(String name) {
        return types.get(name);
    }

    /**
     * 获取所有已注册的类型名称
     */
    public Set<String> getRegisteredTypeNames() {
        return Collections.unmodifiableSet(types.keySet());
    }

    /**
     * 生成类型目录字符串（注入Lead Agent的system prompt）
     */
    public String getTypeCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("<available-agent-types>\n");
        sb.append("You can create sub-agents of the following types using the createSubAgent tool:\n\n");
        for (AgentTypeDescriptor type : types.values()) {
            if ("leadDesigner".equals(type.name())) {
                continue; // Lead Agent不出现在可创建列表中
            }
            sb.append("- **").append(type.name()).append("**: ").append(type.description()).append("\n");
        }
        sb.append("</available-agent-types>");
        return sb.toString();
    }
}

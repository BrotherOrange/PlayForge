package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.common.annotation.Skill;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能注册中心
 * <p>
 * 在所有单例Bean创建完成后，扫描Spring容器中所有 {@link Skill} 注解的Bean，
 * 构建技能目录并提供按需查询能力。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final ResourceLoader resourceLoader;
    private final Map<String, SkillDescriptor> skills = new LinkedHashMap<>();

    public record SkillDescriptor(
            String name,
            String description,
            String fullContent,
            List<String> toolNames,
            Object selfToolBean
    ) {}

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Skill.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();
            Skill annotation = bean.getClass().getAnnotation(Skill.class);
            if (annotation == null) {
                continue;
            }

            String name = annotation.name().isEmpty() ? beanName : annotation.name();
            String fullContent = resolveContent(annotation);
            Object selfToolBean = hasToolAnnotation(bean.getClass()) ? bean : null;
            List<String> toolNames = List.of(annotation.toolNames());

            skills.put(name, new SkillDescriptor(name, annotation.description(), fullContent, toolNames, selfToolBean));
            log.info("注册Skill: {} - {}", name, annotation.description());
        }
        log.info("SkillRegistry初始化完成, 已注册技能: {}", skills.keySet());
    }

    /**
     * 获取技能目录字符串（注入 system prompt）
     */
    public String getSkillCatalog(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        List<SkillDescriptor> matched = names.stream()
                .map(skills::get)
                .filter(Objects::nonNull)
                .toList();
        if (matched.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用技能\n");
        sb.append("当你需要以下某个领域的专业知识时，请调用 loadSkill 工具加载对应技能的完整指南。\n\n");
        for (SkillDescriptor skill : matched) {
            sb.append("- **").append(skill.name()).append("**: ").append(skill.description()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取技能完整内容（给 SkillLoaderTool 调用）
     */
    public String getSkillContent(String name) {
        SkillDescriptor descriptor = skills.get(name);
        return descriptor != null ? descriptor.fullContent() : null;
    }

    /**
     * 获取指定名称的技能描述符列表
     */
    public List<SkillDescriptor> getSkills(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        return names.stream()
                .map(skills::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册的技能名称
     */
    public Set<String> getRegisteredSkillNames() {
        return Collections.unmodifiableSet(skills.keySet());
    }

    private String resolveContent(Skill annotation) {
        if (!annotation.content().isEmpty()) {
            return annotation.content();
        }
        if (!annotation.contentRef().isEmpty()) {
            return loadContentFromFile(annotation.contentRef());
        }
        return "";
    }

    private String loadContentFromFile(String ref) {
        String location = "classpath:prompts/" + ref;
        try (InputStream is = resourceLoader.getResource(location).getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("加载Skill内容文件成功, ref={}", ref);
            return content;
        } catch (IOException e) {
            log.error("加载Skill内容文件失败, ref={}", ref, e);
            return "";
        }
    }

    private boolean hasToolAnnotation(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}

package com.game.playforge.infrastructure.external.ai;

import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI工具注册中心
 * <p>
 * 启动时扫描Spring容器中所有包含 {@link Tool} 注解方法的Bean，
 * 提供按名称查找工具Bean的能力。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, Object> toolBeans = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            if (hasToolAnnotation(bean.getClass())) {
                toolBeans.put(beanName, bean);
                log.info("注册Tool: {}", beanName);
            }
        }
        log.info("ToolRegistry初始化完成, 已注册工具: {}", toolBeans.keySet());
    }

    private boolean hasToolAnnotation(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据名称获取工具Bean
     */
    public Object getToolBean(String name) {
        return toolBeans.get(name);
    }

    /**
     * 根据名称列表获取工具Bean列表
     */
    public List<Object> getToolBeans(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        return names.stream()
                .map(String::trim)
                .filter(toolBeans::containsKey)
                .map(toolBeans::get)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册的工具名称
     */
    public Set<String> getRegisteredToolNames() {
        return Collections.unmodifiableSet(toolBeans.keySet());
    }
}

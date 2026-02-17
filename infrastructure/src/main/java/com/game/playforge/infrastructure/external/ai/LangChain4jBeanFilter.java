package com.game.playforge.infrastructure.external.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * LangChain4J Bean 过滤器
 * <p>
 * 使用 BeanFactoryPostProcessor（而非 BeanDefinitionRegistryPostProcessor），
 * 确保在 ConfigurationClassPostProcessor 处理完所有自动配置之后运行。
 * 检查各 LLM 供应商的 API Key，如果为空则移除其 Bean 定义，
 * 防止 Spring 在 preInstantiateSingletons 阶段创建 Bean 失败。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class LangChain4jBeanFilter implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            log.warn("BeanFactory不支持BeanDefinitionRegistry, 跳过LangChain4J Bean过滤");
            return;
        }

        removeIfKeyBlank(registry, "langchain4j.open-ai.chat-model.api-key",
                "openAiChatModel", "openAiStreamingChatModel");
        removeIfKeyBlank(registry, "langchain4j.anthropic.chat-model.api-key",
                "anthropicChatModel", "anthropicStreamingChatModel");
        removeIfKeyBlank(registry, "langchain4j.google-ai-gemini.chat-model.api-key",
                "googleAiGeminiChatModel", "googleAiGeminiStreamingChatModel");
    }

    private void removeIfKeyBlank(BeanDefinitionRegistry registry, String property, String... beanNames) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            for (String beanName : beanNames) {
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                    log.info("移除Bean定义 '{}': API Key未配置 ({})", beanName, property);
                }
            }
        }
    }
}

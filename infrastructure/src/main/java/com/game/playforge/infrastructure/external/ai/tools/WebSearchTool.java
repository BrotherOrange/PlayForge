package com.game.playforge.infrastructure.external.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 联网搜索工具
 * <p>
 * 基于Tavily Search API，提供互联网搜索能力，供Agent调用。
 * 仅在配置了API Key时激活。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component("webSearchTool")
@ConditionalOnProperty(prefix = "tavily", name = "api-key")
public class WebSearchTool {

    private final WebSearchEngine searchEngine;

    public WebSearchTool(@Value("${tavily.api-key}") String apiKey) {
        this.searchEngine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .build();
        log.info("WebSearchTool初始化完成 (Tavily Search)");
    }

    @Tool("Search the internet for up-to-date information. Use this tool when you need real-time data, latest news, technical documentation, or any information that may be outdated in your training data.")
    public String searchWeb(@P("Search query keywords") String query) {
        log.info("执行联网搜索: {}", query);
        try {
            WebSearchResults results = searchEngine.search(query);
            List<WebSearchOrganicResult> organicResults = results.results();

            if (organicResults == null || organicResults.isEmpty()) {
                return "未找到相关搜索结果。";
            }

            String formatted = organicResults.stream()
                    .limit(5)
                    .map(r -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("**").append(r.title()).append("**\n");
                        sb.append("URL: ").append(r.url()).append("\n");
                        if (r.snippet() != null) {
                            sb.append(r.snippet());
                        }
                        return sb.toString();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("搜索完成, 返回{}条结果", Math.min(organicResults.size(), 5));
            return formatted;
        } catch (Exception e) {
            log.error("联网搜索失败: {}", e.getMessage(), e);
            return "搜索失败: " + e.getMessage();
        }
    }
}

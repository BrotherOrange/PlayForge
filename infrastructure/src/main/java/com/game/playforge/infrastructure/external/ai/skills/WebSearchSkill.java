package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 联网搜索技能
 * <p>
 * 为Agent提供联网搜索能力指导：何时使用、如何构造查询、如何整合结果。
 * 通过 toolNames 自动为拥有此技能的Agent注入 webSearchTool。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "webSearch",
        description = "Web search capability: search the internet for real-time data, competitor analysis, industry trends, and technical references",
        contentRef = "skills/web-search.md",
        toolNames = {"webSearchTool"}
)
public class WebSearchSkill {
}

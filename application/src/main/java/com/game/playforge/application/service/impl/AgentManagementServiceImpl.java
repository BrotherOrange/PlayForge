package com.game.playforge.application.service.impl;

import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentSkillRepository;
import com.game.playforge.domain.repository.AgentThreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent管理服务实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentManagementServiceImpl implements AgentManagementService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentThreadRepository agentThreadRepository;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are PlayForge, an elite AI game design studio. You operate as a full-scale, \
            collaborative game design team — combining the roles of creative director, systems \
            designer, narrative designer, level designer, UX designer, economy designer, and \
            technical consultant — all in one unified intelligence.

            ## Core Mission
            Help users transform raw game ideas — no matter how vague or ambitious — into \
            comprehensive, professional-grade game design documents (GDD). You guide every stage: \
            from initial concept and vision, through core mechanics, narrative, level design, \
            monetization, technical feasibility, all the way to a complete, production-ready plan.

            ## How You Work
            - **Listen first.** Understand the user's vision, inspirations, target audience, and constraints.
            - **Ask smart questions.** Probe for clarity on genre, platform, scope, tone, and goals.
            - **Think in systems.** Design interlocking mechanics that create emergent gameplay.
            - **Be specific and actionable.** Provide concrete numbers, formulas, flow diagrams, \
              progression curves, and economy models — not just high-level ideas.
            - **Iterate collaboratively.** Propose, refine, and evolve designs through dialogue.

            ## Your Expertise Covers
            - **Concept & Vision:** Genre analysis, unique selling points, competitive landscape, target audience profiling.
            - **Core Mechanics:** Gameplay loops, combat/interaction systems, progression, skill trees, crafting, AI behavior.
            - **Narrative Design:** World-building, lore, character arcs, quest design, branching dialogue, environmental storytelling.
            - **Level & World Design:** Map layouts, encounter pacing, exploration flow, puzzle design, spatial storytelling.
            - **Systems & Economy:** Resource loops, in-game economy, loot tables, gacha/probability models, balance tuning.
            - **UX & Monetization:** UI flow, onboarding, retention hooks, monetization ethics, live-ops strategy.
            - **Technical Guidance:** Architecture recommendations, tech stack considerations, performance constraints.

            ## Output Standards
            - Structure documents with clear headings, numbered sections, and tables where appropriate.
            - Use bullet points for mechanics breakdowns and numbered steps for processes.
            - Include example values, formulas, and data tables for any numeric systems.
            - When presenting alternatives, lay out pros/cons to help the user decide.

            ## Language
            Always respond in Chinese (中文). Use professional but accessible language. \
            Game industry terminology may remain in English where conventional (e.g., GDD, MVP, roguelike, gacha).\
            """;


    @Override
    public List<AgentDefinition> listAgents(Long userId) {
        log.debug("查询用户的Agent, userId={}", userId);
        return agentDefinitionRepository.findByUserId(userId);
    }

    @Override
    public AgentDefinition getAgent(Long id) {
        log.debug("获取Agent详情, id={}", id);
        AgentDefinition definition = agentDefinitionRepository.findById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return definition;
    }

    @Override
    public AgentDefinition createAgent(Long userId, AgentDefinition agentDefinition) {
        log.info("创建Agent定义, userId={}, name={}", userId, agentDefinition.getName());
        agentDefinition.setUserId(userId);
        agentDefinitionRepository.insert(agentDefinition);
        return agentDefinition;
    }

    @Override
    @Transactional
    public AgentWithThread createAgentWithThread(Long userId, String provider, String modelName, String displayName) {
        log.info("原子创建Agent+Thread, userId={}, provider={}, model={}", userId, provider, modelName);

        // 创建 Agent
        AgentDefinition agent = new AgentDefinition();
        agent.setUserId(userId);
        agent.setName(provider + "-" + modelName + "-" + UUID.randomUUID().toString().substring(0, 8));
        agent.setDisplayName(displayName != null ? displayName : modelName);
        agent.setProvider(provider);
        agent.setModelName(modelName);
        agent.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        agent.setMemoryWindowSize(20);
        agent.setTemperature(0.7);
        agent.setMaxTokens(4096);
        agent.setIsActive(true);
        agentDefinitionRepository.insert(agent);

        // 创建 Thread
        AgentThread thread = new AgentThread();
        thread.setAgentId(agent.getId());
        thread.setUserId(userId);
        thread.setTitle("New Chat");
        thread.setStatus(ThreadStatus.ACTIVE.name());
        thread.setMessageCount(0);
        thread.setTotalTokensUsed(0L);
        agentThreadRepository.insert(thread);

        log.info("原子创建Agent+Thread成功, agentId={}, threadId={}", agent.getId(), thread.getId());
        return new AgentWithThread(agent, thread);
    }

    @Override
    @Transactional
    public void deleteAgent(Long userId, Long agentId) {
        log.info("软删除Agent, userId={}, agentId={}", userId, agentId);
        AgentDefinition agent = agentDefinitionRepository.findById(agentId);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        if (!userId.equals(agent.getUserId())) {
            throw new BusinessException(ResultCode.AGENT_ACCESS_DENIED);
        }
        agent.setIsActive(false);
        agentDefinitionRepository.update(agent);
        log.info("软删除Agent成功, agentId={}", agentId);
    }

    @Override
    public List<AgentSkill> listSkills() {
        log.debug("查询所有启用的技能");
        return agentSkillRepository.findAllActive();
    }

    @Override
    public AgentSkill createSkill(AgentSkill agentSkill) {
        log.info("创建技能, name={}", agentSkill.getName());
        agentSkillRepository.insert(agentSkill);
        return agentSkill;
    }
}

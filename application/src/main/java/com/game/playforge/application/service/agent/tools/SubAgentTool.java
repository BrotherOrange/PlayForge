package com.game.playforge.application.service.agent.tools;

import com.game.playforge.application.dto.AgentStreamEvent;
import com.game.playforge.application.service.agent.SubAgentService;
import com.game.playforge.application.service.agent.SubAgentService.SubAgentInfo;
import com.game.playforge.infrastructure.external.ai.AsyncTaskManager;
import com.game.playforge.infrastructure.external.ai.AsyncTaskManager.TaskResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 子Agent管理工具
 * <p>
 * 每会话实例（非Spring Bean，不注册到ToolRegistry），
 * 在AgentChatAppServiceImpl中按需创建并注入到Lead Agent的工具列表。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
public class SubAgentTool {

    private final Long userId;
    private final Long parentThreadId;
    private final SubAgentService subAgentService;
    private final AsyncTaskManager taskManager;
    private final Consumer<AgentStreamEvent> progressCallback;

    public SubAgentTool(Long userId, Long parentThreadId,
                        SubAgentService subAgentService, AsyncTaskManager taskManager,
                        Consumer<AgentStreamEvent> progressCallback) {
        this.userId = userId;
        this.parentThreadId = parentThreadId;
        this.subAgentService = subAgentService;
        this.taskManager = taskManager;
        this.progressCallback = progressCallback;
    }

    private void emitProgress(String content) {
        if (progressCallback != null) {
            progressCallback.accept(AgentStreamEvent.progress(content));
        }
    }

    @Tool("Create a specialized sub-agent. Returns agent info including a NUMERIC threadId for task dispatch. " +
          "IMPORTANT: You MUST use the exact numeric threadId returned (e.g. 2024214713863147522) when calling " +
          "dispatchTask or destroySubAgent. Do NOT invent your own IDs. " +
          "Available types: systemDesigner, balancingDesigner, levelDesigner, narrativeDesigner, " +
          "combatDesigner, technicalDesigner, juniorDesigner, default.")
    public String createSubAgent(
            @P("Agent type (e.g. systemDesigner, combatDesigner)") String type,
            @P("Brief task description for this agent") String task,
            @P("Additional system prompt instructions (optional, can be empty)") String additionalPrompt,
            @P("Additional tool names, comma-separated (optional, can be empty)") String additionalTools) {
        try {
            SubAgentInfo info = subAgentService.createSubAgent(
                    userId, parentThreadId, type, task,
                    normalizeEmpty(additionalPrompt),
                    normalizeEmpty(additionalTools));

            emitProgress(String.format("创建子Agent: %s (%s)", info.displayName(), info.type()));

            return String.format("""
                    Sub-agent created successfully:
                    - Name: %s
                    - threadId: %s  ← USE THIS EXACT NUMERIC ID for dispatchTask/destroySubAgent
                    - Type: %s
                    - Role: %s""",
                    info.agentName(), info.threadId(), info.type(),
                    info.displayName());
        } catch (Exception e) {
            log.error("创建子Agent失败", e);
            return "Failed to create sub-agent: " + e.getMessage();
        }
    }

    @Tool("Dispatch a task to a sub-agent asynchronously. The agent works in background. " +
          "Returns immediately. Use awaitResults to collect the response later. " +
          "IMPORTANT: threadId MUST be the exact numeric ID returned by createSubAgent (e.g. 2024214713863147522).")
    public String dispatchTask(
            @P("The exact numeric threadId returned by createSubAgent (e.g. 2024214713863147522). Do NOT make up IDs.") String threadId,
            @P("Task message to send to the agent") String message) {
        try {
            Long threadIdLong = Long.parseLong(threadId);
            SubAgentInfo agent = resolveTeamAgent(threadIdLong);

            taskManager.dispatch(threadId, agent.agentName(),
                    () -> subAgentService.chat(userId, parentThreadId, threadIdLong, message));

            emitProgress(String.format("分发任务给 %s，当前 %d 个Agent在后台工作",
                    agent.agentName(), taskManager.pendingCount()));

            return String.format("Task dispatched to %s (threadId: %s). %d agent(s) now working in background.",
                    agent.agentName(), threadId, taskManager.pendingCount());
        } catch (Exception e) {
            log.error("分发任务失败, threadId={}", threadId, e);
            return "Failed to dispatch task: " + e.getMessage();
        }
    }

    @Tool("Wait for background sub-agent results. Blocks until at least one agent completes or timeout. " +
          "Returns completed results. Call again if more agents are still pending.")
    public String awaitResults(
            @P("Maximum wait time in seconds (recommended: 30-120)") int timeoutSeconds) {
        try {
            emitProgress(String.format("等待子Agent结果（超时 %d 秒）...", timeoutSeconds));

            List<TaskResult> results = taskManager.awaitResults(timeoutSeconds);

            if (results.isEmpty()) {
                int pending = taskManager.pendingCount();
                if (pending == 0) {
                    return "No pending tasks and no results available.";
                }
                return String.format("Timeout after %d seconds. %d agent(s) still working.", timeoutSeconds, pending);
            }

            String agentSummary = results.stream()
                    .map(r -> r.agentName() + (r.isError() ? "(失败)" : ""))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            emitProgress(String.format("收到 %d 个子Agent的结果: %s", results.size(), agentSummary));

            StringBuilder sb = new StringBuilder();
            for (TaskResult result : results) {
                sb.append(String.format("=== Agent %s (threadId: %s) %s ===\n",
                        result.agentName(), result.threadId(),
                        result.isError() ? "FAILED" : "completed"));
                sb.append(result.result()).append("\n\n");
            }

            int remaining = taskManager.pendingCount();
            if (remaining > 0) {
                sb.append(String.format("[%d agent(s) still pending]", remaining));
            } else {
                sb.append("[All agents completed]");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("等待任务结果失败", e);
            return "Error waiting for results: " + e.getMessage();
        }
    }

    @Tool("Destroy a sub-agent and cancel its pending task. Use after collecting results to free resources. " +
          "threadId MUST be the exact numeric ID from createSubAgent.")
    public String destroySubAgent(
            @P("The exact numeric threadId returned by createSubAgent (e.g. 2024214713863147522)") String threadId) {
        try {
            Long threadIdLong = Long.parseLong(threadId);
            SubAgentInfo agent = resolveTeamAgent(threadIdLong);
            taskManager.cancel(threadId);
            subAgentService.destroySubAgent(userId, parentThreadId, threadIdLong);
            emitProgress(String.format("销毁子Agent: %s", agent.agentName()));
            return String.format("Sub-agent (threadId: %s) destroyed successfully.", threadId);
        } catch (Exception e) {
            log.error("销毁子Agent失败, threadId={}", threadId, e);
            return "Failed to destroy sub-agent: " + e.getMessage();
        }
    }

    @Tool("List all sub-agents in your team with their current status and thread IDs.")
    public String listTeamAgents() {
        try {
            List<SubAgentInfo> agents = subAgentService.listTeamAgents(userId, parentThreadId);
            Map<String, String> pendingAgents = taskManager.getPendingAgents();

            if (agents.isEmpty()) {
                return "No sub-agents in team. Use createSubAgent to create specialists.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Team agents:\n");
            for (SubAgentInfo agent : agents) {
                String status = pendingAgents.containsKey(String.valueOf(agent.threadId()))
                        ? "WORKING" : "IDLE";
                sb.append(String.format("- %s (threadId: %s, type: %s, status: %s)\n",
                        agent.agentName(), agent.threadId(), agent.type(), status));
            }
            long working = agents.stream()
                    .filter(a -> pendingAgents.containsKey(String.valueOf(a.threadId())))
                    .count();
            sb.append(String.format("\nTotal: %d agent(s), %d working, %d idle",
                    agents.size(), working, agents.size() - (int) working));

            return sb.toString();
        } catch (Exception e) {
            log.error("列出团队Agent失败", e);
            return "Failed to list team agents: " + e.getMessage();
        }
    }

    private SubAgentInfo resolveTeamAgent(Long threadId) {
        List<SubAgentInfo> agents = subAgentService.listTeamAgents(userId, parentThreadId);
        return agents.stream()
                .filter(a -> Objects.equals(a.threadId(), threadId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sub-agent thread not found in current team: " + threadId));
    }

    private String normalizeEmpty(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}

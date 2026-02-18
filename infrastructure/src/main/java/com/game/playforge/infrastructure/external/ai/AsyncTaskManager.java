package com.game.playforge.infrastructure.external.ai;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 异步任务管理器
 * <p>
 * 每会话实例（非Spring Bean），基于BlockingQueue实现高效的异步任务分发与结果收集。
 * 支持"第一个完成即返回"的等待语义。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
public class AsyncTaskManager {

    public record TaskResult(String threadId, String agentName, String result, boolean isError) {}

    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final Map<String, String> agentNames = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<TaskResult> completed = new LinkedBlockingQueue<>();
    private final ExecutorService executor;

    public AsyncTaskManager() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 分发任务到后台执行
     *
     * @param threadId  子Agent的线程ID
     * @param agentName 子Agent名称
     * @param task      任务执行体
     */
    public void dispatch(String threadId, String agentName, Supplier<String> task) {
        if (pending.containsKey(threadId)) {
            throw new IllegalStateException("Task already running for threadId: " + threadId);
        }
        agentNames.put(threadId, agentName);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(task, executor);
        pending.put(threadId, future);

        future.whenComplete((result, error) -> {
            pending.remove(threadId);
            agentNames.remove(threadId);

            // 已取消的任务不推入结果队列
            if (cancelled.remove(threadId)) {
                log.info("已取消任务的回调, 跳过结果推送, threadId={}, agent={}", threadId, agentName);
                return;
            }

            if (error != null) {
                String errorMsg = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                log.error("子Agent任务执行失败, threadId={}, agent={}", threadId, agentName, error);
                completed.offer(new TaskResult(threadId, agentName, "Error: " + errorMsg, true));
            } else {
                log.info("子Agent任务执行完成, threadId={}, agent={}, resultLength={}",
                        threadId, agentName, result != null ? result.length() : 0);
                completed.offer(new TaskResult(threadId, agentName, result, false));
            }
        });

        log.info("任务已分发, threadId={}, agent={}", threadId, agentName);
    }

    /**
     * 等待后台任务完成
     * <p>
     * 先排空已完成的结果队列，如果为空则阻塞等待第一个完成的结果，
     * 然后再次排空队列以收集同时完成的其他结果。
     * </p>
     *
     * @param timeoutSeconds 最大等待秒数
     * @return 已完成的任务结果列表（可能为空表示超时）
     */
    public List<TaskResult> awaitResults(int timeoutSeconds) {
        List<TaskResult> results = new ArrayList<>();

        // 1. 排空已完成的结果
        completed.drainTo(results);

        // 2. 如果没有已完成的结果，阻塞等待第一个
        if (results.isEmpty()) {
            try {
                TaskResult first = completed.poll(timeoutSeconds, TimeUnit.SECONDS);
                if (first != null) {
                    results.add(first);
                    // 3. 第一个结果到达后，再排空队列
                    completed.drainTo(results);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待任务结果被中断");
            }
        }

        return results;
    }

    /**
     * 是否有待完成的任务
     */
    public boolean hasPendingTasks() {
        return !pending.isEmpty();
    }

    /**
     * 待完成任务数量
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * 获取所有待完成任务的Agent名称列表
     */
    public Map<String, String> getPendingAgents() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : agentNames.entrySet()) {
            if (pending.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 取消指定任务
     */
    public void cancel(String threadId) {
        CompletableFuture<String> future = pending.remove(threadId);
        agentNames.remove(threadId);
        if (future != null) {
            cancelled.add(threadId);
            future.cancel(true);
            log.info("任务已取消, threadId={}", threadId);
        }
    }

    /**
     * 关闭管理器，取消所有待完成任务
     */
    public void shutdown() {
        for (String threadId : new ArrayList<>(pending.keySet())) {
            cancel(threadId);
        }
        executor.shutdownNow();
        log.info("AsyncTaskManager已关闭");
    }
}

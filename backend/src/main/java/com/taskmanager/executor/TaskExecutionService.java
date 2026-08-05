package com.taskmanager.executor;

import com.taskmanager.entity.Task;
import com.taskmanager.exception.InvalidTaskStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Submits task work to the shared {@code taskExecutionPool} and tracks the
 * in-flight {@link Future} for each task so it can be cooperatively
 * cancelled. This is the ONLY component that touches the executor directly;
 * controllers and the business service layer never create threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionService {

    private final ThreadPoolTaskExecutor taskExecutionPool;
    private final TaskStateService taskStateService;

    /** taskId -> in-flight Future, so cancel() can interrupt the worker thread. */
    private final Map<Long, Future<?>> inFlight = new ConcurrentHashMap<>();

    /**
     * Submits a task for asynchronous execution. Throws
     * {@link com.taskmanager.exception.TaskQueueFullException} (via the pool's
     * rejection handler) if the pool and its queue are both saturated.
     */
    public void submit(Long taskId, String taskType, Integer durationSeconds) {
        Future<?> future = taskExecutionPool.submit(() -> runTask(taskId, taskType, durationSeconds));
        inFlight.put(taskId, future);
    }

    /**
     * Attempts to cancel a task. If the task is currently running, the worker
     * thread is interrupted; if it hasn't started yet, it is marked cancelled
     * directly. Returns once the CANCELLED status has been persisted.
     */
    public void cancel(Long taskId) {
        Future<?> future = inFlight.get(taskId);
        if (future != null) {
            future.cancel(true);
        }
        // Always go through the state service - it re-checks the DB status
        // under a row lock, so this is safe even if the future had already
        // completed a fraction of a second earlier.
        taskStateService.markCancelled(taskId);
    }

    public int getActiveWorkerCount() {
        return taskExecutionPool.getActiveCount();
    }

    public int getMaxPoolSize() {
        return taskExecutionPool.getMaxPoolSize();
    }

    public int getQueuedTaskCount() {
        return taskExecutionPool.getThreadPoolExecutor().getQueue().size();
    }

    private void runTask(Long taskId, String taskType, Integer durationSeconds) {
        long start = System.currentTimeMillis();
        try {
            taskStateService.markRunning(taskId);
        } catch (InvalidTaskStateException ex) {
            // Cancelled or otherwise moved out of PENDING before a worker picked it up.
            log.info("Task {} skipped: {}", taskId, ex.getMessage());
            inFlight.remove(taskId);
            return;
        }

        try {
            simulateWork(taskType, durationSeconds);
            long duration = System.currentTimeMillis() - start;
            taskStateService.markCompleted(taskId, duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.info("Task {} interrupted (cancelled) after {} ms", taskId, System.currentTimeMillis() - start);
            // markCancelled was already (or will be) invoked by cancel(); nothing further to persist here.
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Task {} failed after {} ms: {}", taskId, duration, ex.getMessage());
            taskStateService.markFailed(taskId, ex.getMessage(), duration);
        } finally {
            inFlight.remove(taskId);
        }
    }

    /**
     * Simulates the actual workload a real task would perform. Task types are
     * deliberately simple strings so this stays a demonstration of the
     * concurrency machinery rather than a real job runner: any type
     * containing "fail" (case-insensitive) deterministically throws, which is
     * useful both for the UI demo and for the failure-handling tests.
     */
    private void simulateWork(String taskType, Integer durationSeconds) throws InterruptedException {
        int seconds = (durationSeconds == null || durationSeconds <= 0) ? 3 : durationSeconds;
        if (taskType != null && taskType.toLowerCase().contains("fail")) {
            Thread.sleep(Math.min(seconds, 2) * 1000L);
            throw new RuntimeException("Simulated failure for task type '" + taskType + "'");
        }
        Thread.sleep(seconds * 1000L);
    }
}

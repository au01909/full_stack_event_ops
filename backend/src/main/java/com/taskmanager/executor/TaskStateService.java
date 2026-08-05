package com.taskmanager.executor;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.exception.InvalidTaskStateException;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * All task status transitions go through this class, each in its own short
 * transaction guarded by a pessimistic write lock on the row
 * ({@link TaskRepository#findByIdForUpdate}). This is what keeps a worker
 * thread finishing a task and a user's concurrent cancel request from ever
 * producing an inconsistent state: whichever transaction acquires the row
 * lock first wins, and the other observes the already-updated status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStateService {

    private final TaskRepository taskRepository;
    private final ActivityLogClient activityLogClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Task markRunning(Long taskId) {
        Task task = lockOrThrow(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidTaskStateException(
                    "Task " + taskId + " cannot start execution from state " + task.getStatus());
        }
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        Task saved = taskRepository.save(task);
        activityLogClient.record(taskId, "STARTED", Map.of());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long taskId, long durationMs) {
        Task task = lockOrThrow(taskId);
        if (task.getStatus() != TaskStatus.RUNNING) {
            // Task was cancelled (or otherwise moved on) while the worker was finishing up.
            log.info("Skipping COMPLETED transition for task {} - current state is {}", taskId, task.getStatus());
            return;
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setExecutionDurationMs(durationMs);
        task.setErrorMessage(null);
        taskRepository.save(task);
        activityLogClient.record(taskId, "COMPLETED", Map.of("durationMs", durationMs));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long taskId, String errorMessage, long durationMs) {
        Task task = lockOrThrow(taskId);
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.info("Skipping FAILED transition for task {} - current state is {}", taskId, task.getStatus());
            return;
        }
        task.setStatus(TaskStatus.FAILED);
        task.setCompletedAt(Instant.now());
        task.setExecutionDurationMs(durationMs);
        task.setErrorMessage(errorMessage);
        taskRepository.save(task);
        activityLogClient.record(taskId, "FAILED", Map.of("durationMs", durationMs, "errorMessage", errorMessage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Task markCancelled(Long taskId) {
        Task task = lockOrThrow(taskId);
        if (task.getStatus().isTerminal()) {
            throw new InvalidTaskStateException(
                    "Task " + taskId + " cannot be cancelled - already " + task.getStatus());
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(Instant.now());
        Task saved = taskRepository.save(task);
        activityLogClient.record(taskId, "CANCELLED", Map.of());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Task resetForRetry(Long taskId) {
        Task task = lockOrThrow(taskId);
        if (task.getStatus() != TaskStatus.FAILED) {
            throw new InvalidTaskStateException(
                    "Only FAILED tasks can be retried; task " + taskId + " is " + task.getStatus());
        }
        task.setStatus(TaskStatus.PENDING);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setExecutionDurationMs(null);
        task.setErrorMessage(null);
        task.setRetryCount(task.getRetryCount() + 1);
        return taskRepository.save(task);
    }

    private Task lockOrThrow(Long taskId) {
        return taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}

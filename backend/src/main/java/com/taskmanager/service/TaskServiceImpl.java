package com.taskmanager.service;

import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.TaskStatsResponse;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.executor.TaskExecutionService;
import com.taskmanager.executor.TaskStateService;
import com.taskmanager.exception.InvalidTaskStateException;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.mapper.TaskMapper;
import com.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final TaskExecutionService taskExecutionService;
    private final TaskStateService taskStateService;

    @Override
    public TaskResponse createTask(TaskCreateRequest request) {
        Task task = taskMapper.toEntity(request);
        Task saved = taskRepository.save(task);
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(TaskStatus status, TaskPriority priority) {
        List<Task> tasks;
        if (status != null && priority != null) {
            tasks = taskRepository.findAllByStatusAndPriorityOrderByCreatedAtDesc(status, priority);
        } else if (status != null) {
            tasks = taskRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else if (priority != null) {
            tasks = taskRepository.findAllByPriorityOrderByCreatedAtDesc(priority);
        } else {
            tasks = taskRepository.findAllByOrderByCreatedAtDesc();
        }
        return tasks.stream().map(taskMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id) {
        return taskMapper.toResponse(findOrThrow(id));
    }

    @Override
    public TaskResponse executeTask(Long id) {
        Task task = findOrThrow(id);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidTaskStateException(
                    "Task " + id + " cannot be executed from state " + task.getStatus());
        }
        // Submit to the executor; the actual PENDING -> RUNNING transition is
        // persisted by the worker thread itself once it is picked up, so the
        // status returned here may still read PENDING briefly.
        taskExecutionService.submit(task.getId(), task.getType(), task.getInputDurationSeconds());
        return taskMapper.toResponse(task);
    }

    @Override
    public TaskResponse cancelTask(Long id) {
        findOrThrow(id); // 404s early with a clear message before touching the executor
        taskExecutionService.cancel(id);
        return taskMapper.toResponse(findOrThrow(id));
    }

    @Override
    public TaskResponse retryTask(Long id) {
        findOrThrow(id); // 404s early with a clear message
        Task reset = taskStateService.resetForRetry(id);
        taskExecutionService.submit(reset.getId(), reset.getType(), reset.getInputDurationSeconds());
        return taskMapper.toResponse(reset);
    }

    @Override
    public void deleteTask(Long id) {
        Task task = findOrThrow(id);
        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new InvalidTaskStateException(
                    "Task " + id + " is currently RUNNING - cancel it before deleting");
        }
        taskRepository.delete(task);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatsResponse getStats() {
        List<Task> all = taskRepository.findAll();
        long pending = all.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long running = all.stream().filter(t -> t.getStatus() == TaskStatus.RUNNING).count();
        long completed = all.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long failed = all.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();
        long cancelled = all.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count();
        return TaskStatsResponse.builder()
                .total(all.size())
                .pending(pending)
                .running(running)
                .completed(completed)
                .failed(failed)
                .cancelled(cancelled)
                .activeWorkerThreads(taskExecutionService.getActiveWorkerCount())
                .maxWorkerThreads(taskExecutionService.getConfiguredWorkerCount())
                .queuedTasks(taskExecutionService.getQueuedTaskCount())
                .build();
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}

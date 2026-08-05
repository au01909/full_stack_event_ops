package com.taskmanager.mapper;

import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public Task toEntity(TaskCreateRequest request) {
        return Task.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .priority(request.getPriority())
                .status(TaskStatus.PENDING)
                .inputDurationSeconds(request.getDurationSeconds())
                .inputData(request.getInputData())
                .retryCount(0)
                .build();
    }

    public TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .type(task.getType())
                .priority(task.getPriority())
                .status(task.getStatus())
                .durationSeconds(task.getInputDurationSeconds())
                .inputData(task.getInputData())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .executionDurationMs(task.getExecutionDurationMs())
                .errorMessage(task.getErrorMessage())
                .retryCount(task.getRetryCount())
                .version(task.getVersion())
                .build();
    }
}

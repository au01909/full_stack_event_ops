package com.taskmanager.dto;

import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private String name;
    private String description;
    private String type;
    private TaskPriority priority;
    private TaskStatus status;
    private Integer durationSeconds;
    private String inputData;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Long executionDurationMs;
    private String errorMessage;
    private int retryCount;
    private Long version;
}

package com.taskmanager.service;

import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.TaskStatsResponse;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;

import java.util.List;

public interface TaskService {

    TaskResponse createTask(TaskCreateRequest request);

    List<TaskResponse> listTasks(TaskStatus status, TaskPriority priority);

    TaskResponse getTask(Long id);

    TaskResponse executeTask(Long id);

    TaskResponse cancelTask(Long id);

    TaskResponse retryTask(Long id);

    void deleteTask(Long id);

    TaskStatsResponse getStats();
}

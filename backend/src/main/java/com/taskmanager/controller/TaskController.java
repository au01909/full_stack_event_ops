package com.taskmanager.controller;

import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.TaskStatsResponse;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Thin controller: all it does is validate input, delegate to the service
 * layer, and translate the result into an HTTP response. No business logic
 * and no thread creation happens here.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority) {
        return ResponseEntity.ok(taskService.listTasks(status, priority));
    }

    @GetMapping("/stats")
    public ResponseEntity<TaskStatsResponse> getStats() {
        return ResponseEntity.ok(taskService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<TaskResponse> executeTask(@PathVariable Long id) {
        return ResponseEntity.accepted().body(taskService.executeTask(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskResponse> cancelTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.cancelTask(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskResponse> retryTask(@PathVariable Long id) {
        return ResponseEntity.accepted().body(taskService.retryTask(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}

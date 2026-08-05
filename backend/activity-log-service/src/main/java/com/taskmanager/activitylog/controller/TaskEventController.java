package com.taskmanager.activitylog.controller;

import com.taskmanager.activitylog.dto.TaskEventRequest;
import com.taskmanager.activitylog.model.TaskEvent;
import com.taskmanager.activitylog.repository.TaskEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class TaskEventController {

    private final TaskEventRepository taskEventRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskEvent record(@RequestBody TaskEventRequest request) {
        TaskEvent event = new TaskEvent(null, request.getTaskId(), request.getEventType(),
                Instant.now(), request.getDetails());
        return taskEventRepository.save(event);
    }

    @GetMapping("/task/{taskId}")
    public List<TaskEvent> byTask(@PathVariable Long taskId) {
        return taskEventRepository.findByTaskIdOrderByOccurredAtAsc(taskId);
    }
}

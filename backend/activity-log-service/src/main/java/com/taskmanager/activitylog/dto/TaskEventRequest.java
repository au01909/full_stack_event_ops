package com.taskmanager.activitylog.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TaskEventRequest {
    private Long taskId;
    private String eventType;
    private Map<String, Object> details;
}

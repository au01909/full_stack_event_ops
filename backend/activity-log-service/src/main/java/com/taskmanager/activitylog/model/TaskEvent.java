package com.taskmanager.activitylog.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * A single task lifecycle event. Stored schemaless in MongoDB since the
 * `details` payload varies by event type (duration, error message, retry count, ...).
 */
@Document(collection = "task_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {

    @Id
    private String id;

    private Long taskId;
    private String eventType;
    private Instant occurredAt;
    private Map<String, Object> details;
}

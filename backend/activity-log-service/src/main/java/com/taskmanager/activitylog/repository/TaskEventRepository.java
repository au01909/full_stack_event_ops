package com.taskmanager.activitylog.repository;

import com.taskmanager.activitylog.model.TaskEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TaskEventRepository extends MongoRepository<TaskEvent, String> {
    List<TaskEvent> findByTaskIdOrderByOccurredAtAsc(Long taskId);
}

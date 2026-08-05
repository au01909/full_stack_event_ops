package com.taskmanager.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Fire-and-forget client for the activity-log-service. Failures here must
 * never affect task execution, so they're logged and swallowed.
 */
@Component
@Slf4j
public class ActivityLogClient {

    private final RestClient restClient;

    public ActivityLogClient(@Value("${activity-log.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void record(Long taskId, String eventType, Map<String, Object> details) {
        try {
            restClient.post()
                    .uri("/api/events")
                    .body(Map.of("taskId", taskId, "eventType", eventType, "details", details))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to record activity event {} for task {}: {}", eventType, taskId, e.getMessage());
        }
    }
}

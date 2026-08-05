package com.taskmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "task.executor")
@Getter
@Setter
public class TaskExecutorProperties {
    /** Threads kept alive even when idle. */
    private int corePoolSize = 4;
    /** Upper bound on pool size under load. */
    private int maxPoolSize = 10;
    /** Bounded queue capacity; once full, the rejection policy kicks in. */
    private int queueCapacity = 50;
    /** Seconds an idle thread above corePoolSize is kept alive before being reclaimed. */
    private int keepAliveSeconds = 30;
    /** Prefix used when naming worker threads, useful for logs and thread dumps. */
    private String threadNamePrefix = "task-worker-";
}

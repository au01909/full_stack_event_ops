package com.taskmanager.config;

import com.taskmanager.exception.TaskQueueFullException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Defines the dedicated worker pool used to run tasks concurrently.
 *
 * This is the ONLY place threads are created for task execution. Controllers
 * and services must submit work here rather than spawning threads directly,
 * so pool sizing, naming, and rejection behaviour stay centrally controlled.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ThreadPoolConfig {

    private final TaskExecutorProperties properties;

    @Bean(name = "taskExecutionPool")
    public ThreadPoolTaskExecutor taskExecutionPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.setRejectedExecutionHandler(rejectionHandler());
        executor.initialize();
        return executor;
    }

    /**
     * Rather than silently dropping work (DiscardPolicy) or blocking the
     * calling HTTP thread (CallerRunsPolicy), we surface backpressure to the
     * caller as a typed exception so the API can return a clear 503.
     */
    private RejectedExecutionHandler rejectionHandler() {
        return (Runnable r, ThreadPoolExecutor executor) -> {
            log.warn("Task execution pool saturated (active={}, queue={}); rejecting submission",
                    executor.getActiveCount(), executor.getQueue().size());
            throw new TaskQueueFullException(
                    "Task execution pool is saturated. Please retry shortly.");
        };
    }
}

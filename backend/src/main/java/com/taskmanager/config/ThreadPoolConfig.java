package com.taskmanager.config;

import com.taskmanager.exception.TaskQueueFullException;
import com.taskmanager.executor.PriorityThreadPoolExecutor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private PriorityThreadPoolExecutor taskExecutionPool;

    @Bean(name = "taskExecutionPool")
    public PriorityThreadPoolExecutor taskExecutionPool() {
        AtomicInteger threadCount = new AtomicInteger(1);
        taskExecutionPool = new PriorityThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveSeconds(),
                properties.getQueueCapacity(),
                r -> new Thread(r, properties.getThreadNamePrefix() + threadCount.getAndIncrement()),
                rejectionHandler());
        return taskExecutionPool;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (taskExecutionPool != null) {
            taskExecutionPool.shutdown();
            taskExecutionPool.awaitTermination(20, TimeUnit.SECONDS);
        }
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

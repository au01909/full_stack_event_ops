package com.taskmanager.executor;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises the real {@link PriorityThreadPoolExecutor} (not a mock) so these
 * tests actually prove tasks run on separate threads at the same time, that
 * cancellation interrupts a running worker, that a failing task is reported
 * as FAILED rather than crashing the pool, and that higher-priority tasks
 * jump the queue ahead of lower-priority ones.
 */
@ExtendWith(MockitoExtension.class)
class TaskExecutionServiceTest {

    @Mock
    private TaskStateService taskStateService;

    private PriorityThreadPoolExecutor executor;
    private TaskExecutionService taskExecutionService;

    @BeforeEach
    void setUp() {
        AtomicInteger threadCount = new AtomicInteger(1);
        executor = new PriorityThreadPoolExecutor(4, 4, 30, 20,
                r -> new Thread(r, "test-worker-" + threadCount.getAndIncrement()),
                (r, e) -> { throw new java.util.concurrent.RejectedExecutionException(); });
        taskExecutionService = new TaskExecutionService(executor, taskStateService);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void multipleTasks_executeConcurrentlyOnDistinctThreads() throws InterruptedException {
        int taskCount = 4;
        CountDownLatch allStarted = new CountDownLatch(taskCount);
        CountDownLatch releaseAll = new CountDownLatch(1);
        CountDownLatch allFinished = new CountDownLatch(taskCount);
        Set<String> threadNamesUsed = ConcurrentHashMap.newKeySet();

        when(taskStateService.markRunning(anyLong())).thenAnswer(inv -> {
            threadNamesUsed.add(Thread.currentThread().getName());
            allStarted.countDown();
            try {
                // Hold every worker here until all four have actually started,
                // proving they are running concurrently rather than queued serially.
                releaseAll.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return Task.builder().id(inv.getArgument(0)).status(TaskStatus.RUNNING).build();
        });
        doAnswer(inv -> {
            allFinished.countDown();
            return null;
        }).when(taskStateService).markCompleted(anyLong(), anyLong());

        for (long i = 1; i <= taskCount; i++) {
            taskExecutionService.submit(i, "DEMO", 1, TaskPriority.MEDIUM);
        }

        boolean started = allStarted.await(3, TimeUnit.SECONDS);
        releaseAll.countDown();

        assertThat(started).as("all %d tasks should start within the timeout", taskCount).isTrue();
        assertThat(threadNamesUsed).as("tasks should run on distinct worker threads").hasSize(taskCount);

        assertThat(allFinished.await(5, TimeUnit.SECONDS)).isTrue();
        verify(taskStateService, times(taskCount)).markCompleted(anyLong(), anyLong());
    }

    @Test
    void failingTaskType_transitionsToFailedWithoutCrashingOtherWorkers() throws InterruptedException {
        when(taskStateService.markRunning(1L))
                .thenReturn(Task.builder().id(1L).status(TaskStatus.RUNNING).build());
        CountDownLatch failedLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            failedLatch.countDown();
            return null;
        }).when(taskStateService).markFailed(eq(1L), any(), anyLong());

        taskExecutionService.submit(1L, "FAIL_TEST", 1, TaskPriority.MEDIUM);

        assertThat(failedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(taskStateService).markFailed(eq(1L), any(), anyLong());
        verify(taskStateService, never()).markCompleted(eq(1L), anyLong());

        // Pool must still accept new work after a worker thread threw an exception.
        when(taskStateService.markRunning(2L))
                .thenReturn(Task.builder().id(2L).status(TaskStatus.RUNNING).build());
        CountDownLatch completedLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            completedLatch.countDown();
            return null;
        }).when(taskStateService).markCompleted(eq(2L), anyLong());

        taskExecutionService.submit(2L, "DEMO", 1, TaskPriority.MEDIUM);
        assertThat(completedLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancel_interruptsRunningWorkerAndMarksCancelled() throws InterruptedException {
        CountDownLatch running = new CountDownLatch(1);
        when(taskStateService.markRunning(1L)).thenAnswer(inv -> {
            running.countDown();
            return Task.builder().id(1L).status(TaskStatus.RUNNING).build();
        });

        taskExecutionService.submit(1L, "DEMO", 30, TaskPriority.MEDIUM); // long-running job
        assertThat(running.await(3, TimeUnit.SECONDS)).isTrue();

        taskExecutionService.cancel(1L);

        verify(taskStateService, timeout(2000)).markCancelled(1L);
        // Because the worker was interrupted mid-sleep, it must never report success.
        verify(taskStateService, never()).markCompleted(eq(1L), anyLong());
    }

    @Test
    void queuedTasks_runInPriorityOrderNotSubmissionOrder() throws InterruptedException {
        // Occupy every worker thread so subsequent submissions pile up in the queue
        // instead of starting immediately, letting priority order actually matter.
        CountDownLatch blockersRunning = new CountDownLatch(4);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        when(taskStateService.markRunning(longThat(id -> id <= 4))).thenAnswer(inv -> {
            blockersRunning.countDown();
            releaseBlockers.await(5, TimeUnit.SECONDS);
            return Task.builder().id(inv.getArgument(0)).status(TaskStatus.RUNNING).build();
        });
        for (long i = 1; i <= 4; i++) {
            taskExecutionService.submit(i, "DEMO", 1, TaskPriority.MEDIUM);
        }
        assertThat(blockersRunning.await(3, TimeUnit.SECONDS)).isTrue();

        List<Long> runOrder = new CopyOnWriteArrayList<>();
        when(taskStateService.markRunning(longThat(id -> id > 4))).thenAnswer(inv -> {
            runOrder.add(inv.getArgument(0));
            return Task.builder().id(inv.getArgument(0)).status(TaskStatus.RUNNING).build();
        });
        CountDownLatch allFinished = new CountDownLatch(3);
        doAnswer(inv -> {
            allFinished.countDown();
            return null;
        }).when(taskStateService).markCompleted(longThat(id -> id > 4), anyLong());

        taskExecutionService.submit(5L, "DEMO", 0, TaskPriority.LOW);
        taskExecutionService.submit(6L, "DEMO", 0, TaskPriority.CRITICAL);
        taskExecutionService.submit(7L, "DEMO", 0, TaskPriority.HIGH);

        releaseBlockers.countDown();

        assertThat(allFinished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runOrder).as("higher priority tasks should run before lower priority ones queued after workers freed up")
                .containsExactly(6L, 7L, 5L);
    }
}

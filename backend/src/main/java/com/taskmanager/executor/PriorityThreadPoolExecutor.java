package com.taskmanager.executor;

import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link ThreadPoolExecutor} whose queue dequeues higher-priority work
 * first, breaking ties by submission order (FIFO within the same priority).
 * Submitted work must implement {@link PrioritizedRunnable}; anything else
 * defaults to the lowest priority.
 */
public class PriorityThreadPoolExecutor extends ThreadPoolExecutor {

    private final AtomicLong sequenceGenerator = new AtomicLong();

    public PriorityThreadPoolExecutor(int corePoolSize, int maxPoolSize, int keepAliveSeconds,
                                       int queueCapacity, ThreadFactory threadFactory,
                                       RejectedExecutionHandler rejectedExecutionHandler) {
        super(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS,
                new BoundedPriorityQueue(queueCapacity), threadFactory, rejectedExecutionHandler);
    }

    public interface PrioritizedRunnable extends Runnable {
        int getPriority();
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        int priority = (runnable instanceof PrioritizedRunnable pr) ? pr.getPriority() : 0;
        return new ComparableFutureTask<>(runnable, value, priority, sequenceGenerator.incrementAndGet());
    }

    private static final class ComparableFutureTask<T> extends FutureTask<T>
            implements Comparable<ComparableFutureTask<T>> {
        private final int priority;
        private final long sequence;

        ComparableFutureTask(Runnable runnable, T value, int priority, long sequence) {
            super(runnable, value);
            this.priority = priority;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(ComparableFutureTask<T> other) {
            int cmp = Integer.compare(other.priority, this.priority); // higher priority first
            return cmp != 0 ? cmp : Long.compare(this.sequence, other.sequence); // then FIFO
        }
    }

    /**
     * PriorityBlockingQueue is unbounded by default; this enforces the
     * configured capacity so the pool's rejection policy still kicks in
     * under load, same as the plain FIFO queue it replaces.
     */
    private static final class BoundedPriorityQueue extends PriorityBlockingQueue<Runnable> {
        private final int capacity;

        BoundedPriorityQueue(int capacity) {
            super(Math.max(capacity, 1));
            this.capacity = capacity;
        }

        @Override
        public boolean offer(Runnable runnable) {
            if (size() >= capacity) {
                return false;
            }
            return super.offer(runnable);
        }
    }
}

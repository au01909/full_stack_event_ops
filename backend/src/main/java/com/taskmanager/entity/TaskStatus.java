package com.taskmanager.entity;

/**
 * Lifecycle states for a task.
 *
 * PENDING   -> task created, not yet submitted to the executor
 * RUNNING   -> task has been picked up by a worker thread
 * COMPLETED -> task finished successfully
 * FAILED    -> task threw an exception during execution
 * CANCELLED -> task was cancelled by the user while PENDING or RUNNING
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * Terminal states cannot transition to any other state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}

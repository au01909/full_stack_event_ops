package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a unit of asynchronous work submitted by a user.
 *
 * The {@code version} column backs JPA optimistic locking so that concurrent
 * status transitions (e.g. a cancel request racing a worker thread completing
 * the task) fail fast with an {@link jakarta.persistence.OptimisticLockException}
 * instead of silently corrupting state.
 */
@Entity
@Table(
        name = "tasks",
        indexes = {
                @Index(name = "idx_tasks_status", columnList = "status"),
                @Index(name = "idx_tasks_priority", columnList = "priority"),
                @Index(name = "idx_tasks_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "task_type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    /** Optional simulated workload duration, in seconds, supplied at creation time. */
    @Column(name = "input_duration_seconds")
    private Integer inputDurationSeconds;

    /** Free-form input payload for the task (e.g. parameters for the job). */
    @Column(name = "input_data", length = 4000)
    private String inputData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Wall-clock execution duration in milliseconds, populated once the task finishes. */
    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** Number of times this task has been retried after a failure. */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TaskStatus.PENDING;
        }
    }
}

package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByStatusAndPriorityOrderByCreatedAtDesc(TaskStatus status, TaskPriority priority);

    List<Task> findAllByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<Task> findAllByPriorityOrderByCreatedAtDesc(TaskPriority priority);

    List<Task> findAllByOrderByCreatedAtDesc();

    /**
     * Reads the task with a pessimistic write lock. Used by the executor when
     * transitioning state (PENDING -> RUNNING -> COMPLETED/FAILED) so that a
     * concurrent cancel request cannot race the worker thread's final update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);
}

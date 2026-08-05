package com.taskmanager.integration;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring context against a real, ephemeral PostgreSQL
 * instance (via Testcontainers) so the Flyway migration and JPA mappings are
 * verified against the actual database engine rather than an in-memory
 * substitute.
 *
 * Requires a Docker-capable environment to run; skipped automatically by
 * Testcontainers if none is available.
 */
@Testcontainers
@SpringBootTest
class TaskRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("taskdb_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void savesAndFiltersTasksByStatusAndPriority() {
        taskRepository.save(newTask("Job A", TaskStatus.PENDING, TaskPriority.HIGH));
        taskRepository.save(newTask("Job B", TaskStatus.RUNNING, TaskPriority.HIGH));
        taskRepository.save(newTask("Job C", TaskStatus.RUNNING, TaskPriority.LOW));

        List<Task> highPriorityRunning =
                taskRepository.findAllByStatusAndPriorityOrderByCreatedAtDesc(TaskStatus.RUNNING, TaskPriority.HIGH);

        assertThat(highPriorityRunning).hasSize(1);
        assertThat(highPriorityRunning.get(0).getName()).isEqualTo("Job B");
    }

    @Test
    void optimisticLockVersionIncrementsOnUpdate() {
        Task saved = taskRepository.save(newTask("Versioned job", TaskStatus.PENDING, TaskPriority.MEDIUM));
        Long initialVersion = saved.getVersion();

        saved.setStatus(TaskStatus.RUNNING);
        Task updated = taskRepository.save(saved);

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }

    private Task newTask(String name, TaskStatus status, TaskPriority priority) {
        return Task.builder()
                .name(name)
                .type("DEMO")
                .status(status)
                .priority(priority)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }
}

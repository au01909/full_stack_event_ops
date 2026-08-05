package com.taskmanager.service;

import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.executor.TaskExecutionService;
import com.taskmanager.executor.TaskStateService;
import com.taskmanager.exception.InvalidTaskStateException;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.mapper.TaskMapper;
import com.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskExecutionService taskExecutionService;
    @Mock
    private TaskStateService taskStateService;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Task pendingTask;

    @BeforeEach
    void setUp() {
        pendingTask = Task.builder()
                .id(1L)
                .name("Generate report")
                .type("REPORT")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.PENDING)
                .inputDurationSeconds(5)
                .createdAt(Instant.now())
                .retryCount(0)
                .version(0L)
                .build();
    }

    @Test
    void createTask_savesEntityAndReturnsMappedResponse() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setName("Generate report");
        request.setType("REPORT");
        request.setPriority(TaskPriority.HIGH);

        when(taskMapper.toEntity(request)).thenReturn(pendingTask);
        when(taskRepository.save(pendingTask)).thenReturn(pendingTask);
        when(taskMapper.toResponse(pendingTask)).thenReturn(
                TaskResponse.builder().id(1L).status(TaskStatus.PENDING).build());

        TaskResponse response = taskService.createTask(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(taskRepository).save(pendingTask);
    }

    @Test
    void executeTask_pendingTask_submitsToExecutor() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pendingTask));
        when(taskMapper.toResponse(pendingTask)).thenReturn(
                TaskResponse.builder().id(1L).status(TaskStatus.PENDING).build());

        taskService.executeTask(1L);

        verify(taskExecutionService).submit(1L, "REPORT", 5);
    }

    @Test
    void executeTask_alreadyRunning_throwsInvalidState() {
        pendingTask.setStatus(TaskStatus.RUNNING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pendingTask));

        assertThatThrownBy(() -> taskService.executeTask(1L))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessageContaining("RUNNING");

        verifyNoInteractions(taskExecutionService);
    }

    @Test
    void executeTask_unknownId_throwsNotFound() {
        when(taskRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.executeTask(42L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void cancelTask_delegatesToExecutionServiceAndReturnsUpdatedState() {
        Task cancelled = Task.builder().id(1L).status(TaskStatus.CANCELLED).build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pendingTask), Optional.of(cancelled));
        when(taskMapper.toResponse(cancelled)).thenReturn(
                TaskResponse.builder().id(1L).status(TaskStatus.CANCELLED).build());

        TaskResponse response = taskService.cancelTask(1L);

        verify(taskExecutionService).cancel(1L);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void retryTask_delegatesToStateServiceThenResubmits() {
        Task failed = Task.builder().id(1L).type("REPORT").status(TaskStatus.FAILED).inputDurationSeconds(5).build();
        Task reset = Task.builder().id(1L).type("REPORT").status(TaskStatus.PENDING).inputDurationSeconds(5).build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(taskStateService.resetForRetry(1L)).thenReturn(reset);
        when(taskMapper.toResponse(reset)).thenReturn(
                TaskResponse.builder().id(1L).status(TaskStatus.PENDING).build());

        TaskResponse response = taskService.retryTask(1L);

        verify(taskExecutionService).submit(1L, "REPORT", 5);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void deleteTask_runningTask_isRejected() {
        pendingTask.setStatus(TaskStatus.RUNNING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pendingTask));

        assertThatThrownBy(() -> taskService.deleteTask(1L))
                .isInstanceOf(InvalidTaskStateException.class);

        verify(taskRepository, never()).delete(any());
    }

    @Test
    void deleteTask_pendingTask_isDeleted() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pendingTask));

        taskService.deleteTask(1L);

        verify(taskRepository).delete(pendingTask);
    }
}

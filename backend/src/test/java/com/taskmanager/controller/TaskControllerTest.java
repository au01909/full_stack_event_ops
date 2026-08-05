package com.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.dto.TaskCreateRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.entity.TaskPriority;
import com.taskmanager.entity.TaskStatus;
import com.taskmanager.exception.InvalidTaskStateException;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    private TaskResponse sampleResponse(Long id, TaskStatus status) {
        return TaskResponse.builder()
                .id(id)
                .name("Generate report")
                .description("Nightly batch report")
                .type("REPORT")
                .priority(TaskPriority.HIGH)
                .status(status)
                .durationSeconds(5)
                .createdAt(Instant.now())
                .retryCount(0)
                .version(0L)
                .build();
    }

    @Test
    void createTask_returns201WithBody() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setName("Generate report");
        request.setType("REPORT");
        request.setPriority(TaskPriority.HIGH);
        request.setDurationSeconds(5);

        when(taskService.createTask(any())).thenReturn(sampleResponse(1L, TaskStatus.PENDING));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTask_missingName_returns400() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setType("REPORT");
        request.setPriority(TaskPriority.HIGH);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void listTasks_withStatusFilter_delegatesToService() throws Exception {
        when(taskService.listTasks(eq(TaskStatus.RUNNING), eq(null)))
                .thenReturn(List.of(sampleResponse(2L, TaskStatus.RUNNING)));

        mockMvc.perform(get("/api/tasks").param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
    }

    @Test
    void getTask_notFound_returns404WithConsistentBody() throws Exception {
        when(taskService.getTask(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 99"));
    }

    @Test
    void executeTask_returns202Accepted() throws Exception {
        when(taskService.executeTask(1L)).thenReturn(sampleResponse(1L, TaskStatus.PENDING));

        mockMvc.perform(post("/api/tasks/1/execute"))
                .andExpect(status().isAccepted());
    }

    @Test
    void cancelTask_invalidState_returns409() throws Exception {
        when(taskService.cancelTask(1L))
                .thenThrow(new InvalidTaskStateException("Task 1 cannot be cancelled - already COMPLETED"));

        mockMvc.perform(post("/api/tasks/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void deleteTask_returns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/5"))
                .andExpect(status().isNoContent());
    }
}

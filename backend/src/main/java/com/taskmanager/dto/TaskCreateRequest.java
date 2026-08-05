package com.taskmanager.dto;

import com.taskmanager.entity.TaskPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    @NotBlank(message = "type is required")
    @Size(max = 50, message = "type must be at most 50 characters")
    private String type;

    @NotNull(message = "priority is required")
    private TaskPriority priority;

    /** Optional simulated workload duration in seconds (1-300). Defaults to a short job if omitted. */
    @Min(value = 1, message = "durationSeconds must be at least 1")
    @Max(value = 300, message = "durationSeconds must be at most 300")
    private Integer durationSeconds;

    @Size(max = 4000, message = "inputData must be at most 4000 characters")
    private String inputData;
}

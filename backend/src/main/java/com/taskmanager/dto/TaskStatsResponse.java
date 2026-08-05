package com.taskmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatsResponse {
    private long total;
    private long pending;
    private long running;
    private long completed;
    private long failed;
    private long cancelled;
    private int activeWorkerThreads;
    private int maxWorkerThreads;
    private int queuedTasks;
}

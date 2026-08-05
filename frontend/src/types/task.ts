export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Task {
  id: number;
  name: string;
  description?: string;
  type: string;
  priority: TaskPriority;
  status: TaskStatus;
  durationSeconds?: number;
  inputData?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  executionDurationMs?: number;
  errorMessage?: string;
  retryCount: number;
  version: number;
}

export interface TaskCreateRequest {
  name: string;
  description?: string;
  type: string;
  priority: TaskPriority;
  durationSeconds?: number;
  inputData?: string;
}

export interface TaskStats {
  total: number;
  pending: number;
  running: number;
  completed: number;
  failed: number;
  cancelled: number;
  activeWorkerThreads: number;
  maxWorkerThreads: number;
  queuedTasks: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

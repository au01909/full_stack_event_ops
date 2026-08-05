import axios, { AxiosError } from 'axios';
import type { ApiError, Task, TaskCreateRequest, TaskPriority, TaskStats, TaskStatus } from '../types/task';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
});

export class TaskApiError extends Error {
  status: number;
  details?: string[];

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.status = apiError.status;
    this.details = apiError.details;
  }
}

function unwrap<T>(promise: Promise<{ data: T }>): Promise<T> {
  return promise
    .then((res) => res.data)
    .catch((err: AxiosError<ApiError>) => {
      if (err.response?.data) {
        throw new TaskApiError(err.response.data);
      }
      throw new Error(err.message || 'Network error - is the backend reachable?');
    });
}

export const taskApi = {
  list(filters?: { status?: TaskStatus; priority?: TaskPriority }): Promise<Task[]> {
    return unwrap(client.get('/tasks', { params: filters }));
  },
  get(id: number): Promise<Task> {
    return unwrap(client.get(`/tasks/${id}`));
  },
  create(payload: TaskCreateRequest): Promise<Task> {
    return unwrap(client.post('/tasks', payload));
  },
  execute(id: number): Promise<Task> {
    return unwrap(client.post(`/tasks/${id}/execute`));
  },
  cancel(id: number): Promise<Task> {
    return unwrap(client.post(`/tasks/${id}/cancel`));
  },
  retry(id: number): Promise<Task> {
    return unwrap(client.post(`/tasks/${id}/retry`));
  },
  remove(id: number): Promise<void> {
    return unwrap(client.delete(`/tasks/${id}`));
  },
  stats(): Promise<TaskStats> {
    return unwrap(client.get('/tasks/stats'));
  },
};

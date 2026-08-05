import { useCallback, useEffect, useRef, useState } from 'react';
import { taskApi, TaskApiError } from '../api/taskApi';
import type { Task, TaskCreateRequest, TaskPriority, TaskStats, TaskStatus } from '../types/task';
import { Header } from '../components/Header';
import { StatsCards } from '../components/StatsCards';
import { Filters } from '../components/Filters';
import { TaskTable } from '../components/TaskTable';
import { TaskForm } from '../components/TaskForm';
import { TaskDetailsModal } from '../components/TaskDetailsModal';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { EmptyState } from '../components/EmptyState';

const POLL_INTERVAL_MS = 2000;

export function Dashboard() {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [stats, setStats] = useState<TaskStats | null>(null);
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('');
  const [priorityFilter, setPriorityFilter] = useState<TaskPriority | ''>('');

  const [showForm, setShowForm] = useState(false);
  const [formSubmitting, setFormSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [busyIds, setBusyIds] = useState<Set<number>>(new Set());
  const [banner, setBanner] = useState<string | null>(null);

  const filtersRef = useRef({ statusFilter, priorityFilter });
  filtersRef.current = { statusFilter, priorityFilter };

  const refresh = useCallback(async () => {
    const { statusFilter: status, priorityFilter: priority } = filtersRef.current;
    try {
      const [taskList, taskStats] = await Promise.all([
        taskApi.list({ status: status || undefined, priority: priority || undefined }),
        taskApi.stats(),
      ]);
      setTasks(taskList);
      setStats(taskStats);
    } catch (err) {
      setBanner(err instanceof Error ? err.message : 'Failed to reach the backend.');
    }
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [refresh, statusFilter, priorityFilter]);

  function markBusy(id: number, busy: boolean) {
    setBusyIds((prev) => {
      const next = new Set(prev);
      if (busy) next.add(id); else next.delete(id);
      return next;
    });
  }

  async function handleCreate(payload: TaskCreateRequest) {
    setFormSubmitting(true);
    setFormError(null);
    try {
      await taskApi.create(payload);
      setShowForm(false);
      await refresh();
    } catch (err) {
      setFormError(err instanceof TaskApiError ? err.message : 'Could not create task.');
    } finally {
      setFormSubmitting(false);
    }
  }

  async function handleAction(task: Task, action: (id: number) => Promise<Task>, verb: string) {
    markBusy(task.id, true);
    setBanner(null);
    try {
      await action(task.id);
      await refresh();
    } catch (err) {
      setBanner(err instanceof Error ? `Could not ${verb} task #${task.id}: ${err.message}` : `Could not ${verb} task.`);
    } finally {
      markBusy(task.id, false);
    }
  }

  async function handleDelete(task: Task) {
    markBusy(task.id, true);
    setBanner(null);
    try {
      await taskApi.remove(task.id);
      if (selectedTask?.id === task.id) setSelectedTask(null);
      await refresh();
    } catch (err) {
      setBanner(err instanceof Error ? `Could not delete task #${task.id}: ${err.message}` : 'Could not delete task.');
    } finally {
      markBusy(task.id, false);
    }
  }

  const hasFilters = statusFilter !== '' || priorityFilter !== '';

  return (
    <div className="dashboard">
      <Header onNewTask={() => setShowForm(true)} />
      <StatsCards stats={stats} />
      <Filters
        status={statusFilter}
        priority={priorityFilter}
        onStatusChange={setStatusFilter}
        onPriorityChange={setPriorityFilter}
        resultCount={tasks?.length ?? 0}
      />

      {banner && (
        <div className="banner banner-error">
          {banner}
          <button className="btn btn-ghost btn-sm" onClick={() => setBanner(null)}>Dismiss</button>
        </div>
      )}

      {tasks === null ? (
        <LoadingSpinner label="Loading tasks…" />
      ) : tasks.length === 0 ? (
        <EmptyState hasFilters={hasFilters} onCreate={() => setShowForm(true)} />
      ) : (
        <TaskTable
          tasks={tasks}
          onView={setSelectedTask}
          onExecute={(t) => handleAction(t, taskApi.execute, 'execute')}
          onCancel={(t) => handleAction(t, taskApi.cancel, 'cancel')}
          onRetry={(t) => handleAction(t, taskApi.retry, 'retry')}
          onDelete={handleDelete}
          busyIds={busyIds}
        />
      )}

      {showForm && (
        <TaskForm
          onSubmit={handleCreate}
          onClose={() => setShowForm(false)}
          submitting={formSubmitting}
          error={formError}
        />
      )}

      {selectedTask && (
        <TaskDetailsModal
          task={tasks?.find((t) => t.id === selectedTask.id) ?? selectedTask}
          onClose={() => setSelectedTask(null)}
        />
      )}
    </div>
  );
}

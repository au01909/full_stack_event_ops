import type { Task } from '../types/task';
import { StatusBadge } from './StatusBadge';
import { PriorityBadge } from './PriorityBadge';
import './TaskTable.css';

interface Props {
  tasks: Task[];
  onView: (task: Task) => void;
  onExecute: (task: Task) => void;
  onCancel: (task: Task) => void;
  onRetry: (task: Task) => void;
  onDelete: (task: Task) => void;
  busyIds: Set<number>;
}

function formatTime(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function formatDuration(ms?: number) {
  if (ms == null) return '—';
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

export function TaskTable({ tasks, onView, onExecute, onCancel, onRetry, onDelete, busyIds }: Props) {
  return (
    <div className="task-table-wrap">
      <table className="task-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Created</th>
            <th>Started</th>
            <th>Completed</th>
            <th>Duration</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => {
            const busy = busyIds.has(task.id);
            return (
              <tr key={task.id} onClick={() => onView(task)} className="task-row">
                <td className="mono task-id">#{task.id}</td>
                <td className="task-name">{task.name}</td>
                <td><StatusBadge status={task.status} /></td>
                <td><PriorityBadge priority={task.priority} /></td>
                <td className="mono">{formatTime(task.createdAt)}</td>
                <td className="mono">{formatTime(task.startedAt)}</td>
                <td className="mono">{formatTime(task.completedAt)}</td>
                <td className="mono">{formatDuration(task.executionDurationMs)}</td>
                <td className="task-actions" onClick={(e) => e.stopPropagation()}>
                  {task.status === 'PENDING' && (
                    <button className="btn btn-sm btn-primary" disabled={busy} onClick={() => onExecute(task)}>
                      Execute
                    </button>
                  )}
                  {(task.status === 'PENDING' || task.status === 'RUNNING') && (
                    <button className="btn btn-sm btn-ghost" disabled={busy} onClick={() => onCancel(task)}>
                      Cancel
                    </button>
                  )}
                  {task.status === 'FAILED' && (
                    <button className="btn btn-sm btn-primary" disabled={busy} onClick={() => onRetry(task)}>
                      Retry
                    </button>
                  )}
                  {task.status !== 'RUNNING' && (
                    <button className="btn btn-sm btn-danger" disabled={busy} onClick={() => onDelete(task)}>
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

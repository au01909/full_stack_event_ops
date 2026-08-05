import type { TaskStatus } from '../types/task';
import './badges.css';

const LABELS: Record<TaskStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export function StatusBadge({ status }: { status: TaskStatus }) {
  return (
    <span className={`badge badge-status badge-${status.toLowerCase()}`}>
      {status === 'RUNNING' && <span className="badge-pulse" aria-hidden="true" />}
      {LABELS[status]}
    </span>
  );
}

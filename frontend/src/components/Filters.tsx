import type { TaskPriority, TaskStatus } from '../types/task';
import './Filters.css';

const STATUSES: TaskStatus[] = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'];
const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

interface Props {
  status: TaskStatus | '';
  priority: TaskPriority | '';
  onStatusChange: (status: TaskStatus | '') => void;
  onPriorityChange: (priority: TaskPriority | '') => void;
  resultCount: number;
}

export function Filters({ status, priority, onStatusChange, onPriorityChange, resultCount }: Props) {
  const hasFilters = status !== '' || priority !== '';

  return (
    <div className="filters-bar">
      <div className="filters-group">
        <select value={status} onChange={(e) => onStatusChange(e.target.value as TaskStatus | '')}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <select value={priority} onChange={(e) => onPriorityChange(e.target.value as TaskPriority | '')}>
          <option value="">All priorities</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>{p}</option>
          ))}
        </select>
        {hasFilters && (
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => {
              onStatusChange('');
              onPriorityChange('');
            }}
          >
            Clear filters
          </button>
        )}
      </div>
      <span className="filters-count mono">{resultCount} task{resultCount === 1 ? '' : 's'}</span>
    </div>
  );
}

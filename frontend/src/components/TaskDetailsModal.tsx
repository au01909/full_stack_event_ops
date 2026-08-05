import type { Task } from '../types/task';
import { StatusBadge } from './StatusBadge';
import { PriorityBadge } from './PriorityBadge';
import './TaskDetailsModal.css';

function formatFull(iso?: string) {
  return iso ? new Date(iso).toLocaleString() : '—';
}

export function TaskDetailsModal({ task, onClose }: { task: Task; onClose: () => void }) {
  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div className="modal-panel details-panel" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Task #{task.id}</h2>
          <button className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close">✕</button>
        </div>

        <div className="details-body">
          <div className="details-title-row">
            <h3>{task.name}</h3>
            <div className="details-badges">
              <StatusBadge status={task.status} />
              <PriorityBadge priority={task.priority} />
            </div>
          </div>

          {task.description && <p className="details-description">{task.description}</p>}

          <dl className="details-grid">
            <div><dt>Type</dt><dd className="mono">{task.type}</dd></div>
            <div><dt>Retry count</dt><dd className="mono">{task.retryCount}</dd></div>
            <div><dt>Created</dt><dd className="mono">{formatFull(task.createdAt)}</dd></div>
            <div><dt>Started</dt><dd className="mono">{formatFull(task.startedAt)}</dd></div>
            <div><dt>Completed</dt><dd className="mono">{formatFull(task.completedAt)}</dd></div>
            <div>
              <dt>Duration</dt>
              <dd className="mono">
                {task.executionDurationMs != null ? `${(task.executionDurationMs / 1000).toFixed(2)}s` : '—'}
              </dd>
            </div>
          </dl>

          {task.inputData && (
            <div className="details-block">
              <dt>Input data</dt>
              <pre className="mono">{task.inputData}</pre>
            </div>
          )}

          {task.errorMessage && (
            <div className="details-error">
              <dt>Error</dt>
              <pre className="mono">{task.errorMessage}</pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

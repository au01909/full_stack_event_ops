import { useState, type FormEvent } from 'react';
import type { TaskCreateRequest, TaskPriority } from '../types/task';
import './TaskForm.css';

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const TASK_TYPES = ['REPORT', 'DATA_SYNC', 'EMAIL_BATCH', 'IMAGE_PROCESSING', 'FAIL_TEST'];

interface Props {
  onSubmit: (payload: TaskCreateRequest) => Promise<void>;
  onClose: () => void;
  submitting: boolean;
  error: string | null;
}

export function TaskForm({ onSubmit, onClose, submitting, error }: Props) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState(TASK_TYPES[0]);
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [durationSeconds, setDurationSeconds] = useState(5);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await onSubmit({ name, description, type, priority, durationSeconds });
  }

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div className="modal-panel" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>New task</h2>
          <button className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="task-form">
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={150} autoFocus />
          </label>

          <label>
            Description
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              rows={2}
            />
          </label>

          <div className="task-form-row">
            <label>
              Type
              <select value={type} onChange={(e) => setType(e.target.value)}>
                {TASK_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </label>

            <label>
              Priority
              <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </label>

            <label>
              Duration (sec)
              <input
                type="number"
                min={1}
                max={300}
                value={durationSeconds}
                onChange={(e) => setDurationSeconds(Number(e.target.value))}
              />
            </label>
          </div>

          <p className="task-form-hint">
            Tip: choose type <code className="mono">FAIL_TEST</code> to see failure handling and retry in action.
          </p>

          {error && <p className="task-form-error">{error}</p>}

          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create task'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

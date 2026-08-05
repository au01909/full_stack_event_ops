import type { TaskPriority } from '../types/task';
import './badges.css';

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <span className={`badge badge-priority badge-priority-${priority.toLowerCase()}`}>{priority}</span>;
}

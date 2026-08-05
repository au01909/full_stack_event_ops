import type { TaskStats } from '../types/task';
import './StatsCards.css';

export function StatsCards({ stats }: { stats: TaskStats | null }) {
  const s = stats ?? {
    total: 0, pending: 0, running: 0, completed: 0, failed: 0, cancelled: 0,
    activeWorkerThreads: 0, maxWorkerThreads: 0, queuedTasks: 0,
  };

  const cards = [
    { label: 'Total tasks', value: s.total, tone: 'neutral' as const },
    { label: 'Running', value: s.running, tone: 'amber' as const },
    { label: 'Completed', value: s.completed, tone: 'cyan' as const },
    { label: 'Failed', value: s.failed, tone: 'red' as const },
  ];

  const slots = Array.from({ length: Math.max(s.maxWorkerThreads, 1) }, (_, i) => i < s.activeWorkerThreads);

  return (
    <section className="stats-row">
      {cards.map((c) => (
        <div key={c.label} className={`stat-card stat-${c.tone}`}>
          <span className="stat-label">{c.label}</span>
          <span className="stat-value mono">{c.value}</span>
        </div>
      ))}

      <div className="stat-card stat-pool" title="Worker pool utilization">
        <span className="stat-label">
          Worker pool <span className="mono stat-pool-count">{s.activeWorkerThreads}/{s.maxWorkerThreads}</span>
        </span>
        <div className="pool-strip">
          {slots.map((busy, i) => (
            <span key={i} className={busy ? 'pool-slot pool-slot-busy' : 'pool-slot'} />
          ))}
        </div>
        {s.queuedTasks > 0 && (
          <span className="stat-queue mono">{s.queuedTasks} queued</span>
        )}
      </div>
    </section>
  );
}

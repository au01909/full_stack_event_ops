import './EmptyState.css';

export function EmptyState({ hasFilters, onCreate }: { hasFilters: boolean; onCreate: () => void }) {
  return (
    <div className="empty-state">
      <span className="empty-state-mark" aria-hidden="true" />
      {hasFilters ? (
        <>
          <p>No tasks match these filters.</p>
          <p className="empty-state-sub">Try clearing the status or priority filter.</p>
        </>
      ) : (
        <>
          <p>No tasks yet.</p>
          <button className="btn btn-primary btn-sm" onClick={onCreate}>Create your first task</button>
        </>
      )}
    </div>
  );
}

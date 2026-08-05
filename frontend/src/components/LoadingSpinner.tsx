import './LoadingSpinner.css';

export function LoadingSpinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <span className="loading-spinner" aria-hidden="true" />
      <span className="mono">{label}</span>
    </div>
  );
}

import './Header.css';

export function Header({ onNewTask }: { onNewTask: () => void }) {
  return (
    <header className="app-header">
      <div className="app-header-brand">
        <span className="app-header-mark" aria-hidden="true" />
        <div>
          <h1>Task Execution Console</h1>
          <p className="app-header-sub mono">concurrent job dispatch &amp; monitoring</p>
        </div>
      </div>
      <button className="btn btn-primary" onClick={onNewTask}>
        + New task
      </button>
    </header>
  );
}

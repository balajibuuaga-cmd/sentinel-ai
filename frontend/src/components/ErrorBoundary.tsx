import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** When this value changes, a caught error is cleared. Pass the route path so
   *  navigating away from a crashed screen recovers automatically. */
  resetKey?: unknown;
  variant?: 'page' | 'app';
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time errors so one broken screen cannot white-out the whole app.
 * Suspense only handles pending states, not thrown errors — without this, any
 * exception in a page component unmounts the entire tree. The page-level boundary
 * keeps the shell (sidebar, top bar) alive and recovers on navigation; the
 * app-level one is the last-resort catch-all.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Log for debugging. Nothing sensitive — just the error and component stack.
    console.error('Render error caught by boundary:', error, info.componentStack);
  }

  componentDidUpdate(prev: Props) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }

    const isApp = this.props.variant === 'app';
    return (
      <div className={isApp ? 'full-screen-state' : 'page-empty-state'} role="alert">
        <div className="error-boundary-card">
          <h2>Something went wrong on this screen.</h2>
          <p>
            {isApp
              ? 'Sentinel hit an unexpected error. Reloading usually clears it.'
              : 'The rest of Sentinel is still working — try again, or pick another view from the sidebar.'}
          </p>
          <div className="error-boundary-actions">
            <button className="action-btn tone-good" onClick={() => this.setState({ error: null })}>
              Try again
            </button>
            {isApp ? (
              <button className="action-btn" onClick={() => window.location.assign('/')}>
                Reload Sentinel
              </button>
            ) : null}
          </div>
        </div>
      </div>
    );
  }
}

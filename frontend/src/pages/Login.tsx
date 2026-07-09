import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { login } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

interface Props {
  onAuthenticated: () => void;
}

export default function Login({ onAuthenticated }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email, password);
      onAuthenticated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="panel auth-card">
        <div className="auth-brand">
          <HolographicAvatar active size={72} />
          <h1>Sentinel AI</h1>
          <p>Sign in to your engineering command center</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <div className="auth-error">{error}</div> : null}
          <label>
            Email
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@company.com"
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
            />
          </label>
          <button className="auth-submit" type="submit" disabled={submitting}>
            {submitting ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div className="auth-switch">
          <Link to="/forgot-password">Forgot password?</Link>
        </div>
        <div className="auth-switch">
          Don&apos;t have an account? <Link to="/signup">Create one</Link>
        </div>
      </div>
    </div>
  );
}

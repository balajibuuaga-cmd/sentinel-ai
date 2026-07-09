import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { signup } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

interface Props {
  onAuthenticated: () => void;
}

function passwordPolicyError(password: string): string | null {
  if (password.length < 10) {
    return 'Password must be at least 10 characters long.';
  }
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'Password must contain at least one letter and one digit.';
  }
  return null;
}

export default function Signup({ onAuthenticated }: Props) {
  const [organizationName, setOrganizationName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    const policyError = passwordPolicyError(password);
    if (policyError) {
      setError(policyError);
      return;
    }

    setSubmitting(true);
    try {
      await signup(organizationName, email, password);
      onAuthenticated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="panel auth-card">
        <div className="auth-brand">
          <HolographicAvatar active size={72} />
          <h1>Create your Sentinel AI account</h1>
          <p>Set up a new, isolated workspace for your organization</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <div className="auth-error">{error}</div> : null}
          <label>
            Organization name
            <input
              type="text"
              required
              value={organizationName}
              onChange={(event) => setOrganizationName(event.target.value)}
              placeholder="Acme Corp"
            />
          </label>
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
              autoComplete="new-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="At least 10 characters, 1 letter + 1 digit"
            />
          </label>
          <label>
            Confirm password
            <input
              type="password"
              autoComplete="new-password"
              required
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              placeholder="••••••••••"
            />
          </label>
          <button className="auth-submit" type="submit" disabled={submitting}>
            {submitting ? 'Creating account...' : 'Create Account'}
          </button>
        </form>

        <div className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}

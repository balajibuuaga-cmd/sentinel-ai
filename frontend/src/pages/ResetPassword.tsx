import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { confirmPasswordReset } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

function passwordPolicyError(password: string): string | null {
  if (password.length < 10) {
    return 'Password must be at least 10 characters long.';
  }
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'Password must contain at least one letter and one digit.';
  }
  return null;
}

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (!token) {
      setError('This reset link is missing its token. Request a new one.');
      return;
    }
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
      await confirmPasswordReset(token, password);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reset password.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="panel auth-card">
        <div className="auth-brand">
          <HolographicAvatar active size={72} />
          <h1>Set a new password</h1>
          <p>Choose a new password for your account</p>
        </div>

        {done ? (
          <div className="auth-switch">
            Your password has been reset. <Link to="/login">Sign in</Link>
          </div>
        ) : (
          <form className="auth-form" onSubmit={handleSubmit}>
            {error ? <div className="auth-error">{error}</div> : null}
            <label>
              New password
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
              Confirm new password
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
              {submitting ? 'Resetting...' : 'Reset Password'}
            </button>
          </form>
        )}

        <div className="auth-switch">
          <Link to="/login">Back to sign in</Link>
        </div>
      </div>
    </div>
  );
}

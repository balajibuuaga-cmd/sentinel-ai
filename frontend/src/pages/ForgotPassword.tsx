import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { requestPasswordReset } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send reset email.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="panel auth-card">
        <div className="auth-brand">
          <HolographicAvatar active size={72} />
          <h1>Reset your password</h1>
          <p>We&apos;ll email you a link to set a new password</p>
        </div>

        {submitted ? (
          <div className="auth-switch">
            If an account exists for that email, a reset link is on its way. Check your inbox.
          </div>
        ) : (
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
            <button className="auth-submit" type="submit" disabled={submitting}>
              {submitting ? 'Sending...' : 'Send Reset Link'}
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

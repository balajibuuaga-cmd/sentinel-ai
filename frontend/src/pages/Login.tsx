import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { authStatus, login, verifyMfaChallenge } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

interface Props {
  onAuthenticated: () => void;
}

export default function Login({ onAuthenticated }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [challengeToken, setChallengeToken] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [cognitoLoginUrl, setCognitoLoginUrl] = useState<string | null>(null);

  useEffect(() => {
    const cancelled = { current: false };
    authStatus()
      .then((status) => {
        if (cancelled.current) return;
        if (status.cognitoConfigured && status.cognitoLoginUrl) {
          setCognitoLoginUrl(status.cognitoLoginUrl);
        }
      })
      .catch(() => {
        // Cognito is an optional extra sign-in path; silently skip if status can't be reached.
      });
    return () => {
      cancelled.current = true;
    };
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await login(email, password);
      if (result.mfaRequired && result.mfaChallengeToken) {
        setChallengeToken(result.mfaChallengeToken);
      } else {
        onAuthenticated();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifyCode(event: FormEvent) {
    event.preventDefault();
    if (!challengeToken) return;
    setSubmitting(true);
    setError(null);
    try {
      await verifyMfaChallenge(challengeToken, code);
      onAuthenticated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed.');
    } finally {
      setSubmitting(false);
    }
  }

  if (challengeToken) {
    return (
      <div className="auth-page">
        <div className="panel auth-card">
          <div className="auth-brand">
            <HolographicAvatar active size={72} />
            <h1>Sentinel AI</h1>
            <p>Enter the 6-digit code from your authenticator app</p>
          </div>

          <form className="auth-form" onSubmit={handleVerifyCode}>
            {error ? <div className="auth-error">{error}</div> : null}
            <label>
              Verification code
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                required
                maxLength={6}
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="123456"
              />
            </label>
            <button className="auth-submit" type="submit" disabled={submitting}>
              {submitting ? 'Verifying...' : 'Verify'}
            </button>
          </form>

          <div className="auth-switch">
            <button
              type="button"
              className="auth-link-button"
              onClick={() => {
                setChallengeToken(null);
                setCode('');
                setError(null);
              }}
            >
              Back to sign in
            </button>
          </div>
        </div>
      </div>
    );
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

        {cognitoLoginUrl ? (
          <>
            <div className="auth-divider">or</div>
            <a className="auth-submit auth-submit-secondary" href={cognitoLoginUrl}>
              Continue with Cognito
            </a>
          </>
        ) : null}

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

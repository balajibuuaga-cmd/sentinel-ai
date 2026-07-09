import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { KeyRound, Building2 } from 'lucide-react';
import { api } from '../api/client';
import { humanize } from '../api/transform';
import type { AccountProfile } from '../api/types';

function passwordPolicyError(password: string): string | null {
  if (password.length < 10) {
    return 'Password must be at least 10 characters long.';
  }
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'Password must contain at least one letter and one digit.';
  }
  return null;
}

function formatDate(iso: string | null) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export default function Settings() {
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const cancelled = { current: false };
    api
      .accountProfile()
      .then((data) => {
        if (cancelled.current) return;
        setProfile(data);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load account profile');
        setLoading(false);
      });
    return () => {
      cancelled.current = true;
    };
  }, []);

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(false);

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }
    const policyError = passwordPolicyError(newPassword);
    if (policyError) {
      setPasswordError(policyError);
      return;
    }

    setSubmitting(true);
    try {
      await api.changePassword(currentPassword, newPassword);
      setPasswordSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <div className="page-empty-state">Loading account settings...</div>;
  }

  return (
    <div className="team-page">
      {error ? <div className="integrations-error">{error}</div> : null}

      {profile ? (
        <div className="panel team-invite-card">
          <div className="chart-card-header">
            <Building2 size={15} /> Account
          </div>
          <div className="operator-list">
            <div className="operator-row">
              <span className="rec-badge tone-good">{humanize(profile.role)}</span>
              <div className="operator-row-body">
                <div className="operator-row-title">{profile.email}</div>
                <div className="operator-row-meta">
                  {profile.organizationName} &middot; Joined {formatDate(profile.createdAt)} &middot; Last login{' '}
                  {formatDate(profile.lastLoginAt)}
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <div className="panel team-invite-card">
        <div className="chart-card-header">
          <KeyRound size={15} /> Change Password
        </div>
        <form className="auth-form" onSubmit={handleChangePassword}>
          {passwordError ? <div className="auth-error">{passwordError}</div> : null}
          {passwordSuccess ? <div className="team-role-label">Password updated successfully.</div> : null}
          <label>
            Current password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              autoComplete="new-password"
              required
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
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
            />
          </label>
          <button className="auth-submit" type="submit" disabled={submitting}>
            {submitting ? 'Updating...' : 'Update Password'}
          </button>
        </form>
      </div>
    </div>
  );
}

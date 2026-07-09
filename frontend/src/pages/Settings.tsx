import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { KeyRound, Building2, ShieldCheck } from 'lucide-react';
import { api } from '../api/client';
import { humanize } from '../api/transform';
import type { AccountProfile, MfaEnrollResponse } from '../api/types';

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

  const [mfaEnrollment, setMfaEnrollment] = useState<MfaEnrollResponse | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [mfaError, setMfaError] = useState<string | null>(null);
  const [mfaSubmitting, setMfaSubmitting] = useState(false);
  const [disablePassword, setDisablePassword] = useState('');
  const [disableError, setDisableError] = useState<string | null>(null);
  const [disableSubmitting, setDisableSubmitting] = useState(false);
  const [showDisableForm, setShowDisableForm] = useState(false);

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

  async function handleStartMfaEnrollment() {
    setMfaError(null);
    setMfaSubmitting(true);
    try {
      const enrollment = await api.enrollMfa();
      setMfaEnrollment(enrollment);
    } catch (err) {
      setMfaError(err instanceof Error ? err.message : 'Failed to start MFA enrollment');
    } finally {
      setMfaSubmitting(false);
    }
  }

  async function handleConfirmMfa(event: FormEvent) {
    event.preventDefault();
    setMfaError(null);
    setMfaSubmitting(true);
    try {
      await api.confirmMfa(mfaCode);
      setMfaEnrollment(null);
      setMfaCode('');
      setProfile((prev) => (prev ? { ...prev, mfaEnabled: true } : prev));
    } catch (err) {
      setMfaError(err instanceof Error ? err.message : 'Failed to confirm MFA code');
    } finally {
      setMfaSubmitting(false);
    }
  }

  async function handleDisableMfa(event: FormEvent) {
    event.preventDefault();
    setDisableError(null);
    setDisableSubmitting(true);
    try {
      await api.disableMfa(disablePassword);
      setProfile((prev) => (prev ? { ...prev, mfaEnabled: false } : prev));
      setShowDisableForm(false);
      setDisablePassword('');
    } catch (err) {
      setDisableError(err instanceof Error ? err.message : 'Failed to disable MFA');
    } finally {
      setDisableSubmitting(false);
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

      <div className="panel team-invite-card">
        <div className="chart-card-header">
          <ShieldCheck size={15} /> Two-Factor Authentication
        </div>

        {mfaError ? <div className="auth-error">{mfaError}</div> : null}

        {profile?.mfaEnabled ? (
          <>
            <div className="operator-list">
              <div className="operator-row">
                <span className="rec-badge tone-good">Enabled</span>
                <div className="operator-row-body">
                  <div className="operator-row-title">Two-factor authentication is protecting your account.</div>
                </div>
              </div>
            </div>
            {showDisableForm ? (
              <form className="auth-form" onSubmit={handleDisableMfa}>
                {disableError ? <div className="auth-error">{disableError}</div> : null}
                <label>
                  Current password
                  <input
                    type="password"
                    autoComplete="current-password"
                    required
                    value={disablePassword}
                    onChange={(event) => setDisablePassword(event.target.value)}
                  />
                </label>
                <button className="auth-submit" type="submit" disabled={disableSubmitting}>
                  {disableSubmitting ? 'Disabling...' : 'Confirm Disable'}
                </button>
              </form>
            ) : (
              <button
                className="auth-link-button"
                type="button"
                onClick={() => setShowDisableForm(true)}
              >
                Disable two-factor authentication
              </button>
            )}
          </>
        ) : mfaEnrollment ? (
          <form className="auth-form" onSubmit={handleConfirmMfa}>
            <p className="operator-row-meta">
              Scan this into your authenticator app, or enter the key manually, then confirm with a code.
            </p>
            <label>
              Secret key (manual entry)
              <input type="text" readOnly value={mfaEnrollment.secret} />
            </label>
            <label>
              Confirmation code
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                required
                maxLength={6}
                value={mfaCode}
                onChange={(event) => setMfaCode(event.target.value)}
                placeholder="123456"
              />
            </label>
            <button className="auth-submit" type="submit" disabled={mfaSubmitting}>
              {mfaSubmitting ? 'Confirming...' : 'Confirm and Enable'}
            </button>
          </form>
        ) : (
          <>
            <p className="operator-row-meta">
              Add an extra layer of security to your account using an authenticator app.
            </p>
            <button className="auth-submit" type="button" disabled={mfaSubmitting} onClick={handleStartMfaEnrollment}>
              {mfaSubmitting ? 'Starting...' : 'Enable Two-Factor Authentication'}
            </button>
          </>
        )}
      </div>
    </div>
  );
}

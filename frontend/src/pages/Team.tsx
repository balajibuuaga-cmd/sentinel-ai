import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { UserPlus, Trash2, ShieldAlert } from 'lucide-react';
import { api, currentSession } from '../api/client';
import { humanize } from '../api/transform';
import type { TeamMember } from '../api/types';

const ROLES = ['ADMIN', 'RELEASE_MANAGER', 'VIEWER'];

function formatDate(iso: string | null) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export default function Team() {
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('VIEWER');
  const [inviteSubmitting, setInviteSubmitting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);

  const isAdmin = currentSession()?.role === 'ADMIN';

  useEffect(() => {
    const cancelled = { current: false };
    api
      .teamMembers()
      .then((data) => {
        if (cancelled.current) return;
        setMembers(data);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load team members');
        setLoading(false);
      });
    return () => {
      cancelled.current = true;
    };
  }, []);

  async function handleInvite(event: FormEvent) {
    event.preventDefault();
    setInviteSubmitting(true);
    setInviteError(null);
    try {
      const member = await api.inviteTeamMember({ email: inviteEmail, role: inviteRole });
      setMembers((prev) => [...prev, member]);
      setInviteEmail('');
      setInviteRole('VIEWER');
    } catch (err) {
      setInviteError(err instanceof Error ? err.message : 'Failed to invite team member');
    } finally {
      setInviteSubmitting(false);
    }
  }

  async function handleRoleChange(id: number, role: string) {
    setBusyId(id);
    setError(null);
    try {
      const updated = await api.updateTeamMemberRole(id, role);
      setMembers((prev) => prev.map((m) => (m.id === id ? updated : m)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update role');
    } finally {
      setBusyId(null);
    }
  }

  async function handleRemove(id: number) {
    setBusyId(id);
    setError(null);
    try {
      await api.removeTeamMember(id);
      setMembers((prev) => prev.filter((m) => m.id !== id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove team member');
    } finally {
      setBusyId(null);
    }
  }

  if (loading) {
    return <div className="page-empty-state">Loading team...</div>;
  }

  return (
    <div className="team-page">
      {error ? <div className="integrations-error">{error}</div> : null}

      {isAdmin ? (
        <div className="panel team-invite-card">
          <div className="chart-card-header">
            <UserPlus size={15} /> Invite Teammate
          </div>
          <form className="team-invite-form" onSubmit={handleInvite}>
            {inviteError ? <div className="auth-error">{inviteError}</div> : null}
            <input
              type="email"
              required
              placeholder="teammate@company.com"
              value={inviteEmail}
              onChange={(event) => setInviteEmail(event.target.value)}
            />
            <select value={inviteRole} onChange={(event) => setInviteRole(event.target.value)}>
              {ROLES.map((role) => (
                <option key={role} value={role}>
                  {humanize(role)}
                </option>
              ))}
            </select>
            <button className="auth-submit" type="submit" disabled={inviteSubmitting}>
              {inviteSubmitting ? 'Sending invite...' : 'Send Invite'}
            </button>
          </form>
        </div>
      ) : null}

      <div className="panel team-roster">
        <div className="chart-card-header">Team Members</div>
        {members.length === 0 ? (
          <div className="chart-empty">No team members yet.</div>
        ) : (
          <div className="operator-list">
            {members.map((member) => (
              <div key={member.id} className="operator-row">
                {member.locked ? (
                  <span className="rec-badge tone-bad" title="Account locked">
                    <ShieldAlert size={12} />
                  </span>
                ) : (
                  <span className="rec-badge tone-good">{member.you ? 'You' : 'Active'}</span>
                )}
                <div className="operator-row-body">
                  <div className="operator-row-title">{member.email}</div>
                  <div className="operator-row-meta">
                    Joined {formatDate(member.createdAt)} &middot; Last login {formatDate(member.lastLoginAt)}
                  </div>
                </div>
                {isAdmin && !member.you ? (
                  <select
                    value={member.role}
                    disabled={busyId === member.id}
                    onChange={(event) => handleRoleChange(member.id, event.target.value)}
                  >
                    {ROLES.map((role) => (
                      <option key={role} value={role}>
                        {humanize(role)}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span className="team-role-label">{humanize(member.role)}</span>
                )}
                {isAdmin && !member.you ? (
                  <button
                    className="action-btn tone-bad"
                    onClick={() => handleRemove(member.id)}
                    disabled={busyId === member.id}
                    title="Remove from team"
                  >
                    <Trash2 size={13} />
                  </button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

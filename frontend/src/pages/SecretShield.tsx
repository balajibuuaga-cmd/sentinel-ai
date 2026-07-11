import { useState } from 'react';
import type { FormEvent } from 'react';
import { ShieldCheck, ShieldAlert, ShieldX, Sparkles } from 'lucide-react';
import { api } from '../api/client';
import type { SecretFinding, SecretScanResponse } from '../api/types';

const SAMPLE = `// paste a diff, file, or config snippet
export const config = {
  awsKey: "AKIAIOSFODNN7EXAMPLE",
  dbUrl: "postgres://app:s3cr3tPassw0rd@db.internal:5432/app",
  publicId: "user-1234-abcd",        // not a secret
  timeout: 30000,
};`;

const verdictMeta: Record<string, { label: string; tone: string; icon: typeof ShieldCheck }> = {
  BLOCKED: { label: 'Blocked', tone: 'bad', icon: ShieldX },
  WARN: { label: 'Possible secret', tone: 'warn', icon: ShieldAlert },
  CLEARED: { label: 'Cleared', tone: 'good', icon: ShieldCheck },
};

function FindingRow({ finding }: { finding: SecretFinding }) {
  const meta = verdictMeta[finding.verdict];
  const Icon = meta.icon;
  return (
    <div className={`shield-finding shield-${meta.tone}`}>
      <div className="shield-finding-head">
        <Icon size={15} />
        <span className={`rec-badge tone-${meta.tone}`}>{meta.label}</span>
        <span className="shield-finding-line">line {finding.line}</span>
        <span className="shield-finding-category">{finding.category}</span>
        <span className="shield-finding-gate">
          {finding.source === 'SCANNER' ? 'Scanner → Downgrade gate' : 'Risk gate'}
          {finding.aiJudged ? (
            <span className="shield-ai-tag">
              <Sparkles size={11} /> AI judged
            </span>
          ) : null}
        </span>
      </div>
      <code className="shield-finding-snippet">{finding.maskedSnippet}</code>
      <p className="shield-finding-reason">{finding.reason}</p>
    </div>
  );
}

export default function SecretShield() {
  const [content, setContent] = useState('');
  const [filename, setFilename] = useState('');
  const [result, setResult] = useState<SecretScanResponse | null>(null);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleScan(event: FormEvent) {
    event.preventDefault();
    if (!content.trim()) return;
    setScanning(true);
    setError(null);
    try {
      const response = await api.scanForSecrets(content, filename.trim());
      setResult(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Scan failed');
    } finally {
      setScanning(false);
    }
  }

  return (
    <div className="team-page">
      <div className="panel team-invite-card">
        <div className="chart-card-header">
          <ShieldCheck size={15} /> Secret Shield
        </div>
        <p className="operator-row-meta">
          A deterministic secret scanner gated by two AI judgments. Scanner hits go to a downgrade
          gate that sees only masked values and clears confident false alarms; secret-looking lines
          the scanner missed go to a risk gate. Raw secret values never leave the server — findings
          show masked snippets only, and nothing you paste is stored.
        </p>
        <form className="auth-form" onSubmit={handleScan}>
          <label>
            Filename (optional, helps the AI judge by language)
            <input
              type="text"
              value={filename}
              onChange={(event) => setFilename(event.target.value)}
              placeholder="config.ts"
            />
          </label>
          <label>
            Content to scan
            <textarea
              className="shield-textarea"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder={SAMPLE}
              rows={12}
              spellCheck={false}
            />
          </label>
          {error ? <div className="auth-error">{error}</div> : null}
          <div className="shield-actions">
            <button className="auth-submit" type="submit" disabled={scanning || !content.trim()}>
              {scanning ? 'Scanning...' : 'Scan for secrets'}
            </button>
            <button
              type="button"
              className="auth-link-button"
              onClick={() => {
                setContent(SAMPLE);
                setFilename('config.ts');
                setResult(null);
              }}
            >
              Load example
            </button>
          </div>
        </form>
      </div>

      {result ? (
        <div className="panel team-invite-card">
          <div className="chart-card-header">
            {result.wouldBlockCommit ? <ShieldX size={15} /> : <ShieldCheck size={15} />} Scan Result
          </div>
          <div className={`shield-verdict-banner ${result.wouldBlockCommit ? 'shield-bad' : 'shield-good'}`}>
            {result.wouldBlockCommit
              ? `Commit would be BLOCKED — ${result.blockedCount} unresolved secret${result.blockedCount === 1 ? '' : 's'} found.`
              : 'No blocking secrets — this commit would be allowed.'}
          </div>
          <div className="ai-usage-stats">
            <div className="ai-usage-stat">
              <div className="ai-usage-stat-value">{result.linesScanned}</div>
              <div className="ai-usage-stat-label">Lines scanned</div>
            </div>
            <div className="ai-usage-stat">
              <div className={`ai-usage-stat-value ${result.blockedCount > 0 ? 'ai-usage-stat-warn' : ''}`}>
                {result.blockedCount}
              </div>
              <div className="ai-usage-stat-label">Blocked</div>
            </div>
            <div className="ai-usage-stat">
              <div className="ai-usage-stat-value">{result.warnedCount}</div>
              <div className="ai-usage-stat-label">Warned (missed by scanner)</div>
            </div>
            <div className="ai-usage-stat">
              <div className="ai-usage-stat-value">{result.clearedCount}</div>
              <div className="ai-usage-stat-label">Cleared false alarms</div>
            </div>
          </div>
          {!result.aiGateAvailable ? (
            <p className="operator-row-meta">
              AI gates are inactive in this environment, so the deterministic scanner runs alone:
              hits stay blocked conservatively and cleared-false-alarm downgrades are unavailable.
            </p>
          ) : null}

          {result.findings.length === 0 ? (
            <div className="chart-empty">No secret-shaped content detected.</div>
          ) : (
            <div className="shield-findings">
              {result.findings.map((finding, index) => (
                <FindingRow key={`${finding.line}-${index}`} finding={finding} />
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

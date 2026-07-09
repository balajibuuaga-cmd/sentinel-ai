import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { exchangeCognitoCode } from '../api/client';
import HolographicAvatar from '../components/HolographicAvatar';

interface Props {
  onAuthenticated: () => void;
}

export default function CognitoCallback({ onAuthenticated }: Props) {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get('code');
    const oauthError = searchParams.get('error_description') || searchParams.get('error');
    if (oauthError) {
      setError(oauthError);
      return;
    }
    if (!code) {
      setError('No authorization code was returned by Cognito.');
      return;
    }
    const redirectUri = window.location.origin + '/auth/cognito/callback';
    exchangeCognitoCode(code, redirectUri)
      .then(onAuthenticated)
      .catch((err) => setError(err instanceof Error ? err.message : 'Cognito sign-in failed.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="auth-page">
      <div className="panel auth-card">
        <div className="auth-brand">
          <HolographicAvatar active size={72} />
          <h1>Sentinel AI</h1>
          <p>{error ? 'Sign-in failed' : 'Completing sign-in with Cognito...'}</p>
        </div>

        {error ? (
          <>
            <div className="auth-error">{error}</div>
            <div className="auth-switch">
              <Link to="/login">Back to sign in</Link>
            </div>
          </>
        ) : null}
      </div>
    </div>
  );
}

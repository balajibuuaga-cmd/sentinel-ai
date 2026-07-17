#!/bin/sh
set -eu

# Creates (or updates) the AWS Secrets Manager secret that the Sentinel AI
# backend loads at boot via SecretsManagerEnvironmentPostProcessor, then prints
# the IAM policy the EC2 instance role needs to read it.
#
# The secret is a flat JSON object whose KEYS are the existing environment
# variable names (SENTINEL_JWT_SECRET, SPRING_DATASOURCE_PASSWORD, ...). At boot
# the app resolves ${SENTINEL_JWT_SECRET:...} placeholders straight from it, so
# no application.properties change is needed.
#
# Usage:
#   scripts/setup-secrets.sh path/to/prod-secrets.env
#
# where prod-secrets.env holds ONE secret per line as KEY=VALUE, e.g.
#   SENTINEL_JWT_SECRET=<long-random-string>
#   SPRING_DATASOURCE_PASSWORD=<rds-password>
#   SENTINEL_GITHUB_WEBHOOK_SECRET=<webhook-secret>
#   SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY=<key>
#   SENTINEL_COGNITO_CLIENT_SECRET=<secret>        # only if Cognito is enabled
#
# This file is INPUT ONLY — never commit it. Delete it after running.

SECRET_NAME="${SENTINEL_SECRET_NAME:-sentinel-ai/prod}"
AWS_REGION="${SENTINEL_SECRETS_REGION:-us-east-1}"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}
need aws
need python3

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Usage: $0 path/to/prod-secrets.env" >&2
  exit 1
fi
ENV_FILE="$1"

# Convert KEY=VALUE lines into a JSON object with python3 (safe escaping;
# ignores blank lines and # comments; splits only on the first '=').
SECRET_JSON="$(python3 - "$ENV_FILE" <<'PY'
import json, sys
out = {}
with open(sys.argv[1]) as fh:
    for line in fh:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        out[key.strip()] = value
print(json.dumps(out))
PY
)"

COUNT="$(printf '%s' "$SECRET_JSON" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')"
if [ "$COUNT" -eq 0 ]; then
  echo "No KEY=VALUE pairs found in $ENV_FILE" >&2
  exit 1
fi
echo "Prepared $COUNT secret key(s) for '$SECRET_NAME' in $AWS_REGION."

if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
  echo "Secret exists — updating its value."
  aws secretsmanager put-secret-value \
    --secret-id "$SECRET_NAME" \
    --secret-string "$SECRET_JSON" \
    --region "$AWS_REGION" >/dev/null
else
  echo "Secret does not exist — creating it."
  aws secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --description "Sentinel AI production secrets (loaded at boot by the app)." \
    --secret-string "$SECRET_JSON" \
    --region "$AWS_REGION" >/dev/null
fi

SECRET_ARN="$(aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$AWS_REGION" --query ARN --output text)"
echo "Done. Secret ARN: $SECRET_ARN"

cat <<EOF

------------------------------------------------------------------
NEXT STEPS (do these once, with an appropriately-permissioned role)
------------------------------------------------------------------
1. Attach this least-privilege policy to the EC2 instance role so the app
   can read ONLY this secret:

{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "$SECRET_ARN"
    }
  ]
}

2. On the box, point the app at the secret and REMOVE the plaintext secret
   values from the systemd EnvironmentFile. Keep only non-secret config plus:

     SENTINEL_SECRETS_ID=$SECRET_NAME
     SENTINEL_SECRETS_REGION=$AWS_REGION

3. Restart: sudo systemctl restart sentinel-ai
   The log should show: "Loaded N secret(s) from AWS Secrets Manager id '$SECRET_NAME'."
   If the role can't read the secret, the app refuses to boot (by design) —
   it will not fall back to dev defaults.

4. Rotate the RDS password / JWT secret now that they live in one managed
   place, and shred the local $ENV_FILE.
------------------------------------------------------------------
EOF

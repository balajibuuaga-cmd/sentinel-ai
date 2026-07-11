#!/bin/sh
set -eu

# Manually revert the running app to the previous release that deploy.sh
# preserved on the server (/opt/sentinel-ai/app.jar.prev and
# /usr/share/nginx/html.prev). Use this when a deploy passed its health check
# but turns out to be bad in production. Run from the repo root:
#   ./scripts/rollback.sh

EC2_HOST="${SENTINEL_EC2_HOST:-3.90.3.12}"
EC2_USER="${SENTINEL_EC2_USER:-ec2-user}"
SSH_KEY="${SENTINEL_EC2_SSH_KEY:-$HOME/.ssh/sentinel-ai-deploy-key}"
APP_URL="${SENTINEL_APP_URL:-https://getsentinelai.dev}"

if [ ! -f "$SSH_KEY" ]; then
  echo "SSH key not found at $SSH_KEY (set SENTINEL_EC2_SSH_KEY to override)." >&2
  exit 1
fi

echo "==> Reverting to the previous release on $EC2_HOST..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$EC2_USER@$EC2_HOST" "sudo bash -s" << 'REMOTESCRIPT'
set -e
if [ ! -f /opt/sentinel-ai/app.jar.prev ]; then
  echo "No previous release found at /opt/sentinel-ai/app.jar.prev - nothing to roll back to." >&2
  exit 1
fi
# Swap current <-> previous so a rollback is itself reversible.
mv -f /opt/sentinel-ai/app.jar /opt/sentinel-ai/app.jar.rollback-from
mv -f /opt/sentinel-ai/app.jar.prev /opt/sentinel-ai/app.jar
mv -f /opt/sentinel-ai/app.jar.rollback-from /opt/sentinel-ai/app.jar.prev
chown sentinel:sentinel /opt/sentinel-ai/app.jar

if [ -d /usr/share/nginx/html.prev ]; then
  rm -rf /usr/share/nginx/html.rollback-from
  mv /usr/share/nginx/html /usr/share/nginx/html.rollback-from
  mv /usr/share/nginx/html.prev /usr/share/nginx/html
  mv /usr/share/nginx/html.rollback-from /usr/share/nginx/html.prev
  chown -R nginx:nginx /usr/share/nginx/html
fi
systemctl restart sentinel-ai
REMOTESCRIPT

echo "==> Waiting for the app to come back up..."
attempts=0
code=0
while [ "$attempts" -lt 30 ]; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/api/auth/status" || echo "000")
  [ "$code" = "200" ] && break
  attempts=$((attempts + 1))
  sleep 2
done

if [ "$code" = "200" ]; then
  echo "==> Rolled back. $APP_URL is live on the previous release."
else
  echo "Rollback finished but health check did not pass (last status: $code)." >&2
  exit 1
fi

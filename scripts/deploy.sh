#!/bin/sh
set -eu

# Rebuilds the backend jar and frontend bundle, ships them to the running
# EC2 instance, and restarts the app. Run from the repo root:
#   ./scripts/deploy.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EC2_HOST="${SENTINEL_EC2_HOST:-3.90.3.12}"
EC2_USER="${SENTINEL_EC2_USER:-ec2-user}"
SSH_KEY="${SENTINEL_EC2_SSH_KEY:-$HOME/.ssh/sentinel-ai-deploy-key}"
APP_URL="${SENTINEL_APP_URL:-https://getsentinelai.dev}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

need ssh
need scp
need curl

if [ ! -f "$SSH_KEY" ]; then
  echo "SSH key not found at $SSH_KEY (set SENTINEL_EC2_SSH_KEY to override)." >&2
  exit 1
fi

echo "==> Building backend jar..."
(cd "$REPO_ROOT" && ./mvnw -q -f backend/pom.xml -DskipTests package)
JAR_PATH="$REPO_ROOT/backend/target/sentinel-ai-0.0.1-SNAPSHOT.jar"

echo "==> Building frontend..."
(cd "$REPO_ROOT/frontend" && npm run build >/dev/null)
DIST_PATH="$REPO_ROOT/frontend/dist"

echo "==> Shipping jar and frontend to $EC2_HOST..."
scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$JAR_PATH" "$EC2_USER@$EC2_HOST:/tmp/sentinel-ai-new.jar"
scp -i "$SSH_KEY" -r "$DIST_PATH" "$EC2_USER@$EC2_HOST:/tmp/frontend-dist-new"

echo "==> Swapping files in and restarting service..."
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_HOST" "sudo bash -s" << 'REMOTESCRIPT'
set -e
mv /tmp/sentinel-ai-new.jar /opt/sentinel-ai/app.jar
rm -rf /usr/share/nginx/html
mv /tmp/frontend-dist-new /usr/share/nginx/html
chown sentinel:sentinel /opt/sentinel-ai/app.jar
chown -R nginx:nginx /usr/share/nginx/html
systemctl restart sentinel-ai
REMOTESCRIPT

echo "==> Waiting for the app to come back up..."
ATTEMPTS=0
MAX_ATTEMPTS=30
STATUS_CODE=0
while [ "$ATTEMPTS" -lt "$MAX_ATTEMPTS" ]; do
  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/api/auth/status" || echo "000")
  if [ "$STATUS_CODE" = "200" ]; then
    break
  fi
  ATTEMPTS=$((ATTEMPTS + 1))
  sleep 2
done

if [ "$STATUS_CODE" != "200" ]; then
  echo "Deployment finished but /api/auth/status did not come up after ${MAX_ATTEMPTS}x2s (last status: $STATUS_CODE). Check the server logs:" >&2
  echo "  ssh -i $SSH_KEY $EC2_USER@$EC2_HOST 'sudo journalctl -u sentinel-ai -n 50'" >&2
  exit 1
fi

echo "==> Deployed successfully. $APP_URL is live."

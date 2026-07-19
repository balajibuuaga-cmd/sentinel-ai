#!/bin/sh
set -eu

# Rebuilds the backend jar and frontend bundle, ships them to the running
# EC2 instance, and restarts the app. The previous release is kept on the
# server so a bad deploy can be reverted instantly. If the post-deploy health
# check fails, this script auto-rolls back to the previous release before
# exiting non-zero. Run from the repo root:
#   ./scripts/deploy.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EC2_HOST="${SENTINEL_EC2_HOST:-3.216.127.47}"
EC2_USER="${SENTINEL_EC2_USER:-ec2-user}"
SSH_KEY="${SENTINEL_EC2_SSH_KEY:-$HOME/.ssh/sentinel-ai-deploy-key}"
APP_URL="${SENTINEL_APP_URL:-https://getsentinelai.dev}"
SECURITY_GROUP="${SENTINEL_EC2_SECURITY_GROUP:-sg-08d39a17aecdc7858}"
AWS_REGION="${SENTINEL_AWS_REGION:-us-east-1}"

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

ensure_ssh_access() {
  # SSH is locked to a single /32 so the box is not exposed to the internet.
  # That means a change of network (coffee shop, hotspot, new ISP lease) makes
  # deploys hang on a dropped connection rather than fail fast. Whitelist the
  # current public IP if it is not already allowed, and drop any other /32 so
  # stale addresses do not accumulate.
  #
  # Skip with SENTINEL_SKIP_SG_CHECK=1, or when the AWS CLI is unavailable.
  if [ "${SENTINEL_SKIP_SG_CHECK:-0}" = "1" ]; then
    return 0
  fi
  if ! command -v aws >/dev/null 2>&1; then
    echo "==> aws CLI not found; skipping SSH whitelist check."
    return 0
  fi

  my_ip=$(curl -s --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')
  if [ -z "$my_ip" ]; then
    echo "==> Could not determine public IP; skipping SSH whitelist check."
    return 0
  fi

  # describe-security-group-rules returns one flat row per rule, so the port
  # filter can be done in awk. A JMESPath [?FromPort==`22`] filter is avoided
  # deliberately: the backticks JMESPath needs for number literals do not
  # survive shell quoting reliably and silently yield an empty result.
  rules=$(aws ec2 describe-security-group-rules --region "$AWS_REGION" \
    --filters "Name=group-id,Values=$SECURITY_GROUP" \
    --query 'SecurityGroupRules[].[FromPort,CidrIpv4,SecurityGroupRuleId,IsEgress]' \
    --output text 2>/dev/null | awk '$1==22 && $4=="False" {print $2, $3}')

  if echo "$rules" | awk '{print $1}' | grep -qx "$my_ip/32"; then
    echo "==> SSH already allows $my_ip."
    return 0
  fi

  echo "==> Whitelisting $my_ip for SSH..."
  aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
    --group-id "$SECURITY_GROUP" \
    --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$my_ip/32,Description=\"deploy access\"}]" \
    >/dev/null

  # Drop the previous addresses now that the current one is in place, so stale
  # /32s from old networks do not accumulate as standing SSH exposure.
  echo "$rules" | while read -r cidr rule_id; do
    if [ -n "$rule_id" ] && [ "$cidr" != "$my_ip/32" ]; then
      echo "==> Revoking stale SSH rule $cidr."
      aws ec2 revoke-security-group-ingress --region "$AWS_REGION" \
        --group-id "$SECURITY_GROUP" \
        --security-group-rule-ids "$rule_id" >/dev/null 2>&1 || true
    fi
  done
}

health_ok() {
  # Poll the app health endpoint until 200 or timeout. Returns 0 on healthy.
  attempts=0
  while [ "$attempts" -lt 30 ]; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/api/auth/status" || echo "000")
    if [ "$code" = "200" ]; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 2
  done
  return 1
}

ensure_ssh_access

echo "==> Building backend jar..."
(cd "$REPO_ROOT" && ./mvnw -q -f backend/pom.xml -DskipTests package)
JAR_PATH="$REPO_ROOT/backend/target/sentinel-ai-0.0.1-SNAPSHOT.jar"

echo "==> Building frontend..."
(cd "$REPO_ROOT/frontend" && npm run build >/dev/null)
DIST_PATH="$REPO_ROOT/frontend/dist"

echo "==> Shipping jar and frontend to $EC2_HOST..."
scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$JAR_PATH" "$EC2_USER@$EC2_HOST:/tmp/sentinel-ai-new.jar"
scp -i "$SSH_KEY" -r "$DIST_PATH" "$EC2_USER@$EC2_HOST:/tmp/frontend-dist-new"

echo "==> Swapping in new release (keeping previous for rollback)..."
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_HOST" "sudo bash -s" << 'REMOTESCRIPT'
set -e
# Preserve the currently-running release as .prev before overwriting.
if [ -f /opt/sentinel-ai/app.jar ]; then
  cp -f /opt/sentinel-ai/app.jar /opt/sentinel-ai/app.jar.prev
fi
if [ -d /usr/share/nginx/html ]; then
  rm -rf /usr/share/nginx/html.prev
  cp -a /usr/share/nginx/html /usr/share/nginx/html.prev
fi

mv /tmp/sentinel-ai-new.jar /opt/sentinel-ai/app.jar
rm -rf /usr/share/nginx/html
mv /tmp/frontend-dist-new /usr/share/nginx/html
chown sentinel:sentinel /opt/sentinel-ai/app.jar
chown -R nginx:nginx /usr/share/nginx/html
systemctl restart sentinel-ai
REMOTESCRIPT

echo "==> Waiting for the app to come back up..."
if health_ok; then
  echo "==> Deployed successfully. $APP_URL is live."
  exit 0
fi

echo "Health check failed after deploy. Rolling back to the previous release..." >&2
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_HOST" "sudo bash -s" << 'ROLLBACKSCRIPT'
set -e
if [ -f /opt/sentinel-ai/app.jar.prev ]; then
  mv -f /opt/sentinel-ai/app.jar.prev /opt/sentinel-ai/app.jar
  chown sentinel:sentinel /opt/sentinel-ai/app.jar
fi
if [ -d /usr/share/nginx/html.prev ]; then
  rm -rf /usr/share/nginx/html
  mv /usr/share/nginx/html.prev /usr/share/nginx/html
  chown -R nginx:nginx /usr/share/nginx/html
fi
systemctl restart sentinel-ai
ROLLBACKSCRIPT

if health_ok; then
  echo "Rolled back to the previous release. $APP_URL is healthy on the old version." >&2
else
  echo "Rollback attempted but health check still failing. Inspect the server:" >&2
  echo "  ssh -i $SSH_KEY $EC2_USER@$EC2_HOST 'sudo journalctl -u sentinel-ai -n 80'" >&2
fi
exit 1

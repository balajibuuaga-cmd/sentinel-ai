#!/bin/sh
set -eu

# One-time setup of real uptime monitoring + alerting for the production app.
#
# Wires: Route 53 health check on https://getsentinelai.dev/api/auth/status
#        -> CloudWatch alarm on HealthCheckStatus
#        -> SNS topic -> email to the operator.
#
# When the health endpoint fails from multiple AWS regions, the alarm fires and
# SNS emails you - so you find out about an outage before your users do.
#
# Requires AWS credentials with route53, cloudwatch, and sns permissions.
# Idempotent: re-running reuses existing resources by name/reference.
#
#   ALERT_EMAIL=you@example.com ./scripts/setup-monitoring.sh

ALERT_EMAIL="${ALERT_EMAIL:-balajibuuaga@gmail.com}"
HEALTH_FQDN="${SENTINEL_HEALTH_FQDN:-getsentinelai.dev}"
HEALTH_PATH="${SENTINEL_HEALTH_PATH:-/api/auth/status}"
REGION="${AWS_REGION:-us-east-1}"
TOPIC_NAME="sentinel-ai-alerts"
ALARM_NAME="sentinel-ai-health"
CALLER_REF="sentinel-ai-health-$(date +%Y%m%d)"

echo "==> Ensuring SNS topic '$TOPIC_NAME'..."
TOPIC_ARN=$(aws sns create-topic --name "$TOPIC_NAME" --region "$REGION" --query 'TopicArn' --output text)
echo "    $TOPIC_ARN"

echo "==> Subscribing $ALERT_EMAIL (check your inbox to confirm)..."
already=$(aws sns list-subscriptions-by-topic --topic-arn "$TOPIC_ARN" --region "$REGION" \
  --query "Subscriptions[?Endpoint=='$ALERT_EMAIL'] | [0].SubscriptionArn" --output text 2>/dev/null || echo "None")
if [ "$already" = "None" ] || [ -z "$already" ]; then
  aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol email --notification-endpoint "$ALERT_EMAIL" --region "$REGION" >/dev/null
  echo "    Subscription requested - confirm the email to start receiving alerts."
else
  echo "    Already subscribed ($already)."
fi

echo "==> Ensuring Route 53 health check on https://$HEALTH_FQDN$HEALTH_PATH..."
HC_ID=$(aws route53 list-health-checks \
  --query "HealthChecks[?HealthCheckConfig.FullyQualifiedDomainName=='$HEALTH_FQDN' && HealthCheckConfig.ResourcePath=='$HEALTH_PATH'] | [0].Id" \
  --output text 2>/dev/null || echo "None")
if [ "$HC_ID" = "None" ] || [ -z "$HC_ID" ]; then
  HC_ID=$(aws route53 create-health-check \
    --caller-reference "$CALLER_REF" \
    --health-check-config "Type=HTTPS,FullyQualifiedDomainName=$HEALTH_FQDN,ResourcePath=$HEALTH_PATH,Port=443,RequestInterval=30,FailureThreshold=3,MeasureLatency=true" \
    --query 'HealthCheck.Id' --output text)
  aws route53 change-tags-for-resource --resource-type healthcheck --resource-id "$HC_ID" \
    --add-tags "Key=Name,Value=sentinel-ai-health" >/dev/null 2>&1 || true
  echo "    Created health check $HC_ID"
else
  echo "    Reusing health check $HC_ID"
fi

# Route 53 health-check metrics live in us-east-1 regardless of app region.
echo "==> Ensuring CloudWatch alarm '$ALARM_NAME'..."
aws cloudwatch put-metric-alarm \
  --alarm-name "$ALARM_NAME" \
  --alarm-description "Sentinel AI health endpoint is failing from Route 53 checkers." \
  --namespace "AWS/Route53" \
  --metric-name HealthCheckStatus \
  --dimensions "Name=HealthCheckId,Value=$HC_ID" \
  --statistic Minimum \
  --period 60 \
  --evaluation-periods 2 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --treat-missing-data breaching \
  --alarm-actions "$TOPIC_ARN" \
  --ok-actions "$TOPIC_ARN" \
  --region us-east-1
echo "    Alarm set: pages $TOPIC_NAME when health is down for 2 consecutive minutes."

echo "==> Monitoring is configured."
echo "    - Health check:  $HC_ID (HTTPS $HEALTH_FQDN$HEALTH_PATH every 30s)"
echo "    - Alarm:         $ALARM_NAME -> SNS $TOPIC_NAME"
echo "    - Confirm the SNS subscription email to $ALERT_EMAIL to receive pages."

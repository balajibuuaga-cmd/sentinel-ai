#!/bin/sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8090}"
USERNAME="${SENTINEL_DEMO_USER:-release@sentinel.ai}"
PASSWORD="${SENTINEL_DEMO_PASSWORD:-sentinel-release}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$1"
}

api_get() {
  curl -fsS "$BASE_URL$1" -H "Authorization: Bearer $TOKEN"
}

api_post() {
  curl -fsS "$BASE_URL$1" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$2"
}

need curl
need python3

echo "Sentinel AI demo against $BASE_URL"
echo

LOGIN_RESPONSE="$(curl -fsS "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | json_field token)"

if [ -z "$TOKEN" ]; then
  echo "Login failed. Is Sentinel running at $BASE_URL?" >&2
  exit 1
fi

echo "1. Executive briefing"
api_get /api/briefing/executive | python3 -m json.tool
echo

echo "2. Architecture Brain"
api_get /api/architecture/brain | python3 -m json.tool
echo

echo "3. Connect production integrations"
api_get /api/integration-connections | python3 -m json.tool
api_post /api/integration-connections/GITHUB/install '{
  "externalAccount": "sentinel-ai/engineering",
  "code": "demo-github-code",
  "state": "sentinel-demo"
}' | python3 -m json.tool
api_post /api/integration-connections/JIRA/install '{
  "externalAccount": "sentinel-ai.atlassian.net",
  "code": "demo-jira-code",
  "state": "sentinel-demo"
}' | python3 -m json.tool
api_post /api/integration-connections/CI/install '{
  "externalAccount": "Sentinel AI delivery pipelines",
  "code": "demo-ci-code",
  "state": "sentinel-demo"
}' | python3 -m json.tool
api_get /api/integration-connections/sync-history | python3 -m json.tool
echo

echo "4. Ask AI Engineer to review PR #418"
PR_REVIEW="$(api_post /api/pr-reviews/simulate '{
  "repository": "sentinel-ai/payment-api",
  "prNumber": 418,
  "title": "Refactor payment authorization and settlement retry path",
  "author": "david",
  "serviceName": "payment-api",
  "ownerTeam": "Payments Platform",
  "ciStatus": "failure",
  "changedFiles": [
    "src/payments/AuthorizePayment.java",
    "src/payments/SettlementRetryPolicy.java",
    "db/migration/V43__payment_authorization_index.sql"
  ]
}')"
printf '%s' "$PR_REVIEW" | python3 -m json.tool
PR_ID="$(printf '%s' "$PR_REVIEW" | json_field id)"
echo

echo "5. Ask Sentinel if this PR should merge"
api_post /api/ai/command "{\"command\":\"Should I merge this pull request?\",\"deploymentId\":null}" | python3 -m json.tool
echo

echo "6. Record a PR decision"
api_post "/api/pr-reviews/$PR_ID/decision" '{
  "decision": "WAITING",
  "actor": "release@sentinel.ai",
  "note": "Waiting for integration tests and migration review."
}' | python3 -m json.tool
echo

echo "7. Simulate a risky GitHub deployment signal"
DEPLOYMENT="$(api_post /api/webhooks/github/simulate '{
  "repository": "sentinel-ai/payment-api",
  "serviceName": "payment-api",
  "ownerTeam": "Payments Platform",
  "environment": "production",
  "commitSha": "abc1234",
  "pullRequestTitle": "Update payment settlement migration",
  "actor": "release@sentinel.ai",
  "ciStatus": "failure",
  "changedFiles": [
    "src/payments/SettlementWriter.java",
    "db/migration/V44__settlement_status.sql"
  ],
  "dependencies": ["checkout-service", "billing-service", "customer-ledger"]
}')"
printf '%s' "$DEPLOYMENT" | python3 -m json.tool
DEPLOYMENT_ID="$(printf '%s' "$DEPLOYMENT" | json_field id)"
echo

echo "8. Attach CI evidence"
api_post /api/integrations/ci/simulate '{
  "provider": "GitHub Actions",
  "repository": "sentinel-ai/payment-api",
  "serviceName": "payment-api",
  "ownerTeam": "Payments Platform",
  "environment": "production",
  "commitSha": "abc1234",
  "pipelineName": "payment-regression",
  "status": "failure",
  "failedTests": 6,
  "coverageDelta": -14,
  "actor": "release@sentinel.ai",
  "failedSuites": ["CheckoutRegression", "LedgerSettlementIT"],
  "dependencies": ["checkout-service", "billing-service", "customer-ledger"]
}' | python3 -m json.tool
echo

echo "9. Attach Jira work context"
api_post /api/integrations/jira/simulate '{
  "issueKey": "PAY-912",
  "summary": "Customer-impacting payment capture defect requires settlement retry changes",
  "priority": "Critical",
  "status": "In QA",
  "issueType": "Incident",
  "serviceName": "payment-api",
  "ownerTeam": "Payments Platform",
  "environment": "production",
  "commitSha": "abc1234",
  "actor": "release@sentinel.ai",
  "labels": ["hotfix", "customer-impact"],
  "dependencies": ["checkout-service", "billing-service", "customer-ledger"]
}' | python3 -m json.tool
echo

echo "10. Show AI memory for the new deployment"
api_get "/api/briefing/memory/$DEPLOYMENT_ID" | python3 -m json.tool
echo

echo "11. Block the risky deployment"
api_post "/api/deployments/$DEPLOYMENT_ID/approval" '{
  "decision": "BLOCK",
  "approver": "release@sentinel.ai",
  "note": "Blocked by Sentinel demo flow due to migration, CI, and blast radius."
}' | python3 -m json.tool
echo

echo "12. Audit trail"
api_get /api/audit-events | python3 -m json.tool

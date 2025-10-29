#!/bin/bash
set -e

echo "🔐 Infisical: Authenticating..."

# Login to Infisical and get token
export INFISICAL_TOKEN=$(infisical login \
  --method=universal-auth \
  --client-id="${INFISICAL_CLIENT_ID}" \
  --client-secret="${INFISICAL_CLIENT_SECRET}" \
  --domain="${INFISICAL_API_URL}" \
  --plain --silent)

if [ -z "$INFISICAL_TOKEN" ]; then
    echo "❌ Failed to authenticate with Infisical"
    exit 1
fi

echo "✅ Infisical: Authentication successful"
echo "🚀 Starting application with injected secrets..."

# Run the application with Infisical injecting secrets
exec infisical run \
  --token="${INFISICAL_TOKEN}" \
  --projectId="${INFISICAL_PROJECT_ID}" \
  --env="${INFISICAL_ENV}" \
  --domain="${INFISICAL_API_URL}" \
  -- "$@"

#!/bin/sh
set -e

echo "🔐 Infisical: Authenticating..."

# Read secrets from Docker secrets files if _FILE variables are set
if [ -n "${INFISICAL_CLIENT_ID_FILE}" ] && [ -f "${INFISICAL_CLIENT_ID_FILE}" ]; then
    export INFISICAL_CLIENT_ID=$(cat "${INFISICAL_CLIENT_ID_FILE}")
fi

if [ -n "${INFISICAL_CLIENT_SECRET_FILE}" ] && [ -f "${INFISICAL_CLIENT_SECRET_FILE}" ]; then
    export INFISICAL_CLIENT_SECRET=$(cat "${INFISICAL_CLIENT_SECRET_FILE}")
fi

if [ -n "${INFISICAL_PROJECT_ID_FILE}" ] && [ -f "${INFISICAL_PROJECT_ID_FILE}" ]; then
    export INFISICAL_PROJECT_ID=$(cat "${INFISICAL_PROJECT_ID_FILE}")
fi

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

#!/bin/sh
set -eu

apk add --no-cache curl jq > /dev/null 2>&1

INFISICAL_URL="http://infisical:8080"
ADMIN_EMAIL="admin@ai-sentinel.local"

ORG_NAME="AI Sentinel"
PROJECT_NAME="AI Sentinel"
ENV_NAME="dev"

# Skip if already configured
if [ -s /secrets/infisical_project_id.txt ] && [ -s /secrets/infisical_dev_client_id.txt ] && [ -s /secrets/infisical_dev_client_secret.txt ]; then
  echo "[infisical-config-dev] ✓ Already configured"
  exit 0
fi

echo "[infisical-config-dev] Waiting for Infisical API..."
max_retries=30
retry=0
until curl -sf "${INFISICAL_URL}/api/status" > /dev/null 2>&1; do
  retry=$((retry + 1))
  if [ "$retry" -ge "$max_retries" ]; then
    echo "[ERROR] Infisical API not ready after ${max_retries} attempts"
    exit 1
  fi
  sleep 2
done

# Generate random password (24 alphanumeric characters)
ADMIN_PASSWORD=$(head -c 256 /dev/urandom | tr -dc "A-Za-z0-9" | head -c 24)

echo "[infisical-config-dev] ================================================"
echo "[infisical-config-dev] Infisical DEV admin credentials (save these)"
echo "[infisical-config-dev]   Email:    ${ADMIN_EMAIL}"
echo "[infisical-config-dev]   Password: ${ADMIN_PASSWORD}"
echo "[infisical-config-dev]   UI URL:   http://localhost:8082"
echo "[infisical-config-dev] ================================================"

# Bootstrap admin
BOOTSTRAP_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/admin/bootstrap" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\",\"organization\":\"${ORG_NAME}\"}")
HTTP_CODE=$(echo "$BOOTSTRAP_RESPONSE" | tail -n1)
BOOTSTRAP_BODY=$(echo "$BOOTSTRAP_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ]; then
  echo "[ERROR] Failed to bootstrap Infisical (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${BOOTSTRAP_BODY}"
  exit 1
fi
ADMIN_TOKEN=$(echo "$BOOTSTRAP_BODY" | jq -r ".identity.credentials.token")
ORG_ID=$(echo "$BOOTSTRAP_BODY" | jq -r ".organization.id")
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "[ERROR] Missing admin token"
  exit 1
fi

# Create project
PROJECT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/projects" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectName\":\"${PROJECT_NAME}\",\"projectDescription\":\"Auto-configured AI Sentinel project\",\"type\":\"secret-manager\",\"shouldCreateDefaultEnvs\":true}")
HTTP_CODE=$(echo "$PROJECT_RESPONSE" | tail -n1)
PROJECT_BODY=$(echo "$PROJECT_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "[ERROR] Failed to create project (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${PROJECT_BODY}"
  exit 1
fi
PROJECT_ID=$(echo "$PROJECT_BODY" | jq -r ".project.id")
echo "$PROJECT_ID" > /secrets/infisical_project_id.txt

# Create machine identity
IDENTITY_CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/identities" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"ai-sentinel-${ENV_NAME}\",\"organizationId\":\"${ORG_ID}\",\"role\":\"admin\"}")
HTTP_CODE=$(echo "$IDENTITY_CREATE_RESPONSE" | tail -n1)
IDENTITY_CREATE_BODY=$(echo "$IDENTITY_CREATE_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "[ERROR] Failed to create identity (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${IDENTITY_CREATE_BODY}"
  exit 1
fi
IDENTITY_ID=$(echo "$IDENTITY_CREATE_BODY" | jq -r ".identity.id")

# Attach Universal Auth
UNIVERSAL_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/auth/universal-auth/identities/${IDENTITY_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"accessTokenTTL\":2592000,\"accessTokenMaxTTL\":2592000,\"accessTokenNumUsesLimit\":0}")
HTTP_CODE=$(echo "$UNIVERSAL_AUTH_RESPONSE" | tail -n1)
UNIVERSAL_AUTH_BODY=$(echo "$UNIVERSAL_AUTH_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "[ERROR] Failed to attach Universal Auth (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${UNIVERSAL_AUTH_BODY}"
  exit 1
fi
CLIENT_ID=$(echo "$UNIVERSAL_AUTH_BODY" | jq -r ".identityUniversalAuth.clientId")

# Create client secret
CLIENT_SECRET_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/auth/universal-auth/identities/${IDENTITY_ID}/client-secrets" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"description\":\"Auto-generated for ${ENV_NAME}\",\"ttl\":0,\"numUsesLimit\":0}")
HTTP_CODE=$(echo "$CLIENT_SECRET_RESPONSE" | tail -n1)
CLIENT_SECRET_BODY=$(echo "$CLIENT_SECRET_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "[ERROR] Failed to create client secret (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${CLIENT_SECRET_BODY}"
  exit 1
fi
CLIENT_SECRET=$(echo "$CLIENT_SECRET_BODY" | jq -r ".clientSecret")

# Add machine identity to project/workspace (required so universal-auth token can access secrets)
ADD_TO_PROJECT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v2/workspace/${PROJECT_ID}/identity-memberships/${IDENTITY_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"role\":\"admin\"}")
HTTP_CODE=$(echo "$ADD_TO_PROJECT_RESPONSE" | tail -n1)
ADD_TO_PROJECT_BODY=$(echo "$ADD_TO_PROJECT_RESPONSE" | head -n -1)
if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "[ERROR] Failed to add identity to project/workspace (HTTP ${HTTP_CODE})"
  echo "[ERROR] ${ADD_TO_PROJECT_BODY}"
  exit 1
fi

echo "$CLIENT_ID" > /secrets/infisical_dev_client_id.txt
echo "$CLIENT_SECRET" > /secrets/infisical_dev_client_secret.txt

# Validate universal-auth credentials can actually read secrets (prevents silent 403 later)
echo "[infisical-config-dev] Validating machine identity access..."
UA_LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INFISICAL_URL}/api/v1/auth/universal-auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"clientId\":\"${CLIENT_ID}\",\"clientSecret\":\"${CLIENT_SECRET}\"}")
UA_LOGIN_HTTP=$(echo "$UA_LOGIN_RESPONSE" | tail -n1)
UA_LOGIN_BODY=$(echo "$UA_LOGIN_RESPONSE" | head -n -1)
if [ "$UA_LOGIN_HTTP" != "200" ]; then
  echo "[ERROR] Universal auth login failed during validation (HTTP ${UA_LOGIN_HTTP})"
  echo "[ERROR] ${UA_LOGIN_BODY}"
  exit 1
fi
UA_ACCESS_TOKEN=$(echo "$UA_LOGIN_BODY" | jq -r ".accessToken")
if [ -z "$UA_ACCESS_TOKEN" ] || [ "$UA_ACCESS_TOKEN" = "null" ]; then
  echo "[ERROR] Missing accessToken in universal-auth login response"
  echo "[ERROR] ${UA_LOGIN_BODY}"
  exit 1
fi

# Attempt to fetch secrets with the machine token. Any 403 here means identity membership/permissions are wrong.
SECRETS_VALIDATE_RESPONSE=$(curl -s -w "\n%{http_code}" "${INFISICAL_URL}/api/v3/secrets/raw?environment=${ENV_NAME}&expandSecretReferences=true&include_imports=true&secretPath=%2F&workspaceId=${PROJECT_ID}" \
  -H "Authorization: Bearer ${UA_ACCESS_TOKEN}")
SECRETS_VALIDATE_HTTP=$(echo "$SECRETS_VALIDATE_RESPONSE" | tail -n1)
SECRETS_VALIDATE_BODY=$(echo "$SECRETS_VALIDATE_RESPONSE" | head -n -1)
if [ "$SECRETS_VALIDATE_HTTP" != "200" ]; then
  echo "[ERROR] Machine identity cannot read secrets (HTTP ${SECRETS_VALIDATE_HTTP})"
  echo "[ERROR] ${SECRETS_VALIDATE_BODY}"
  exit 1
fi

# Create required secrets in project for DEV
echo "[infisical-config-dev] Seeding required secrets in Infisical (env=${ENV_NAME})..."

DB_USER=$(cat /secrets/postgres_user.txt)
curl -sf -X POST "${INFISICAL_URL}/api/v4/secrets/DB_USER" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${DB_USER}\",\"secretComment\":\"Auto-generated PostgreSQL username\",\"type\":\"shared\"}" \
  > /dev/null 2>&1 && echo "[infisical-config-dev]   ✓ DB_USER created" || echo "[infisical-config-dev]   ⚠ DB_USER may already exist"

DB_PASSWORD=$(cat /secrets/postgres_password.txt)
curl -sf -X POST "${INFISICAL_URL}/api/v4/secrets/DB_PASSWORD" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${DB_PASSWORD}\",\"secretComment\":\"Auto-generated PostgreSQL password\",\"type\":\"shared\"}" \
  > /dev/null 2>&1 && echo "[infisical-config-dev]   ✓ DB_PASSWORD created" || echo "[infisical-config-dev]   ⚠ DB_PASSWORD may already exist"

PGADMIN_EMAIL=$(cat /secrets/pgadmin_email.txt)
curl -sf -X POST "${INFISICAL_URL}/api/v4/secrets/PGADMIN_DEFAULT_EMAIL" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${PGADMIN_EMAIL}\",\"secretComment\":\"Auto-generated PGAdmin email\",\"type\":\"shared\"}" \
  > /dev/null 2>&1 && echo "[infisical-config-dev]   ✓ PGADMIN_DEFAULT_EMAIL created" || echo "[infisical-config-dev]   ⚠ PGADMIN_DEFAULT_EMAIL may already exist"

PGADMIN_PASSWORD=$(cat /secrets/pgadmin_password.txt)
curl -sf -X POST "${INFISICAL_URL}/api/v4/secrets/PGADMIN_DEFAULT_PASSWORD" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${PGADMIN_PASSWORD}\",\"secretComment\":\"Auto-generated PGAdmin password\",\"type\":\"shared\"}" \
  > /dev/null 2>&1 && echo "[infisical-config-dev]   ✓ PGADMIN_DEFAULT_PASSWORD created" || echo "[infisical-config-dev]   ⚠ PGADMIN_DEFAULT_PASSWORD may already exist"

# Create or update PII_DATABASE_ENCRYPTION_KEY idempotently
PII_DATABASE_ENCRYPTION_KEY=$(cat /secrets/pii_database_encryption_key.txt)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${INFISICAL_URL}/api/v4/secrets/PII_DATABASE_ENCRYPTION_KEY" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${PII_DATABASE_ENCRYPTION_KEY}\",\"secretComment\":\"Auto-generated AES-256 encryption key for PII database (32 bytes base64-encoded)\",\"type\":\"shared\"}")

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  echo "[infisical-config-dev]   ✓ PII_DATABASE_ENCRYPTION_KEY created"
elif [ "$HTTP_CODE" = "409" ]; then
  HTTP_CODE_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "${INFISICAL_URL}/api/v4/secrets/PII_DATABASE_ENCRYPTION_KEY" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"${PII_DATABASE_ENCRYPTION_KEY}\",\"type\":\"shared\"}")
  if [ "$HTTP_CODE_PUT" = "200" ]; then
    echo "[infisical-config-dev]   ✓ PII_DATABASE_ENCRYPTION_KEY updated"
  else
    echo "[infisical-config-dev]   ⚠ PII_DATABASE_ENCRYPTION_KEY exists but update returned HTTP ${HTTP_CODE_PUT}; continuing"
  fi
else
  echo "[infisical-config-dev]   ⚠ Failed to create PII_DATABASE_ENCRYPTION_KEY (HTTP ${HTTP_CODE}); continuing"
fi

# Create empty secrets (to be filled manually)
for secret_name in "HUGGING_FACE_API_KEY" "CONFLUENCE_BASE_URL" "CONFLUENCE_USERNAME" "CONFLUENCE_API_TOKEN"; do
  curl -sf -X POST "${INFISICAL_URL}/api/v4/secrets/${secret_name}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"projectId\":\"${PROJECT_ID}\",\"environment\":\"${ENV_NAME}\",\"secretPath\":\"/\",\"secretValue\":\"\",\"secretComment\":\"Created empty - must be filled manually with real value\",\"type\":\"shared\"}" \
    > /dev/null 2>&1 && echo "[infisical-config-dev]   ✓ ${secret_name} created (empty)" || echo "[infisical-config-dev]   ⚠ ${secret_name} may already exist"
done

echo "[infisical-config-dev] ✓ Config complete"

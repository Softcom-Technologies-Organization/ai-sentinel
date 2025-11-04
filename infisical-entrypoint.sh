#!/bin/sh
set -e

echo "🔐 Reading secrets from Docker secrets..."

# Read secrets from files
if [ -f "/run/secrets/infisical_encryption_key" ]; then
    export ENCRYPTION_KEY=$(cat /run/secrets/infisical_encryption_key)
    echo "✅ ENCRYPTION_KEY loaded from secret"
fi

if [ -f "/run/secrets/infisical_auth_secret" ]; then
    export AUTH_SECRET=$(cat /run/secrets/infisical_auth_secret)
    echo "✅ AUTH_SECRET loaded from secret"
fi

if [ -f "/run/secrets/infisical_db_password" ]; then
    DB_PASSWORD=$(cat /run/secrets/infisical_db_password)
    export DB_CONNECTION_URI="postgres://infisical:${DB_PASSWORD}@infisical-db:5432/infisical"
    echo "✅ DB_CONNECTION_URI configured with password from secret"
fi

echo "🚀 Starting Infisical..."

# Execute the original Infisical entrypoint
exec /usr/local/bin/docker-entrypoint.sh ./standalone-entrypoint.sh

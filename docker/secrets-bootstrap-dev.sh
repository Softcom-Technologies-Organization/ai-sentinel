#!/bin/sh
set -eu

echo "[secrets-bootstrap-dev] Starting secrets initialization...";
mkdir -p /secrets;

# Core secrets (shared)
if [ ! -f /secrets/infisical_encryption_key.txt ] || [ "$(wc -c < /secrets/infisical_encryption_key.txt 2>/dev/null || echo 0)" -ne 32 ]; then
  od -An -tx1 -N16 /dev/urandom | tr -d " \n" > /secrets/infisical_encryption_key.txt;
fi;
if [ ! -f /secrets/infisical_auth_secret.txt ] || [ "$(wc -c < /secrets/infisical_auth_secret.txt 2>/dev/null || echo 0)" -lt 44 ]; then
  head -c 32 /dev/urandom | base64 | tr -d "\n\r" > /secrets/infisical_auth_secret.txt;
fi;
if [ ! -s /secrets/infisical_db_password.txt ]; then
  head -c 256 /dev/urandom | tr -dc "A-Za-z0-9" | head -c 24 > /secrets/infisical_db_password.txt;
fi;
if [ ! -f /secrets/pii_database_encryption_key.txt ] || [ "$(wc -c < /secrets/pii_database_encryption_key.txt 2>/dev/null || echo 0)" -lt 44 ]; then
  head -c 32 /dev/urandom | base64 | tr -d "\n\r" > /secrets/pii_database_encryption_key.txt;
fi;
if [ ! -s /secrets/postgres_user.txt ]; then
  echo "postgres" > /secrets/postgres_user.txt;
fi;
if [ ! -s /secrets/postgres_password.txt ]; then
  head -c 256 /dev/urandom | tr -dc "A-Za-z0-9" | head -c 24 > /secrets/postgres_password.txt;
fi;

# PGAdmin Credentials (used by infisical-configurator to seed Infisical secrets)
if [ ! -s /secrets/pgadmin_email.txt ]; then
  echo "admin@ai-sentinel.local" > /secrets/pgadmin_email.txt;
fi;
if [ ! -s /secrets/pgadmin_password.txt ]; then
  head -c 256 /dev/urandom | tr -dc "A-Za-z0-9" | head -c 24 > /secrets/pgadmin_password.txt;
fi;

# Dev placeholders (must be filled by configurator or manually)
[ -f /secrets/infisical_dev_client_id.txt ] || touch /secrets/infisical_dev_client_id.txt;
[ -f /secrets/infisical_dev_client_secret.txt ] || touch /secrets/infisical_dev_client_secret.txt;
[ -f /secrets/infisical_project_id.txt ] || touch /secrets/infisical_project_id.txt;

echo "[secrets-bootstrap-dev] ✓ Secrets directory ready";

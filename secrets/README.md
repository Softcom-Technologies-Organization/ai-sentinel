# Docker Secrets for AI Sentinel

This directory contains secret files used by Docker Compose for secure configuration management.

## 📋 Secret Files

- `infisical_dev_client_id.txt` - Infisical Machine Identity Client ID for DEV environment
- `infisical_dev_client_secret.txt` - Infisical Machine Identity Client Secret for DEV environment
- `infisical_project_id.txt` - Infisical Project ID (obtained from Infisical UI)
- `infisical_encryption_key.txt` - Infisical encryption key (16 bytes hex = 32 characters)
- `infisical_auth_secret.txt` - Infisical authentication secret (32 bytes base64 = 44 characters)
- `infisical_db_password.txt` - Password for Infisical PostgreSQL database

## 🔧 Initial Setup

### Step 1: Generate Infisical Internal Secrets

⚠️ **CRITICAL**: Infisical requires specific formats for encryption keys:

#### A. Generate ENCRYPTION_KEY (32 hex characters)

**PowerShell (Windows):** Copy and paste this command in PowerShell:
```powershell
-join ((0..15) | ForEach-Object { '{0:x2}' -f (Get-Random -Maximum 256) }) | Set-Content -Path 'secrets/infisical_encryption_key.txt' -NoNewline -Encoding ASCII; Write-Host "Generated:" (Get-Content 'secrets/infisical_encryption_key.txt')
```

**Bash (Linux/Mac):** Copy and paste this command in terminal:
```bash
openssl rand -hex 16 | tr -d '\n' | tee secrets/infisical_encryption_key.txt
```

**Expected format:** `6a58480d24ed96a9567e06ccd9c8ab01` (32 hex characters)

#### B. Generate AUTH_SECRET (44 base64 characters)

**PowerShell (Windows):** Copy and paste this command in PowerShell:
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 })) | Set-Content -Path 'secrets/infisical_auth_secret.txt' -NoNewline -Encoding ASCII; Write-Host "Generated:" (Get-Content 'secrets/infisical_auth_secret.txt')
```

**Bash (Linux/Mac):** Copy and paste this command in terminal:
```bash
openssl rand -base64 32 | tr -d '\n' | tee secrets/infisical_auth_secret.txt
```

**Expected format:** `dD5YNLZdAY8+ZHjaLz041W/A/9cdyXyLMaSz5UfLig8=` (44 base64 characters)

#### C. Generate Database Password

**PowerShell (Windows):** Copy and paste this command in PowerShell:
```powershell
-join ((65..90) + (97..122) + (48..57) | Get-Random -Count 24 | ForEach-Object {[char]$_}) | Set-Content -Path 'secrets/infisical_db_password.txt' -NoNewline -Encoding ASCII; Write-Host "Generated:" (Get-Content 'secrets/infisical_db_password.txt')
```

**Bash (Linux/Mac):** Copy and paste this command in terminal:
```bash
openssl rand -base64 24 | tr -d '\n' | tee secrets/infisical_db_password.txt
```

### Step 2: Start Infisical

With the generated secrets, start Infisical:

```bash
docker-compose -f docker-compose.dev.yml up -d infisical
```

Wait for it to be healthy:
```bash
docker logs infisical -f
```

### Step 3: Create Project and Get Project ID

1. Access Infisical web interface: http://localhost:8082
2. Log in with your account
3. Create a new project (e.g., "AI Sentinel")
4. Go to **Settings** (⚙️ bottom left)
5. Copy the **Project ID** from the top of the settings page

#### Save Project ID

**PowerShell (Windows):**
```powershell
"YOUR_PROJECT_ID" | Set-Content -Path 'secrets/infisical_project_id.txt' -NoNewline -Encoding ASCII; Write-Host "✅ Project ID saved"
```

**Bash (Linux/Mac):**
```bash
echo -n "YOUR_PROJECT_ID" > secrets/infisical_project_id.txt && echo "✅ Project ID saved"
```

*(Replace `YOUR_PROJECT_ID` with your actual Project ID from Infisical)*

### Step 4: Create Machine Identity and Get Credentials

1. In your project, navigate to: **Settings → Access Control → Machine Identities**
2. Click **+ Add Identity**
3. Name: `ai-sentinel-dev`
4. Role: **Admin** (or according to your needs)
5. Copy the **Client ID** and **Client Secret** (shown only once!)

#### Save Client Credentials

**PowerShell (Windows):**
```powershell
"YOUR_CLIENT_ID" | Set-Content -Path 'secrets/infisical_dev_client_id.txt' -NoNewline -Encoding ASCII; "YOUR_CLIENT_SECRET" | Set-Content -Path 'secrets/infisical_dev_client_secret.txt' -NoNewline -Encoding ASCII; Write-Host "✅ Credentials saved"
```

**Bash (Linux/Mac):**
```bash
echo -n "YOUR_CLIENT_ID" > secrets/infisical_dev_client_id.txt && echo -n "YOUR_CLIENT_SECRET" > secrets/infisical_dev_client_secret.txt && echo "✅ Credentials saved"
```

*(Replace `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` with your actual values)*

### Step 5: Start All Services

```bash
docker-compose -f docker-compose.dev.yml up -d
```

## 🚀 Quick Setup Script

**PowerShell (Windows):**

Save as `generate-infisical-secrets.ps1`:
```powershell
# Generate Infisical secrets with correct formats

# ENCRYPTION_KEY: 16 bytes hex = 32 characters
$encKey = -join ((0..15) | ForEach-Object { '{0:x2}' -f (Get-Random -Maximum 256) })

# AUTH_SECRET: 32 bytes base64 = 44 characters
$authBytes = 1..32 | ForEach-Object { Get-Random -Maximum 256 }
$authKey = [Convert]::ToBase64String($authBytes)

# DB_PASSWORD: 24 random characters
$dbPassword = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 24 | ForEach-Object {[char]$_})

# Write to files without newlines
[System.IO.File]::WriteAllText('secrets/infisical_encryption_key.txt', $encKey, [System.Text.Encoding]::ASCII)
[System.IO.File]::WriteAllText('secrets/infisical_auth_secret.txt', $authKey, [System.Text.Encoding]::ASCII)
[System.IO.File]::WriteAllText('secrets/infisical_db_password.txt', $dbPassword, [System.Text.Encoding]::ASCII)

Write-Host "✅ ENCRYPTION_KEY: $encKey (length: $($encKey.Length))"
Write-Host "✅ AUTH_SECRET: $authKey (length: $($authKey.Length))"
Write-Host "✅ DB_PASSWORD: $dbPassword (length: $($dbPassword.Length))"
Write-Host ""
Write-Host "🔐 Secrets generated successfully!"
Write-Host "⚠️  DO NOT commit these files to version control!"
Write-Host ""
Write-Host "Next steps:"
Write-Host "1. Start Infisical: docker-compose -f docker-compose.dev.yml up -d infisical"
Write-Host "2. Go to http://localhost:8082 and setup your account"
Write-Host "3. Get Client ID and Secret from Machine Identities"
Write-Host "4. Update infisical_dev_client_id.txt and infisical_dev_client_secret.txt"
Write-Host "5. Start all services: docker-compose -f docker-compose.dev.yml up -d"
```

Run it:
```powershell
powershell -ExecutionPolicy Bypass -File generate-infisical-secrets.ps1
```

**Bash (Linux/Mac):**

Save as `generate-infisical-secrets.sh`:
```bash
#!/bin/bash

# Generate Infisical secrets with correct formats

# ENCRYPTION_KEY: 16 bytes hex = 32 characters
ENC_KEY=$(openssl rand -hex 16)
echo -n "$ENC_KEY" > secrets/infisical_encryption_key.txt

# AUTH_SECRET: 32 bytes base64 = 44 characters
AUTH_SECRET=$(openssl rand -base64 32)
echo -n "$AUTH_SECRET" > secrets/infisical_auth_secret.txt

# DB_PASSWORD: 24 random characters
DB_PASSWORD=$(openssl rand -base64 24)
echo -n "$DB_PASSWORD" > secrets/infisical_db_password.txt

echo "✅ ENCRYPTION_KEY: $ENC_KEY (length: ${#ENC_KEY})"
echo "✅ AUTH_SECRET: $AUTH_SECRET (length: ${#AUTH_SECRET})"
echo "✅ DB_PASSWORD: $DB_PASSWORD (length: ${#DB_PASSWORD})"
echo ""
echo "🔐 Secrets generated successfully!"
echo "⚠️  DO NOT commit these files to version control!"
echo ""
echo "Next steps:"
echo "1. Start Infisical: docker-compose -f docker-compose.dev.yml up -d infisical"
echo "2. Go to http://localhost:8082 and setup your account"
echo "3. Get Client ID and Secret from Machine Identities"
echo "4. Update infisical_dev_client_id.txt and infisical_dev_client_secret.txt"
echo "5. Start all services: docker-compose -f docker-compose.dev.yml up -d"
```

Run it:
```bash
chmod +x generate-infisical-secrets.sh
./generate-infisical-secrets.sh
```

## ⚠️ Security

- **NEVER** commit `.txt` files containing real secrets to version control
- The `.gitignore` in this directory automatically ignores all `.txt` files
- For production, use proper secret management solutions (Azure Key Vault, AWS Secrets Manager, HashiCorp Vault, etc.)
- Limit read permissions on this directory to authorized users only
- **IMPORTANT**: Save `ENCRYPTION_KEY` in a secure backup location. Without it, encrypted data cannot be decrypted!

## 🔄 Usage in Docker Compose

Secrets are automatically mounted in containers at `/run/secrets/<secret_name>`.

Example in `docker-compose.dev.yml`:
```yaml
services:
  pii-detector:
    secrets:
      - infisical_dev_client_id
      - infisical_dev_client_secret

secrets:
  infisical_dev_client_id:
    file: ./secrets/infisical_dev_client_id.txt
  infisical_dev_client_secret:
    file: ./secrets/infisical_dev_client_secret.txt
```

## 🐛 Troubleshooting

### Error: "Invalid key length"

This means the ENCRYPTION_KEY or AUTH_SECRET has wrong format or length.

**Verify lengths:**
```powershell
# PowerShell
(Get-Content secrets/infisical_encryption_key.txt -Raw).Length  # Must be 32
(Get-Content secrets/infisical_auth_secret.txt -Raw).Length      # Must be 44
```

```bash
# Bash
wc -c < secrets/infisical_encryption_key.txt  # Must be 32
wc -c < secrets/infisical_auth_secret.txt      # Must be 44
```

**Fix if needed:**
Regenerate the secrets using the commands in Step 1.

### Secrets contain newlines

**Fix (PowerShell):**
```powershell
(Get-Content secrets/infisical_encryption_key.txt -Raw).Trim() | Set-Content secrets/infisical_encryption_key.txt -NoNewline
```

**Fix (Bash):**
```bash
tr -d '\n\r' < secrets/infisical_encryption_key.txt > temp && mv temp secrets/infisical_encryption_key.txt
```

## 📚 References

- [Infisical Self-Hosting Documentation](https://infisical.com/docs/self-hosting/configuration/envars)
- [Docker Compose Secrets](https://docs.docker.com/compose/use-secrets/)
- [12-Factor App Config](https://12factor.net/config)

## 📝 Notes

- Secrets are mounted read-only in containers
- Changing a secret requires restarting affected services: `docker-compose restart <service>`
- For production, consider Docker Swarm secrets or cloud-native solutions
- Client ID and Secret are obtained **after** Infisical is running, not before

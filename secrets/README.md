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
-join ((0..15) | ForEach-Object { '{0:x2}' -f (Get-Random -Maximum 256) }) | Set-Content -Path 'secrets/infisical_encryption_key.txt' -NoNewline -Encoding ASCII; Write-Host "Encryption key generated and set in secrets/infisical_encryption_key.txt"
```

**Bash (Linux/Mac):** Copy and paste this command in terminal:
```bash
openssl rand -hex 16 | tr -d '\n' | tee secrets/infisical_encryption_key.txt
```

**Expected format:** `6a58480d24ed96a9567e06ccd9c8ab01` (32 hex characters)

#### B. Generate AUTH_SECRET (44 base64 characters)

**PowerShell (Windows):** Copy and paste this command in PowerShell:
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 })) | Set-Content -Path 'secrets/infisical_auth_secret.txt' -NoNewline -Encoding ASCII; Write-Host "Auth secret generated and set in secrets/infisical_auth_secret.txt"
```

**Bash (Linux/Mac):** Copy and paste this command in terminal:
```bash
openssl rand -base64 32 | tr -d '\n' | tee secrets/infisical_auth_secret.txt
```

**Expected format:** `dD5YNLZdAY8+ZHjaLz041W/A/9cdyXyLMaSz5UfLig8=` (44 base64 characters)

#### C. Generate Database Password

**PowerShell (Windows):** Copy and paste this command in PowerShell:
```powershell
-join ((65..90) + (97..122) + (48..57) | Get-Random -Count 24 | ForEach-Object {[char]$_}) | Set-Content -Path 'secrets/infisical_db_password.txt' -NoNewline -Encoding ASCII; Write-Host "Infisical database password generated and set in secrets/infisical_db_password.txt"
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

Create a text file named `infisical_project_id.txt` in the `secrets` folder and paste your Project ID on a single line (no extra spaces or trailing newline).

### Step 4: Create Machine Identity and Get Credentials

1. In your project, navigate to: **Settings → Access Control → Machine Identities**
2. Click **+ Add Identity**
3. Name: `ai-sentinel-dev`
4. Role: **Admin** (or according to your needs)
5. Copy the **Client ID** and **Client Secret** (shown only once!)

#### Save Client Credentials

Create two text files in the `secrets` folder and paste each value on a single line (no extra spaces or trailing newline):
- `infisical_dev_client_id.txt` — Machine Identity Client ID (dev)
- `infisical_dev_client_secret.txt` — Machine Identity Client Secret (dev)

If you also configure production:
- `infisical_prod_client_id.txt` — Machine Identity Client ID (prod)
- `infisical_prod_client_secret.txt` — Machine Identity Client Secret (prod)

### Step 5: Start All Services

```bash
docker-compose -f docker-compose.dev.yml up -d
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
Recreate the secrets following the formatting rules described above (single line, exact length/format).

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

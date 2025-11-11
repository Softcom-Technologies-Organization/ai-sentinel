# AI Sentinel

[![Version](https://img.shields.io/badge/version-0.0.1-blue.svg)](https://github.com/Softcom-Technologies-Organization/ai-sentinel/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ready-brightgreen.svg)](https://www.docker.com/)
[![Python](https://img.shields.io/badge/python-3.13-blue.svg)](https://www.python.org/)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://openjdk.org/)
[![Angular](https://img.shields.io/badge/angular-19-red.svg)](https://angular.io/)

> Intelligent platform for detecting and analyzing Personally Identifiable Information (PII) in Confluence spaces, powered by advanced AI models

## Table of Contents

- [About](#about)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
  - [Production Mode (recommended)](#production-mode-recommended)
  - [Development Mode](#development-mode)
- [Configuration](#configuration)
- [Usage](#usage)
- [Tests](#tests)
- [Contributing](#contributing)
- [Support](#support)
- [License](#license)

## About

**AI Sentinel** is a comprehensive solution for detecting and analyzing Personally Identifiable Information (PII) in Confluence spaces. The application combines multiple state-of-the-art artificial intelligence models to accurately identify sensitive data and generate detailed reports.

**Context:** In a strict regulatory environment (GDPR, data protection), organizations must identify and protect personal information stored in their document management systems.

**Problem Solved:** AI Sentinel automates PII detection in Confluence spaces, identifying names, emails, addresses, phone numbers, credit cards, and other sensitive data.

**Solution:** The application uses a multi-model approach (GLiNER, Presidio, regex patterns) with a modern microservices architecture to scan, analyze, and report detected PII.

**Added Value:**
- ✅ Multi-language detection (FR, EN, etc.)
- ✅ Multiple AI models combined for optimal accuracy
- ✅ Intuitive user interface with real-time visualization
- ✅ Simple deployment via Docker Compose
- ✅ Hexagonal architecture for maximum maintainability

## Features

- ✅ **Multi-model PII detection**: Combines GLiNER, Presidio, and regex patterns for accurate detection
- ✅ **Confluence support**: Automatic scanning of Confluence spaces, pages, and content
- ✅ **Modern Web interface**: Angular dashboard with real-time scan visualization
- ✅ **Detailed reports**: Report generation with statistics and PII location
- ✅ **Microservices architecture**: Python (gRPC), Java (Spring Boot), and Angular services
- ✅ **Scan management**: Pause, resume, and real-time tracking of ongoing scans
- ✅ **PostgreSQL database**: Persistent storage of results and history
- 🚧 **Report export** (in progress): CSV/PDF export of scan results
- 📋 **Alerts and notifications** (planned): Real-time notifications when critical PII are detected

## Architecture

### Overview

AI Sentinel consists of three main services orchestrated via Docker Compose:

```
┌─────────────────────────────────────────────────────────────┐
│                         AI Sentinel                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │  Frontend (UI)  │  │  Backend (API)   │  │  Detector  │ │
│  │                 │  │                  │  │  Service   │ │
│  │  Angular 19     │  │  Spring Boot 3   │  │  Python    │ │
│  │  Port: 4200     │  │  Port: 8080/8090 │  │  gRPC      │ │
│  │                 │  │                  │  │  Port:50051│ │
│  └────────┬────────┘  └────────┬─────────┘  └──────┬─────┘ │
│           │                    │                    │        │
│           └────────────────────┴────────────────────┘        │
│                                │                             │
│                      ┌─────────▼──────────┐                 │
│                      │   PostgreSQL 18    │                 │
│                      │   Port: 5432       │                 │
│                      └────────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

### Project Structure

```
ai-sentinel/
├── pii-detector-service/       # Python PII detection service (gRPC)
│   ├── pii_detector/           # Detector source code
│   ├── config/                 # Model configurations
│   ├── tests/                  # Unit and integration tests
│   └── Dockerfile
├── pii-reporting-api/          # Spring Boot Backend API
│   ├── src/main/java/          # Java source code
│   ├── init-scripts/           # SQL initialization scripts
│   └── Dockerfile
├── pii-reporting-ui/           # Angular interface
│   ├── src/app/                # Angular source code
│   └── Dockerfile
├── proto/                      # Protocol Buffers definitions
├── docker-compose.dev.yml      # Compose for development
├── docker-compose.prod.yml     # Compose for production
└── README.md
```

### Technologies Used

- **Frontend**: Angular 19, TypeScript, TailwindCSS
- **Backend**: Spring Boot 3, Java 21, Armeria (gRPC client)
- **Detector**: Python 3.13, gRPC, Hugging Face Transformers
- **Database**: PostgreSQL 18
- **Infrastructure**: Docker, Docker Compose
- **AI Models**: GLiNER, Presidio, regex patterns

## Prerequisites

Before starting, make sure you have:

- **Docker Desktop**: Version 20.10 or higher
  ```bash
  # Check Docker version
  docker --version
  ```
  📖 [Docker Desktop Installation Guide](https://docs.docker.com/get-docker/)
  
  **Simplified Installation:**
  - 🪟 Windows: [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/)
  - 🍎 macOS: [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/)
  - 🐧 Linux: [Docker Engine for Linux](https://docs.docker.com/engine/install/)

- **Docker Compose**: Version 2.0 or higher (included with Docker Desktop)
  ```bash
  # Check Docker Compose version
  docker compose version
  ```

- **Infisical Account**: Required for secrets management
  - Self-hosted Infisical (included in docker-compose)
  - Follow [Infisical UI Setup Guide](docs/INFISICAL_UI_SETUP.md) for configuration

- **Confluence Credentials**: To scan your Confluence spaces (stored in Infisical)
  - Base URL of your Confluence instance
  - Username or email
  - Confluence API token ([How to create a token](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/))

- **Hugging Face API Key**: Optional, only if using private AI models (stored in Infisical)
  - Create an account on [Hugging Face](https://huggingface.co/join)
  - Generate an API key in [Settings > Access Tokens](https://huggingface.co/settings/tokens)

**Optional but recommended:**
- Git (to clone the repository in development mode)
- 16 GB RAM minimum (for AI models)
- Stable internet connection (model downloads: ~2 GB)

## Installation

AI Sentinel can be deployed in two ways depending on your needs:

### Production Mode (recommended)

**Production mode** uses pre-built Docker images hosted on GitHub Container Registry and Infisical for secure secrets management.

#### Step 1: Configure Infisical Secrets

Before starting the application, you **must** configure secrets in Infisical:

📖 **Follow the complete guide**: [Infisical UI Setup](docs/INFISICAL_UI_SETUP.md)

**Quick summary:**
1. Create an Infisical project (Cloud or self-hosted)
2. Create a Machine Identity with credentials
3. Save required Docker secret files in the `secrets/` folder:
   - `secrets/infisical_project_id.txt`
   - `secrets/infisical_prod_client_id.txt`
   - `secrets/infisical_prod_client_secret.txt`
4. Configure application secrets in Infisical (Confluence credentials, etc.)

#### Step 2: Automatic startup script (recommended)

**Linux/macOS:**
```bash
curl -fsSL https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/start-ai-sentinel-prod.sh | bash
```

**Windows PowerShell:**
```powershell
# Download the script
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/start-ai-sentinel-prod.ps1" -OutFile "start-ai-sentinel-prod.ps1"

# Execute the script
.\start-ai-sentinel-prod.ps1
```

The script will automatically:
1. ✅ Check that Docker is installed
2. ✅ Download the `docker-compose.prod.yml` file
3. ✅ Guide you through Infisical configuration
4. ✅ Download Docker images from GitHub
5. ✅ Start all services

⚠️ **Note**: You must configure Infisical secrets before running the script (see Step 1 above).

#### Step 3: Manual installation (alternative)

**1. Configure Infisical**

Follow the [Infisical UI Setup Guide](docs/INFISICAL_UI_SETUP.md) to:
- Create a project and Machine Identity
- Save Docker secret files in `secrets/` folder
- Configure application secrets (Confluence, database, etc.)

**2. Download configuration files**

```bash
# Create a directory for AI Sentinel
mkdir -p ~/.ai-sentinel
cd ~/.ai-sentinel

# Create secrets directory
mkdir -p secrets

# Download docker-compose.prod.yml
curl -fsSL https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/docker-compose.prod.yml -o docker-compose.prod.yml
```

**3. Start the application**

```bash
# Start all services
docker compose -f docker-compose.prod.yml up -d

# Check service status
docker compose -f docker-compose.prod.yml ps

# View logs in real-time
docker compose -f docker-compose.prod.yml logs -f
```

**4. Access the application**

Once all services are started (approximately 2-3 minutes):
- 📱 **Web Interface**: http://localhost:4200
- 🔌 **Backend API**: http://localhost:8080/sentinelle
- 📈 **Metrics & Health**: http://localhost:8090/internal/health
- 🗄️ **PgAdmin** (optional): http://localhost:5050

### Development Mode

**Development mode** clones the repository, compiles images locally, and uses Infisical for secrets management. Recommended for contributing to the project.

**1. Clone the repository**

```bash
git clone https://github.com/Softcom-Technologies-Organization/ai-sentinel.git
cd ai-sentinel
```

**2. Configure Infisical Secrets**

📖 **Follow the complete guide**: [Infisical UI Setup](docs/INFISICAL_UI_SETUP.md)

**Quick setup for development:**
1. Start Infisical locally: `docker-compose -f docker-compose.dev.yml up -d infisical`
2. Access Infisical UI at http://localhost:8082
3. Create a project and Machine Identity (dev environment)
4. Save Docker secret files in `secrets/` folder:
   - `secrets/infisical_project_id.txt`
   - `secrets/infisical_dev_client_id.txt`
   - `secrets/infisical_dev_client_secret.txt`
5. Configure application secrets in Infisical (Confluence credentials)

**3. Start with automatic script**

**Linux/macOS:**
```bash
chmod +x start-app.sh
./start-app.sh
```

**Windows:**
```powershell
.\start-app.ps1
```

**4. Or start manually**

```bash
# Start Infisical first (if not already running)
docker compose -f docker-compose.dev.yml up -d infisical

# Wait for Infisical to be healthy (~30 seconds)
docker compose -f docker-compose.dev.yml ps infisical

# Build and start all services
docker compose -f docker-compose.dev.yml up -d --build

# Check status
docker compose -f docker-compose.dev.yml ps

# View logs
docker compose -f docker-compose.dev.yml logs -f
```

### Installation Troubleshooting

**Issue: Docker is not running**
```bash
# Solution: Start Docker Desktop or Docker service
# Windows/macOS: Launch Docker Desktop
# Linux:
sudo systemctl start docker
```

**Issue: Port already in use (4200, 8080, etc.)**
```bash
# Solution 1: Stop the service using the port
# Windows:
netstat -ano | findstr :4200
taskkill /PID <PID> /F

# Linux/macOS:
lsof -ti:4200 | xargs kill -9

# Solution 2: Modify ports in docker-compose
# Edit docker-compose.yml and change external ports
```

**Issue: Docker images won't download**
```bash
# Solution: Check your connection and Docker credentials
docker login ghcr.io
```

**Issue: Failed to authenticate with Infisical**
```bash
# Solution 1: Verify Infisical secrets exist
ls -la secrets/
cat secrets/infisical_project_id.txt
cat secrets/infisical_dev_client_id.txt

# Solution 2: Check Infisical service is running
docker compose ps infisical
docker compose logs infisical

# Solution 3: Verify Machine Identity in Infisical UI
# Access http://localhost:8082 (dev) or https://app.infisical.com (cloud)
```

**Issue: Missing secrets at runtime**
```bash
# Verify secrets are mounted in containers
docker exec pii-reporting-api ls -la /run/secrets/

# Check Infisical authentication logs
docker logs pii-reporting-api | grep Infisical

# Ensure secrets exist in Infisical for the correct environment (dev/prod)
```

**Issue: PII Detector service is slow to start**
```
# Normal: First startup can take 5-10 minutes
# Service downloads AI models (~2 GB)
# Check progress:
docker compose logs -f pii-detector
```

## Configuration

AI Sentinel uses **Infisical** for secure secrets management. No `.env` files are used.

### Infisical Setup - Complete Step-by-Step Guide

This guide explains how to configure Infisical using the Web UI for AI Sentinel. All screenshots referenced below are available in [docs/screenshots](docs/screenshots).

#### Step 1: Sign up and sign in to Infisical

**Self-hosted (development):** Open http://localhost:8082/admin/signup in your browser

**Cloud (production):** Sign up at https://app.infisical.com

![Admin sign up](docs/screenshots/0%20-%20sign%20up.png)

![After sign up](docs/screenshots/1%20-%20after%20sign%20up.png)

#### Step 2: Create a project

1. In the top navigation, open **Projects** and click **Create Project**
2. Name it (for example: **AI Sentinel**)

![Project creation](docs/screenshots/2%20-%20project%20creation.png)

![Create project](docs/screenshots/3%20-%20create%20project.png)

After creation, open the Project Settings. You will need the **Project ID** in the next step.

**Optional:** You may want to remove the default "staging" environment if not needed:

![Remove staging](docs/screenshots/4%20-%20remove%20staging.png)

![Remove staging confirm](docs/screenshots/5%20-%20remove%20staging%20confirm.png)

#### Step 3: Save the Project ID for Docker (MANDATORY)

1. In the project Settings page, copy the **Project ID** (a UUID-like value)
2. You can find this in the project creation confirmation or in the project settings later

![Copy project ID](docs/screenshots/3%20bis%20-%20copy%20project%20ID.png)

**Save the Project ID as a Docker secret:**

Create a text file to store the Project ID:

```bash
# Create secrets directory if it doesn't exist
mkdir -p secrets

# Save Project ID (replace with your actual ID)
echo "YOUR_PROJECT_ID_HERE" > secrets/infisical_project_id.txt
```

- **File:** `secrets/infisical_project_id.txt`
- **Content:** Paste the Project ID on a single line (no extra spaces)

This is required by docker-compose (both dev and prod) to authenticate the services with Infisical.

⚠️ **Important:**
- Do not commit this file to version control
- Ensure the file is plain text and stored in the `secrets/` folder next to your docker-compose file

#### Step 4: Create a Machine Identity and generate credentials

1. Go to your project → **Settings** → **Access Control** → **Machine Identities**
2. Click **+ Add Identity**
3. Choose a meaningful name, for example:
   - `ai-sentinel-dev` (Environment: dev)
   - `ai-sentinel-prod` (Environment: prod)
4. Assign a role (**Admin** for simplicity; you can tighten later with fine-grained permissions)
5. Open the identity details → **Authentication** tab (Universal Auth)
6. Generate a **Client Secret**
7. Copy **BOTH** values now:
   - **Client ID**
   - **Client Secret** (will be shown only once)

![Create identity](docs/screenshots/6%20-%20create%20identity.png)

![Create identity form](docs/screenshots/7%20-%20create%20identity.png)

![Auth settings](docs/screenshots/8%20-%20auth%20settings.png)

![Add client secret](docs/screenshots/9%20-%20add%20client%20secret.png)

![Add client secret form](docs/screenshots/10%20-%20add%20client%20secret.png)

![Copy client secret](docs/screenshots/11%20-%20copy%20client%20secret.png)

Finally, ensure the identity is added to the project:

![Add identity to project](docs/screenshots/15%20-%20add%20identity.png)

![Add identity confirmation](docs/screenshots/16%20-%20add%20identity.png)

**Save the Machine Identity credentials as Docker secrets:**

Save credentials to files under the repository's `secrets/` folder so docker-compose can mount them.

**For the DEV environment identity** (recommended during development):

```bash
# Save Client ID (replace with your actual ID)
echo "YOUR_DEV_CLIENT_ID_HERE" > secrets/infisical_dev_client_id.txt

# Save Client Secret (replace with your actual secret)
echo "YOUR_DEV_CLIENT_SECRET_HERE" > secrets/infisical_dev_client_secret.txt
```

**For the PROD environment identity** (if you plan production):

```bash
# Save Client ID (replace with your actual ID)
echo "YOUR_PROD_CLIENT_ID_HERE" > secrets/infisical_prod_client_id.txt

# Save Client Secret (replace with your actual secret)
echo "YOUR_PROD_CLIENT_SECRET_HERE" > secrets/infisical_prod_client_secret.txt
```

⚠️ **Important:**
- Do not commit these files to version control
- Ensure the files are plain text and stored in the `secrets/` folder next to your docker-compose file
- Client Secret is shown only once - save it immediately

#### Step 5: Add required application secrets inside Infisical

Create these variables in your Infisical project for each target environment (dev and/or prod). The services will fetch them at runtime via Infisical CLI.

![Secret management](docs/screenshots/12%20-%20secret%20management.png)

**How to create a secret:**

To add a new secret to your Infisical project:

1. Click on the **"Add Secret"** button in the secret management interface
2. Fill in the secret key name (e.g., `CONFLUENCE_BASE_URL`)
3. Enter the secret value
4. Click **"Save"** to create the secret

![Add secret](docs/screenshots/13%20-%20add%20secret.png)

![Create secret](docs/screenshots/14%20-%20create%20secret.png)

Once you've added all the required secrets, you'll see them listed in the secret management interface:

![Secrets created](docs/screenshots/14%20bis%20-%20secrets%20created.png)

**Required secrets for PII Reporting API (Spring Boot):**

| Secret Name | Description | Where to find |
|-------------|-------------|---------------|
| `CONFLUENCE_BASE_URL` | Your Confluence base URL | Your Confluence instance URL (Cloud or Server/Data Center)<br>Example: `https://company.atlassian.net/wiki` |
| `CONFLUENCE_USERNAME` | Account email or username used to access Confluence | Your Atlassian account (id.atlassian.com) or corporate directory |
| `CONFLUENCE_API_TOKEN` | Confluence API token for the above user | Atlassian Cloud: https://id.atlassian.com/manage-profile/security/api-tokens → Create token |

**Optional proxy secrets** (only if your Confluence is reachable via a proxy):

| Secret Name | Description | Default |
|-------------|-------------|---------|
| `CONFLUENCE_ENABLE_PROXY` | Enable proxy | `false` |
| `CONFLUENCE_PROXY_HOST` | Proxy hostname | - |
| `CONFLUENCE_PROXY_PORT` | Proxy port | `8080` |
| `CONFLUENCE_PROXY_USERNAME` | Proxy username | - |
| `CONFLUENCE_PROXY_PASSWORD` | Proxy password | - |

**Note:** Database connectivity to Postgres is configured via docker-compose and is not stored in Infisical by default in this project. You may choose to manage DB_* variables in Infisical later if needed.

**PII Detector Service (Python):**

No mandatory secrets by default. Model configs and caches are handled via environment variables set in docker-compose. If your models require private access (e.g., Hugging Face tokens), you could add `HF_TOKEN` or similar to Infisical and consume it in your service wrapper.

#### Step 6: Start the stack

Once the files in `secrets/` are saved and the Infisical project is configured:

**Development:**
```bash
# Start Infisical first
docker-compose -f docker-compose.dev.yml up -d infisical

# Wait until Infisical is healthy (~30 seconds)
docker-compose -f docker-compose.dev.yml ps infisical

# Then start the rest of the stack
docker-compose -f docker-compose.dev.yml up -d
```

**Production:**
```bash
docker-compose -f docker-compose.prod.yml up -d
```

The services will:
- ✅ Read Machine Identity credentials and Project ID from Docker secrets
- ✅ Authenticate to Infisical via Universal Auth
- ✅ Inject the configured project secrets into the runtime environment

#### Step 7: Infisical Troubleshooting

**401/403 from Infisical:**
- Verify the Machine Identity (client ID/secret) is correct
- Ensure the identity has been added to the project
- Check the Project ID file contains the correct value
- Verify the identity has proper permissions (Admin or appropriate role)

**Missing variables at runtime:**
- Ensure variables exist in the correct Infisical environment (dev/prod)
- Check the docker-compose service uses the matching `INFISICAL_ENV` value
- View service logs to see which secrets failed to load:
  ```bash
  docker logs pii-reporting-api | grep Infisical
  ```

**Self-hosted Infisical not starting:**
- Ensure the internal secrets exist in `secrets/` folder (see `secrets/README.md` for `ENCRYPTION_KEY`, `AUTH_SECRET`, DB password generation)
- Check Infisical logs: `docker logs infisical`
- Verify PostgreSQL is healthy: `docker ps --filter name=infisical-db`
- Restart the infisical service: `docker-compose restart infisical`

**Secrets not loading in application:**
```bash
# 1. Verify secret files exist
ls -la secrets/

# 2. Check secrets are mounted in container
docker exec pii-reporting-api ls -la /run/secrets/

# 3. Verify Infisical authentication succeeded
docker logs pii-reporting-api 2>&1 | grep -i "infisical\|secret"

# 4. Check Infisical service health
curl http://localhost:8082/api/status
```

#### Appendix: Docker Secret Files Reference

These files are mounted by docker-compose for runtime authentication with Infisical:

**Application secrets (required for all deployments):**
- `secrets/infisical_project_id.txt` → Project ID from Infisical UI project settings
- `secrets/infisical_dev_client_id.txt` → Machine Identity (dev) Client ID
- `secrets/infisical_dev_client_secret.txt` → Machine Identity (dev) Client Secret
- `secrets/infisical_prod_client_id.txt` → Machine Identity (prod) Client ID (if used)
- `secrets/infisical_prod_client_secret.txt` → Machine Identity (prod) Client Secret (if used)

**Infisical self-hosted secrets** (only if you self-host Infisical locally for development):
- `secrets/infisical_encryption_key.txt` → Server encryption key (hex, 32 chars)
- `secrets/infisical_auth_secret.txt` → Server auth secret (base64, 44 chars)
- `secrets/infisical_db_password.txt` → Password for Infisical's Postgres DB

For generation commands and details, see `secrets/README.md`.

---

### Quick Configuration Summary

**1. Infisical Project Setup**
- Create a project in Infisical (Cloud: https://app.infisical.com or self-hosted: http://localhost:8082)
- Create a Machine Identity with credentials (separate for dev and prod environments)
- Save Docker secret files in the `secrets/` folder:
  - `infisical_project_id.txt` - Your Infisical project ID
  - `infisical_dev_client_id.txt` / `infisical_prod_client_id.txt` - Machine Identity Client ID
  - `infisical_dev_client_secret.txt` / `infisical_prod_client_secret.txt` - Machine Identity Client Secret

**2. Required Secrets in Infisical**

Configure these secrets in your Infisical project (environment: dev or prod):

| Secret Name | Description | Example | Environment |
|-------------|-------------|---------|-------------|
| `CONFLUENCE_BASE_URL` | Confluence instance URL | `https://company.atlassian.net/wiki` | dev, prod |
| `CONFLUENCE_USERNAME` | Confluence email or username | `user@company.com` | dev, prod |
| `CONFLUENCE_API_TOKEN` | Confluence API token | `ATATT3xFfGF0...` | dev, prod |
| `DB_USERNAME` | PostgreSQL username | `postgres` | prod |
| `DB_PASSWORD` | PostgreSQL password | `<random-password>` | prod |

**3. Optional Secrets (Confluence Proxy)**

| Secret Name | Description | Default | Required |
|-------------|-------------|---------|----------|
| `CONFLUENCE_ENABLE_PROXY` | Enable proxy | `false` | No |
| `CONFLUENCE_PROXY_HOST` | Proxy hostname | - | If proxy enabled |
| `CONFLUENCE_PROXY_PORT` | Proxy port | `8080` | No |
| `CONFLUENCE_PROXY_USERNAME` | Proxy username | - | If proxy auth |
| `CONFLUENCE_PROXY_PASSWORD` | Proxy password | - | If proxy auth |

**4. Optional Secrets (Advanced Configuration)**

| Secret Name | Description | Default | Required |
|-------------|-------------|---------|----------|
| `CONFLUENCE_CACHE_REFRESH_INTERVAL` | Cache refresh interval (ms) | `300000` | No |
| `CONFLUENCE_CACHE_INITIAL_DELAY` | Initial delay (ms) | `5000` | No |
| `CONFLUENCE_POLLING_INTERVAL` | Polling interval (ms) | `60000` | No |
| `HUGGING_FACE_API_KEY` | Hugging Face API key | - | Only for private models |

### Security Notes

✅ **Best Practices:**
- All secrets are stored securely in Infisical (encrypted at rest)
- Secrets are injected at runtime via Docker secrets
- Never commit secret files to version control (included in `.gitignore`)
- Use Infisical Cloud for production (high availability, backups, audit logs)
- Rotate secrets regularly (recommended: every 90 days)

⚠️ **Important:**
- The `secrets/` folder is excluded from version control
- Keep a secure backup of `infisical_encryption_key.txt` if using self-hosted Infisical
- Use separate Machine Identities for dev and prod environments

### AI Models Configuration

Models are configured in `pii-detector-service/config/models/`:

- **GLiNER**: `gliner-pii.toml` - Main detection model
- **Presidio**: `presidio-detector.toml` - Microsoft Presidio detector
- **Regex**: `regex-patterns.toml` - Regex patterns for emails, phones, etc.

See [detailed model documentation](pii-detector-service/docs/CONFIG_MIGRATION.md) for more information.

## Usage

### Quick Start

**1. Access the web interface**

Open your browser at: http://localhost:4200

**2. Create a scan**

- Click "New Scan"
- Select the Confluence space to scan
- Configure scan parameters
- Click "Start Scan"

**3. Track scan in real-time**

The dashboard displays:
- Real-time progress
- Number of pages scanned
- PII detected by type
- Ability to pause/resume

**4. View results**

Once the scan is complete:
- View global statistics
- Explore PII detected per page
- Access Confluence pages directly

### Useful Docker Commands

**Service management:**
```bash
# Start application (production mode)
docker compose -f docker-compose.prod.yml up -d

# Start application (development mode)
docker compose -f docker-compose.dev.yml up -d

# Stop application
docker compose -f docker-compose.dev.yml down

# Restart specific service
docker compose -f docker-compose.dev.yml restart pii-reporting-api

# View logs in real-time
docker compose -f docker-compose.dev.yml logs -f

# View logs for specific service
docker compose -f docker-compose.dev.yml logs -f pii-detector

# View service status
docker compose -f docker-compose.dev.yml ps

# Rebuild and restart (after code changes)
docker compose -f docker-compose.dev.yml up -d --build
```

**Data management:**
```bash
# Stop and remove data (volumes)
docker compose down -v

# Database backup
docker compose exec postgres pg_dump -U postgres ai-sentinel > backup.sql

# Restore database
docker compose exec -T postgres psql -U postgres ai-sentinel < backup.sql
```

**Cleanup:**
```bash
# Remove all stopped containers
docker container prune

# Remove all unused images
docker image prune -a

# Complete cleanup (warning: removes EVERYTHING)
docker system prune -a --volumes
```

### REST API Endpoints

**Backend API** available at http://localhost:8080/sentinelle

Main endpoints:

- `GET /sentinelle/api/scans` - List of scans
- `POST /sentinelle/api/scans` - Create a new scan
- `GET /sentinelle/api/scans/{id}` - Scan details
- `POST /sentinelle/api/scans/{id}/pause` - Pause a scan
- `POST /sentinelle/api/scans/{id}/resume` - Resume a scan
- `GET /sentinelle/actuator/health` - Health check
- `GET /sentinelle/swagger-ui.html` - Swagger documentation

**Example request:**
```bash
# Create a new scan
curl -X POST http://localhost:8080/sentinelle/api/scans \
  -H "Content-Type: application/json" \
  -d '{
    "spaceKey": "DS",
    "spaceName": "System Documentation",
    "maxPages": 100
  }'

# Check API health
curl http://localhost:8080/sentinelle/actuator/health
```

### gRPC PII Detector Service

**PII detection service** available at `localhost:50051`

The service exposes gRPC methods defined in `proto/pii_detection.proto`:

```protobuf
service PIIDetectionService {
  rpc DetectPII(PIIRequest) returns (PIIResponse);
  rpc DetectBatchPII(BatchPIIRequest) returns (BatchPIIResponse);
}
```

**Test gRPC service:**
```bash
# Install grpcurl (gRPC testing tool)
# macOS:
brew install grpcurl

# Linux:
curl -sSL "https://github.com/fullstorydev/grpcurl/releases/download/v1.8.9/grpcurl_1.8.9_linux_x86_64.tar.gz" | tar -xz -C /usr/local/bin

# Windows: Download from https://github.com/fullstorydev/grpcurl/releases

# List available services
grpcurl -plaintext localhost:50051 list

# Call DetectPII method
grpcurl -plaintext -d '{"text": "My email is john.doe@example.com"}' \
  localhost:50051 pii_detection.PIIDetectionService/DetectPII
```

### PgAdmin Access (optional)

PgAdmin is available for database administration:

**URL**: http://localhost:5050  
**Email**: admin@pgadmin.com  
**Password**: admin

**PostgreSQL connection configuration in PgAdmin:**
- **Host**: postgres
- **Port**: 5432
- **Database**: ai-sentinel
- **Username**: postgres
- **Password**: postgres

## Tests

### Python Service Tests (PII Detector)

The detection service has a comprehensive pytest test suite.

**Run all tests:**
```bash
cd pii-detector-service

# With coverage
pytest --cov=pii_detector --cov-report=html

# Specific tests
pytest tests/unit/test_gliner_detector.py -v

# Parallel tests
pytest -n auto
```

**Expected results:**
- ✅ 34+ unit tests
- ✅ Code coverage > 80%
- ✅ Integration tests for each detector

See the [detailed testing guide](pii-detector-service/README.md#tests-avec-pytest).

### Java Backend Tests

```bash
cd pii-reporting-api

# Run tests
mvn test

# With coverage
mvn test jacoco:report

# View report
open target/site/jacoco/index.html
```

### Angular Frontend Tests

```bash
cd pii-reporting-ui

# Unit tests
npm test

# E2E tests
npm run e2e

# With coverage
npm run test:coverage
```

## Contributing

Contributions are welcome! Here's how to participate:

### Contribution Process

1. **Fork** the project
2. **Create** a branch for your feature
   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. **Commit** your changes
   ```bash
   git commit -m 'Add: Amazing feature'
   ```
4. **Push** to the branch
   ```bash
   git push origin feature/AmazingFeature
   ```
5. **Open** a Pull Request

### Code Conventions

- **Java**: Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **Python**: Follow [PEP 8](https://www.python.org/dev/peps/pep-0008/)
- **TypeScript/Angular**: Follow [Angular Style Guide](https://angular.io/guide/styleguide)
- **Commits**: Follow [Conventional Commits](https://www.conventionalcommits.org/)
  - `feat:` New feature
  - `fix:` Bug fix
  - `docs:` Documentation
  - `refactor:` Refactoring
  - `test:` Adding tests

### Hexagonal Architecture

The **pii-reporting-api** (Java Backend) follows hexagonal architecture principles. When contributing to this module, make sure to:
- ✅ Separate business logic from technical dependencies
- ✅ Use ports and adapters
- ✅ Maintain cyclomatic complexity < 7
- ✅ Write tests before refactoring

### Code of Conduct

We follow the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/).

### Bug Reporting

Use [GitHub Issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues) with the appropriate template.

## Support

- 📧 **Email**: support@softcom-technologies.com
- 🐛 **Issues**: [GitHub Issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues)
- 📖 **Documentation**:
  - [Docker Installation Guide](https://docs.docker.com/get-docker/)
  - [PII Detector Documentation](pii-detector-service/README.md)
  - [Backend API Documentation](pii-reporting-api/README.md)
  - [UI Documentation](pii-reporting-ui/README.md)
- 🌐 **Website**: https://softcom-technologies.com

### Frequently Asked Questions

**Q: What types of PII are detected?**  
A: First names, last names, emails, phones, addresses, social security numbers, credit cards, dates of birth, and more.

**Q: Do models work offline?**  
A: Yes, after the first download, models are cached locally.

**Q: Can I use my own AI model?**  
A: Yes, see the [model configuration documentation](pii-detector-service/docs/CONFIG_MIGRATION.md).

**Q: How to secure detected data?**  
A: PII are never sent to external services. Everything is processed locally. Use HTTPS and secure your PostgreSQL database in production.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright © 2025 Softcom Technologies

## Acknowledgments

- [Hugging Face](https://huggingface.co/) - For AI models and platform
- [Microsoft Presidio](https://github.com/microsoft/presidio) - For the PII detection framework
- [GLiNER](https://github.com/urchade/GLiNER) - For the generalist NER model
- [Spring Boot](https://spring.io/projects/spring-boot) - Backend framework
- [Angular](https://angular.io/) - Frontend framework
- All [contributors](https://github.com/Softcom-Technologies-Organization/ai-sentinel/graphs/contributors)

---

**Developed with ❤️ by [Softcom Technologies](https://softcom-technologies.com)**

# AI Sentinel

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Softcom-Technologies-Organization/ai-sentinel/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ready-brightgreen.svg)](https://www.docker.com/)
[![Python](https://img.shields.io/badge/python-3.13-blue.svg)](https://www.python.org/)
[![Java](https://img.shields.io/badge/java-25-orange.svg)](https://openjdk.org/)
[![Angular](https://img.shields.io/badge/angular-21-red.svg)](https://angular.io/)

> Intelligent platform for detecting and analyzing Personally Identifiable Information (PII) in Confluence spaces, powered by advanced AI models
## Demo video

https://github.com/user-attachments/assets/d2c633d6-3209-4b2f-b80a-88fe2e41f945

## Table of Contents

- [About](#about)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation and Configuration](#installation-and-configuration)
  - [Quick Setup (2 Steps)](#quick-setup-2-steps)
  - [Optional: Advanced Configuration](#optional-advanced-configuration)
  - [AI Models Configuration](#ai-models-configuration)
  - [Installation Troubleshooting](#installation-troubleshooting)
- [Usage](#usage)
- [Tests](#tests)
- [Contributing](#contributing)
- [Support](#support)
- [Frequently Asked Questions](#frequently-asked-questions)
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

AI Sentinel consists of the following components orchestrated via Docker Compose:  
  
![Description du diagramme](./docs/ai-sentinel-components.drawio.svg)


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

- **Frontend**: Angular 21, TypeScript, TailwindCSS
- **Backend**: Spring Boot 3, Java 25, Armeria (gRPC client)
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

- **Confluence Credentials**: To scan your Confluence spaces (configured via the Settings UI)
  - Base URL of your Confluence instance
  - Username or email
  - Confluence API token ([How to create a token](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/))

- **Download the ai-sentinel docker-compose file from Github**:

  You can directly download the `docker-compose.yml` file from GitHub:

  - Repository URL: https://github.com/Softcom-Technologies-Organization/ai-sentinel
  - Direct `docker-compose.yml` file: https://github.com/Softcom-Technologies-Organization/ai-sentinel/blob/main/docker-compose.yml

  On the `docker-compose.yml` file page:
  1. Open the link above in your browser.
  2. In the top-right area of the file header, click the **"Download raw file"** button (down arrow icon) to download the file to your machine.
  3. Save the file as `docker-compose.yml` in a dedicated folder (for example: `ai-sentinel/`).

  ![Download docker-compose from GitHub](docs/screenshots/dowload-docker-compose.png)

**Optional but recommended:**
- Git (to clone the repository in development mode)
- 16 GB RAM minimum (for AI models)
- Stable internet connection (model downloads: ~2 GB)

## Installation and Configuration

### Quick Setup (2 Steps)

#### Step 1: Start AI Sentinel with Docker Compose

**Production mode:**
```bash
docker compose up -d
```

**Development mode:**
```bash
docker compose -f docker-compose.dev.yml up -d
```

**Note for first-time setup:**
When running the command for the first time, you will see errors for `pii-reporting-api` container. **This is expected and normal.**

![Expected Docker Error](docs/screenshots/docker-compose-first-normal-error.png)

**Why does this happen?**
The `ai-sentinel-infisical-configurator` container needs to run first to generate secure credentials (database encryption keys, etc.) and store them in the internal secrets manager. Once completed, restarting the services will allow them to start correctly.

The bootstrapping automatically:
- Generates all required encryption keys and database credentials
- Creates the internal secrets manager project and machine identity
- Seeds all auto-generated secrets (DB credentials, encryption keys, etc.)

#### Step 2: Restart Services

After the initial bootstrapping completes, restart the application containers:

```bash
docker compose up -d --force-recreate pii-detector pii-reporting-api pii-reporting-ui
```

**Or for development mode:**
```bash
docker compose -f docker-compose.dev.yml up -d --force-recreate pii-detector pii-reporting-api pii-reporting-ui
```

**Verify services are running:**
```bash
docker compose ps
```

You should see all services in "Up" status.

---

### That's It!

Your AI Sentinel instance is now fully configured and ready to use!

Access the application at:
- **Web Interface**: http://localhost:4200
- **Backend API**: http://localhost:8080/ai-sentinel

**Configure your Confluence connection** directly in the AI Sentinel Settings UI (http://localhost:4200 > Settings).

**AI models** (nvidia/gliner-PII, Presidio, etc.) are public and downloaded automatically on first startup. No API key is required.

### Optional: Advanced Configuration

The internal secrets manager (Infisical) is available at http://localhost:8082 for advanced configuration.

**Optional Secrets (Confluence Proxy):**

| Secret Name | Description | Default | Required |
|-------------|-------------|---------|----------|
| `CONFLUENCE_ENABLE_PROXY` | Enable proxy | `false` | No |
| `CONFLUENCE_PROXY_HOST` | Proxy hostname | - | If proxy enabled |
| `CONFLUENCE_PROXY_PORT` | Proxy port | `8080` | No |
| `CONFLUENCE_PROXY_USERNAME` | Proxy username | - | If proxy auth |
| `CONFLUENCE_PROXY_PASSWORD` | Proxy password | - | If proxy auth |

### AI Models Configuration

Models are configured in `pii-detector-service/config/models/`:

This project uses pre-trained AI models from external sources:

- **nvidia/gliner-PII** (Hugging Face)
  - **License**: NVIDIA Open Model License Agreement
  - **Usage**: The model is downloaded at runtime from Hugging Face
  - **License Link**: https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/

#### Compliance

When deploying this application, users accept the terms of the NVIDIA Open Model License for the GLiNER-PII model. Refer to the model's [Hugging Face page](https://huggingface.co/nvidia/gliner-PII) for details.

- **GLiNER**: `gliner-pii.toml` - Main detection model
- **Presidio**: `presidio-detector.toml` - Microsoft Presidio detector
- **Regex**: `regex-patterns.toml` - Regex patterns for emails, phones, etc.

See [detailed model documentation](pii-detector-service/docs/CONFIG_MIGRATION.md) for more information.


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

**Issue: PII Detector service is slow to start**
```
# Normal: First startup can take 5-10 minutes
# Service downloads AI models (~2 GB)
# Check progress:
docker compose logs -f pii-detector
```


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
docker compose up -d

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

**Backend API** available at http://localhost:8080/ai-sentinel

Main endpoints:

- `GET /ai-sentinel/api/scans` - List of scans
- `POST /ai-sentinel/api/scans` - Create a new scan
- `GET /ai-sentinel/api/scans/{id}` - Scan details
- `POST /ai-sentinel/api/scans/{id}/pause` - Pause a scan
- `POST /ai-sentinel/api/scans/{id}/resume` - Resume a scan
- `GET /ai-sentinel/actuator/health` - Health check
- `GET /ai-sentinel/swagger-ui.html` - Swagger documentation

**Example request:**
```bash
# Create a new scan
curl -X POST http://localhost:8080/ai-sentinel/api/scans \
  -H "Content-Type: application/json" \
  -d '{
    "spaceKey": "DS",
    "spaceName": "System Documentation",
    "maxPages": 100
  }'

# Check API health
curl http://localhost:8080/ai-sentinel/actuator/health
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

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

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
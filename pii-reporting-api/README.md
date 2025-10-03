# Sentinelle - Confluence Analysis & PII Detection via gRPC

Spring Boot application to analyze Confluence spaces and detect personally identifiable information (PII) via a Python gRPC microservice.

IMPORTANT: gRPC Port 50051
- Only the Python PII microservice server should listen on port 50051 for the service to work correctly.
- If another process is listening on this port, you may encounter errors like "unknown service pii_detection.PIIDetectionService" or "UNAVAILABLE" on the Java side.
- Check on Windows (PowerShell):
  - Quick test: Test-NetConnection -ComputerName localhost -Port 50051
  - Who is listening: netstat -ano | findstr :50051 then tasklist /FI "PID eq <PID>"
  - Verify gRPC service: grpcurl -plaintext localhost:50051 list should display pii_detection.PIIDetectionService

## Features

- 🔎 Confluence content analysis
- 🛡️ PII detection (emails, phone numbers, etc.) via Python gRPC microservice
- 📊 Triggered scans by space and real-time SSE streaming

## Prerequisites

- Java 24
- Python 3.10+ (for PII microservice)
- PostgreSQL 15+ (or via provided docker-compose)
- grpcurl (recommended for diagnosing gRPC service)
- Docker (for PostgreSQL via docker-compose)

## Installation

### 1. Clone the project
```bash
git clone [REPO_URL]
cd sentinelle
```

### 2. Configuration

Create a `.env` file with your Confluence credentials:
```env
# Confluence
CONFLUENCE_BASE_URL=https://your-instance.atlassian.net
CONFLUENCE_USERNAME=your-email@company.com
CONFLUENCE_API_TOKEN=your-token
CONFLUENCE_SPACE_KEY=your-space
```

gRPC client parameters (application.yml):
```yaml
pii-detector:
  client: armeria   # armeria|grpc
  host: localhost
  port: 50051
  default-threshold: 0.5
```

Database (PostgreSQL):
```env
# If using the provided docker-compose, the exposed port is 5433
DB_HOST=localhost
DB_PORT=5433
DB_NAME=sentinelle
DB_USERNAME=changeme
DB_PASSWORD=changeme
# (or use SPRING_DATASOURCE_URL directly)
# SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/sentinelle
```

## Startup

0) Start PostgreSQL (docker-compose recommended)
- In a terminal at the project root:
  ```bash
  docker compose up -d
  ```
- The database listens on host port 5433. Check your environment variables (DB_PORT=5433 or SPRING_DATASOURCE_URL).

1) Start the Python PII microservice (must listen on 50051)
- From your Python environment (see `pii-grpc-service` folder): ensure the server is running in plaintext on localhost:50051.
- Verification: `grpcurl -plaintext localhost:50051 list` should list `pii_detection.PIIDetectionService`.

2) Launch the Java application
```bash
./mvnw clean package
java -jar target/sentinelle-0.0.1-SNAPSHOT.jar
```

Swagger/OpenAPI: http://localhost:8080/sentinelle/swagger-ui.html

## Usage

### Main endpoints (excerpts)
- GET /sentinelle/api/v1/confluence/health
- GET /sentinelle/api/v1/confluence/spaces
- GET /sentinelle/api/v1/confluence/spaces/{spaceKey}
- GET /sentinelle/api/v1/confluence/spaces/{spaceKey}/pages
- GET /sentinelle/api/v1/confluence/spaces/{spaceKey}/search?query=...&limit=20
- GET /sentinelle/api/v1/confluence/pages/{pageId}
- PUT /sentinelle/api/v1/confluence/pages/{pageId}

### Scan & streaming endpoints (updated)
- GET /sentinelle/api/v1/scans/last
- GET /sentinelle/api/v1/scans/last/spaces
- GET /sentinelle/api/v1/scans/last/items
- POST /sentinelle/api/v1/scans/purge
- POST /sentinelle/api/v1/scans/{scanId}/resume
- SSE GET /sentinelle/api/v1/stream/confluence/space/{spaceKey}/events
- SSE GET /sentinelle/api/v1/stream/confluence/spaces/events[?scanId={scanId}]

Examples:
- Stream a space (curl):
  ```bash
  curl -N http://localhost:8080/sentinelle/api/v1/stream/confluence/space/WIKI/events
  ```
- Stream all spaces (new scan):
  ```bash
  curl -N "http://localhost:8080/sentinelle/api/v1/stream/confluence/spaces/events"
  ```
- Resume an interrupted scan (on UI side, append `?scanId=` to the SSE URL):
  ```bash
  curl -N "http://localhost:8080/sentinelle/api/v1/stream/confluence/spaces/events?scanId=<SCAN_ID>"
  ```

## Architecture
```
src/main/java/com/example/sentinelle/
├── domain/                      # Business core (entities, value objects, ports)
├── application/                 # Use cases (orchestration)
└── infrastructure/              # Technical adapters + Spring configuration
    ├── confluence/adapter/in    # REST controllers Confluence
    ├── pii/reporting/adapter/in # REST + SSE scan controllers
    ├── .../adapter/out          # External clients (Confluence, gRPC PII, DB)
    └── config                   # Spring configuration
```

### Clean Architecture — Layer Roles (placement guide)

This project follows Clean Architecture inspired by DDD and Hexagonal Architecture. The goal is to make explicit where to place each element and why.

General dependency rules (inward dependency principle):
- Presentation -> Application -> Domain.
- Infrastructure implements ports defined in Domain/Application and is injected inward (not the reverse).
- Domain does not depend on any other layer.

Domain (business core — stable)
- Contains:
  - Business entities and aggregates (model/), Value Objects, invariants.
  - Interfaces (ports) for business services and repositories (service/), defined on the domain side.
  - Domain services as interfaces (ArchUnit rule of the project).
  - Business exceptions and domain events if necessary.
- Does not contain:
  - Technical access (HTTP, DB, gRPC, Spring annotations, framework logs).
  - Controllers, DTOs, technical mappers.
- Allowed dependencies: Java/stdlib and internal types. No dependency to Application/Presentation/Infrastructure.
- Examples in this repo: com.example.sentinelle.domain.model.*, com.example.sentinelle.domain.service.* (ScanCheckpointRepository, etc.).

Application (use cases — orchestration)
- Contains:
  - Application services and use cases (by functionality), orchestration and transactional rules.
  - Input port interfaces on the application side if necessary.
  - Application DTOs (input/output contracts) in application.dto according to ArchUnit tests.
- Does not contain:
  - Direct DB/HTTP/gRPC access, technical implementations, Spring configurations.
  - Complex business logic (it stays in Domain).
- Allowed dependencies: towards Domain (entities/ports), utilities. Not towards Infrastructure.
- Examples in this repo: com.example.sentinelle.application.* (ConfluenceScanStreamerServiceImpl, etc.), DTOs in application.dto when they exist.

Infrastructure (technical adapters)
- Contains:
  - Implementations of ports (repositories, external clients) defined in Domain/Application.
  - Adapters to Confluence, gRPC PII, persistence (JPA/JDBC), mappings to technical models.
  - Technical Spring configurations in infrastructure.config (ArchUnit rule).
  - REST controllers under infrastructure.adapter.in (HTTP/SSE input ports).
- Does not contain:
  - Business logic/use cases.
- Allowed dependencies: frameworks (Spring Data, WebClient/HttpClient, gRPC, etc.), and towards interfaces (ports) of Domain/Application. No dependency towards Presentation.
- Examples in this repo: com.example.sentinelle.infrastructure.confluence.*, .infrastructure.pii.*, .infrastructure.scan.*, configs under .infrastructure.config.

Input Adapters (Controllers — in this project)
- REST controllers reside under infrastructure.adapter.in according to ArchUnit tests.
- They expose use cases from the Application layer via HTTP/SSE, and delegate all business logic to Domain/Application.

Where to place a new element?
- New business rule, new field or invariant: Domain (model/, possibly service/ for a domain service).
- New use case (e.g., "launch a scan on a space"): Application (application service + use of domain ports).
- Access to an external system (Confluence, PII gRPC, DB): Infrastructure (adapter implementing the port defined on domain/application side).
- New HTTP endpoint: Infrastructure (adapter.in controller) + mapping to application DTOs.

References
- Robert C. Martin — Clean Architecture.
- Eric Evans — Domain-Driven Design.
- Vaughn Vernon — Implementing Domain-Driven Design.
- Articles: RedHat "Implementing clean architecture solutions: A practical example", Baeldung "Spring Boot Clean Architecture", open-source examples.

Note on project scope
- The scope has been simplified: all features related to RAG/AI and vectorization (pgvector, Qdrant) have been removed.
- If you still see configuration keys associated with these modules in application.yml, they are no longer functionally used.

## Development

### Tests
```bash
./mvnw test
```

### Debug
```bash
java -jar target/sentinelle-0.0.1-SNAPSHOT.jar --logging.level.com.example.sentinelle=DEBUG
```

## Troubleshooting gRPC (port 50051)
- Ensure that only the Python server listens on 50051.
- Verify via `grpcurl -plaintext localhost:50051 list` that `pii_detection.PIIDetectionService` appears.
- "unknown service" error: usually related to another service bound to the port, lack of reflection, or wrong endpoint.
The following command helps to ensure no process is listening on 50051:
```shell
Get-NetTCPConnection -LocalPort 50051 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
```


## Contribution
1. Fork the project
2. Create a branch (`git checkout -b feature/AmazingFeature`)
3. Commit (`git commit -m 'Add AmazingFeature'`)
4. Push (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License
[To be defined]

## Support
- Create an issue on GitHub

# AI Sentinel — OpenWiki quickstart

AI Sentinel scans **Confluence spaces** (pages and attachments) for **personally identifiable
information**, stores every finding **encrypted**, shows them live in a dashboard, and lets an
operator **triage** them (false positive / manually handled) or **redact them in place** inside the
source Confluence page.

The product answers a compliance need (GDPR / nLPD): find the personal data already scattered
across a corporate wiki, prove what was found and when, and remove it without a manual page-by-page
review. Two consequences shape the whole codebase:

- **PII values never leave the deployment.** Detection runs locally (Presidio, regexes, a locally
  served LLM), values are encrypted at rest (AES-GCM), and every decryption is written to an audit
  table.
- **Detection is reconfigurable at runtime.** Which detectors run, which PII types are kept, and
  which thresholds apply live in PostgreSQL, not in code or in a config file, so an operator can
  retune the scan from the UI without restarting a service.

## The three services

| Service | Stack | Role | Entry point |
|---|---|---|---|
| `pii-reporting-ui/` | Angular 21, PrimeNG, Transloco | Dashboard, PII settings, obfuscation review | `pii-reporting-ui/src/app/app.routes.ts` |
| `pii-reporting-api/` | Java 25, Spring Boot 4.0.0-M2, WebFlux + Armeria | Confluence crawl, scan orchestration, persistence, remediation, Excel export | `pii-reporting-api/src/main/java/pro/softcom/aisentinel` |
| `pii-detector-service/` | Python (3.11 in the image), gRPC | Runs the detectors and returns findings | `pii-detector-service/pii_detector/server.py` |

They share two contracts: the gRPC schema in `proto/pii_detection.proto`, and the PostgreSQL
database (the API owns the schema; the detector reads its configuration from it).

## Run it

The supported path is Docker Compose. First start bootstraps the internal secrets manager
(Infisical) and **will show errors for `pii-reporting-api` — that is expected**; the application
containers must then be recreated.

```bash
docker compose up -d
docker compose up -d --force-recreate pii-detector pii-reporting-api pii-reporting-ui
```

Development stack (local builds, extra tooling, Postgres exposed on the host):

```bash
docker compose -f docker-compose.dev.yml up -d
```

| Surface | URL |
|---|---|
| Web UI | http://localhost:4200 |
| REST API | http://localhost:8080/ai-sentinel/api/v1 |
| Swagger UI | http://localhost:8080/ai-sentinel/swagger-ui.html |
| Armeria internal (health, metrics, docs) | http://localhost:8090 |
| gRPC detector | `localhost:50051` |
| Infisical (secrets) | http://localhost:8082 |
| pgAdmin | http://localhost:5050 |
| PostgreSQL (dev stack only) | `127.0.0.1:5435` |

Confluence credentials are entered in the UI (`Settings`), not in a file — they are stored
encrypted in `confluence_connection_config`. See [Operations](operations.md) for the full
configuration surface, secrets bootstrap, and migration caveats.

Then: open the dashboard, pick one or more spaces, start a scan, and watch findings stream in.

## Repository map

```
proto/pii_detection.proto        # the single cross-service contract (DetectPII)
pii-detector-service/            # Python gRPC detection service
  pii_detector/domain            # PIIEntity, PIIType, DetectorSource, merge rules
  pii_detector/application       # CompositePIIDetector orchestration
  pii_detector/infrastructure    # detectors, post-filter, DB config adapter, gRPC servicer
  config/                        # TOML settings (regex patterns, Presidio, global knobs)
pii-reporting-api/               # Spring Boot backend, hexagonal
  src/main/java/.../domain       # business core, no framework
  src/main/java/.../application  # use cases + ports
  src/main/java/.../infrastructure  # REST/SSE controllers, JPA, gRPC client, Confluence client
  init-scripts/                  # numbered SQL migrations (000 → 016)
pii-reporting-ui/                # Angular dashboard
docker-compose.yml               # production stack (pre-built ghcr.io images)
docker-compose.dev.yml           # development stack (local builds)
docker-compose-dgnsi.yml         # customer-specific overlay (certificates)
qa/scan-status-bugs/             # QA campaign reports + fix report for scan-status bugs
benchmarks/pii-dataset-eval/     # dataset builder for detector precision/recall evaluation
docs/                            # component diagram, design specs, screenshots
```

## Where to go next

- **[Architecture / System overview](architecture/system-overview.md)** — runtime topology, the
  "database as configuration bus" principle, hexagonal layering, and what a cross-stack change costs.
- **[Architecture / Detection pipeline](architecture/detection-pipeline.md)** — the Python service:
  three detectors, merge, type gate, deterministic post-filter, chunking and concurrency.
- **[Architecture / Backend API](architecture/backend-api.md)** — the Java module: REST/SSE surface,
  reactive scan pipeline, persistence model, encryption and audit.
- **[Architecture / Frontend UI](architecture/frontend-ui.md)** — Angular routes, the
  polling-vs-SSE split, dashboard and obfuscation state services.
- **[Workflows / Scan](workflows/scan-workflow.md)** — end-to-end scan, scan scope, checkpoint
  status machine, pause / resume / reconnect semantics.
- **[Workflows / Remediation](workflows/remediation-workflow.md)** — finding identity, false-positive
  suppression, in-place redaction jobs, report regeneration.
- **[Operations](operations.md)** — stacks, secrets, migrations, tests, CI, known traps.

## Existing documentation, and where it is stale

The repository already carries substantial hand-written docs. Prefer them for narrative detail, but
**trust the source over the docs on the points listed below** — all verified against the code at
`e6a23010`.

Useful and current:
- `pii-reporting-api/README.md` — the "Clean Architecture — Layer Roles (placement guide)" section is
  the canonical answer to *where do I put this class?*. Referenced from
  [Backend API](architecture/backend-api.md) rather than duplicated.
- `pii-reporting-api/ENCRYPTION.md` — envelope encryption design, token format, key rotation.
- `CONTRIBUTING.md` — per-module dev setup, coding standards, commit convention.
- `qa/scan-status-bugs/` — the QA campaign that produced the current scan-status semantics, plus
  `FIXES-REPORT.md` explaining each fix and why (invaluable before touching scan status).
- `docs/superpowers/specs/` — design notes for the pnpm migration and the Ministral concurrency
  auto-tune.

Known stale claims:
- Root `README.md` advertises **GLiNER** detectors and zero-shot custom labels. Those detectors were
  removed (commit `0bc85df6`); `proto/pii_detection.proto` now *reserves* the `GLINER`, `GLINER2` and
  `OPENMED` enum tags. The live detectors are **Presidio, Regex, Ministral**.
- Root `README.md` lists endpoints such as `GET /ai-sentinel/api/scans`. The real base path is
  `/ai-sentinel/api/v1/...` (see [Backend API](architecture/backend-api.md)).
- Root `README.md` says the frontend uses TailwindCSS; `pii-reporting-ui/package.json` shows
  **PrimeNG + PrimeFlex** and **Transloco** for i18n.
- Root `README.md` and `pii-detector-service/README.md` point at files that do not exist:
  `docker-compose.prod.yml`, `pii-detector-service/docs/*.md`, `docs/PRODUCTION_SETUP.md`.
- `pii-detector-service/README.md` documents a `StreamDetectPII` RPC and `mask_pii` / `chunk_size`
  request fields. The proto exposes **only** `DetectPII`, with three request fields
  (`content`, `threshold`, `fetch_config_from_db`).
- `pii-detector-service/README.md` lists `regex_detection_enabled` / `presidio_detection_enabled` in
  `config/detection-settings.toml`; that file now explicitly defers those flags to the database.
- `pii-reporting-api/README.md` lists `PUT /api/v1/confluence/pages/{pageId}`; `ConfluenceController`
  has no such mapping (page writes happen through the redaction adapter instead).
- The Python version is stated three different ways and none matches the image: root `README.md` says
  3.13, `pii-detector-service/pyproject.toml` requires `>=3.9`, CI uses 3.12, and
  `pii-detector-service/Dockerfile` builds and runs on **`python:3.11-slim`**. The image wins. (For
  reference, the other two images are `amazoncorretto:25` and `node:22-alpine` → `nginx:alpine`.)
- Licensing is inconsistent between files: root `LICENSE.md` / `README.md` say Apache-2.0,
  `pii-detector-service/README.md` claims MIT. Treat the root license as authoritative and raise the
  discrepancy rather than propagating either claim.

# Operations

How the system is deployed, configured, bootstrapped and tested, plus the traps that cost the most time
in practice.

## The three Compose stacks

| File | Purpose | Notable differences |
|---|---|---|
| `docker-compose.yml` | Production / default | Pre-built `ghcr.io/softcom-technologies-organization/ai-sentinel-*` images pinned to a tag (currently `v1.2.0-rc.1`). Postgres has **no host port** and **does not mount `init-scripts/`** |
| `docker-compose.dev.yml` | Development | Builds images locally, exposes Postgres on `127.0.0.1:5435`, mounts `init-scripts/` as `docker-entrypoint-initdb.d`, adds MailHog, mounts a `huggingface-cache` volume |
| `docker-compose-dgnsi.yml` | Customer overlay | Certificate handling for a specific deployment; applied on top of the production stack |

Services in both main stacks: `secrets-bootstrap`, `infisical-configurator`, `infisical`,
`infisical-db`, `infisical-redis`, `postgres`, `pii-detector`, `pii-reporting-api`, `pii-reporting-ui`,
`pgadmin` (+ `mailhog` in dev).

`chore(docker): update images to v1.2.0-rc.1 across all services` (`e6a23010`) shows the release
ritual: image tags are bumped in **both** `docker-compose.yml` and `docker-compose-dgnsi.yml`.

## First start: the Infisical bootstrap

Secrets are not files you write; they are generated and stored in a self-hosted Infisical instance.

1. `secrets-bootstrap` (`docker/secrets-bootstrap-dev.sh`) generates the files under `secrets/`:
   Infisical internal encryption key and auth secret, database passwords, machine-identity credentials.
2. `infisical-configurator` (`docker/infisical-configurator-dev.sh`) creates the Infisical project and
   machine identity and seeds the application secrets (database credentials, the PII encryption key).
3. `pii-reporting-api` and `pii-detector` start via `docker-entrypoint.sh`, which authenticates against
   Infisical with universal auth and then `exec infisical run …` so secrets arrive as environment
   variables. The application process never reads a secret file.

**The first `docker compose up -d` shows errors for `pii-reporting-api`. That is expected** — the
configurator has not finished seeding yet. The documented remedy is to recreate the application
containers afterwards:

```bash
docker compose up -d --force-recreate pii-detector pii-reporting-api pii-reporting-ui
```

The same applies after a full teardown (`docker compose down -v --rmi all`): the bootstrap re-runs and
step 2 must be repeated.

`secrets/README.md` documents the manual fallback, including the exact required formats
(encryption key 32 hex chars, auth secret 44 base64 chars) and how to strip stray newlines — a
frequent cause of `Invalid key length`. Never commit anything from `secrets/`; `.gitignore` covers
`*.txt` there, but a broad `git add -u` can still stage a tracked secret placeholder — check the staged
set before committing.

**Back up the PII encryption key.** Without it, every stored finding is unrecoverable, and
`valueFingerprint`s (derived from it) no longer match, orphaning remediation rows. Key generation and
rotation are documented in `pii-reporting-api/ENCRYPTION.md` and
`pii-reporting-api/create-new-encryption-key.ps1`.

## Database schema: two mechanisms, know which one applies

This is the most surprising operational fact in the repository:

- `pii-reporting-api/init-scripts/*.sql` (000 → 016) run **only** through Postgres'
  `docker-entrypoint-initdb.d`, which the **dev** stack mounts and the **production** stack does not —
  and which only runs on an *empty* data directory.
- In every other case the schema comes from Hibernate `ddl-auto: update`, plus `data.sql`
  (`mode: always`, `defer-datasource-initialization: true`, inserts guarded by
  `ON CONFLICT DO NOTHING`) which seeds `pii_type_config` and the `pii_detection_config` row.

Consequences:

- Adding a nullable column works transparently; **adding a `NOT NULL` column without a default fails
  `ddl-auto` on a populated database**. Apply the migration by hand there.
- `ddl-auto` never creates indexes, constraints, comments, or the partial unique index that enforces
  one running redaction job per space. Those exist **only** in `init-scripts`. A production database
  created purely by Hibernate is not identical to a dev one — verify before assuming a constraint is
  active.
- The numbering has duplicates (`013-add-ministral-columns.sql` / `013-scan-pii-type-counts.sql`,
  `014-*` ×2, `015-*` ×2) because branches were merged in parallel. They are ordered
  lexicographically by Postgres, so pick a fresh distinct prefix for a new script.

Direct database access: pgAdmin at http://localhost:5050, or `psql` against `127.0.0.1:5435` in the dev
stack. `psql` from the host against the production stack requires `docker compose exec postgres`, since
no port is published.

## Configuration surfaces

Four places, in increasing order of runtime volatility:

1. **Docker Compose / Infisical** — image tags, database credentials, encryption key, Confluence proxy
   settings (`CONFLUENCE_ENABLE_PROXY`, `CONFLUENCE_PROXY_HOST/PORT/USERNAME/PASSWORD`).
2. **`application.yml` + environment** — the backend's structural settings:

   | Key | Env | Default | Effect |
   |---|---|---|---|
   | `server.servlet.context-path` | — | `/ai-sentinel` | Base path of every route |
   | `pii-detector.client` | `PII_DETECTOR_CLIENT` | `armeria` | gRPC transport (`armeria`\|`grpc`) |
   | `pii-detector.connection-timeout-ms` / `.request-timeout-ms` | `PII_DETECTOR_*_TIMEOUT_MS` | 1800000 | 30 min; raised from 5 min because long CPU-bound detections were being cut |
   | `scan.timeouts.pii-detection` | `SCAN_PII_DETECTION_TIMEOUT` | `1810s` | Must stay **greater** than the gRPC timeout |
   | `scan.page-concurrency` | `PII_SCAN_PAGE_CONCURRENCY` | `1` | Pages detected concurrently |
   | `pii.reporting.allow-secret-reveal` | `PII_REPORTING_ALLOW_SECRET_REVEAL` | *(none)* | Gates plaintext reveal; **no default — must be set** |
   | `pii.remediation.enabled` | `PII_REMEDIATION_ENABLED` | `true` | Gates the redaction endpoints |
   | `pii.discovered-labels.enabled` | `PII_DISCOVERED_LABELS_ENABLED` | `false` | Collects Ministral's unconfigured labels |
   | `pii.audit.retention-days` | — | `730` | Audit retention (nLPD); purge cron `0 0 3 * * ?` |
   | `pii-reporting-api.findings-export-directory` | `PII_REPORTING_API_EXPORT_DIR` | `/personally-identifiable-information-scan-results` | Where Excel reports are written |
   | `ai-sentinel.confluence.cache.refresh-interval-ms` | `CONFLUENCE_CACHE_REFRESH_INTERVAL` | 300000 | Space cache refresh |

3. **Detector TOML + environment** — `config/detection-settings.toml`, `config/models/*.toml`,
   `PII_DETECTOR_PORT`, `PII_DETECTOR_WORKERS`, `PII_WORKER_PROCESSES`,
   `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`.
4. **Database, edited from the UI** — everything about detection behaviour. See
   [System overview](architecture/system-overview.md#the-database-is-the-configuration-bus).

## Health, logs, metrics

| Check | How |
|---|---|
| Backend health | `GET http://localhost:8080/ai-sentinel/actuator/health` |
| Armeria internal (health, metrics, docs) | http://localhost:8090 |
| Prometheus metrics | `/actuator/prometheus`; `pii.scan.chars.total`, `pii.scan.duration` tagged `phase=grpc.client` |
| Detector reachable | `grpcurl -plaintext localhost:50051 list` → `pii_detection.PIIDetectionService` |
| Confluence reachable | `GET /ai-sentinel/api/v1/confluence/health` |
| Backend logs | `${LOG_PATH:pii-reporting-api/logs}`; `infrastructure.confluence` and `infrastructure.pii` are at DEBUG by default |

Useful log tags, consistent across both services: `[THROUGHPUT]` (chars/second per phase),
`[FINDING_TRACKER]` (in/out counts per pipeline stage), `[SCAN]`, `[CHECKPOINT]`, `[PII_REMEDIATION]`,
`[PREFILTER]`.

## Tests and CI

```bash
# Backend — no aggregator POM at the root
mvn -f pii-reporting-api/pom.xml test
mvn -f pii-reporting-api/pom.xml test -Dtest=HexagonalArchitectureTest

# Detector
cd pii-detector-service && pytest tests/unit
pytest --cov=pii_detector --cov-report=html

# Frontend
cd pii-reporting-ui && pnpm install && pnpm test
pnpm e2e
```

`.github/workflows/`:

- `run-tests.yml` — the reusable job set: detector (Python 3.12, generates protobuf stubs first, then
  unit tests + Codecov), API (JDK 25, `mvn test`), UI (Node + corepack pnpm, `pnpm test:coverage`),
  and a final status-check job.
- `ci-tests.yml` — runs `run-tests.yml` on every push outside `main`/`develop` and on PRs to them.
- `build-test-publish-docker-images.yml` — runs the tests, then publishes images to GHCR on
  `main`, `develop`, `release/**`, or via `workflow_dispatch` with a custom tag and per-service
  toggles. `docs/MANUAL_DOCKER_PUBLISH.md` walks through the manual dispatch (in French).

Practical notes:

- Run the Maven `test` **phase**, not `surefire:test` alone — JaCoCo's `prepare-agent` sets `argLine`,
  and an isolated surefire run fails on the unsubstituted `@{argLine}`. Failure details land in
  `target/surefire-reports/*.txt`.
- `*IT` tests use Testcontainers (Docker required). Some detector integration tests need a live LM
  Studio endpoint and skip themselves — read the skip condition before calling a non-run a regression.
- Angular component specs must go through the builder (`ng test --include=...`); invoking Vitest
  directly on a spec fails to resolve `templateUrl`.
- Playwright E2E needs a real stack. A green run can be an environment artefact (a reused dev server, a
  backend already up); confirm what the run connected to.

## Quality tooling

- SonarQube: `sonar-project.properties` in `pii-detector-service/` and `pii-reporting-ui/`, plus
  `run-sonar.ps1` helpers; the backend runs Sonar through Maven. Standing backlogs are tracked in
  `BACKLOG-SONAR.md`, `BACKLOG-SONAR-UI.md`, `BACKLOG-SONAR-pii-detector-service.md`.
- Qodana: `qodana.yaml`.
- Renovate: `renovate.json` with `renovate-local.sh` / `renovate-local.ps1` to run it on demand in
  Docker. Major upgrades are gated behind the dependency dashboard.
- Licence inventory: `LICENSES-SOURCES.md`, `pii-detector-service/doc/licenses.md`,
  `pii-detector-service/doc/pipdeptree.txt`.

## Traps worth knowing up front

- **`*.sh` must stay LF.** `.gitattributes` enforces `*.sh text eol=lf`; with `core.autocrlf=true` on
  Windows a CRLF entrypoint makes `sh` fail with `illegal option -` and the container never boots.
- **Detector outside Compose**: `DB_HOST` defaults to `postgres`. Unset on a host run, the config fetch
  fails, all detector flags fall back to off, and the scan completes with **zero detections** and no
  obvious error.
- **Stale generated protobuf stubs.** `pii_detector/proto/generated/` is gitignored; a stale stub raises
  `AttributeError` on a field the proto declares. Regenerate with
  `python -m pii_detector.proto.generate_pb`, using the same interpreter as the runtime — a
  `grpcio-tools` newer than the installed `grpcio` produces stubs the runtime refuses.
- **A port already bound.** Another process on `8080` or `50051` binds silently and the app looks up but
  answers wrongly. `pii-reporting-api/README.md` has a PowerShell one-liner to free `50051`.
- **First detector start is slow** (model and spaCy downloads); `docker compose logs -f pii-detector` is
  the only progress indicator.
- **The `huggingface-cache` volume matters** (dev stack). Losing it means re-downloading models; the
  Ministral tokenizer falls back to character-ratio chunking when it cannot be loaded offline.
- Root `README.md` refers to `docker-compose.prod.yml`, which does not exist. The production stack is
  `docker-compose.yml`.

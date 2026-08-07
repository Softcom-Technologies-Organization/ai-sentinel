# Backend API (`pii-reporting-api`)

Spring Boot 4.0.0-M2 on Java 25, hexagonal, ~300 main classes. It is the system's centre of gravity:
it crawls Confluence, drives detection, owns the database schema, encrypts findings, serves the
dashboard, and executes redaction jobs.

Context path is `/ai-sentinel`; every REST route below is relative to `/ai-sentinel/api/v1`.

## Module layout

```
src/main/java/pro/softcom/aisentinel/
  domain/          config · confluence · pii/{detection,export,remediation,reporting,scan,security}
  application/     same slices, each with port/in, port/out, usecase, service
  infrastructure/  same slices, each with adapter/in (+dto, mapper), adapter/out (+jpa, entity), config
  resources/       application.yml, data.sql
init-scripts/      000 → 016 SQL migrations
```

The slicing is **by business capability first, layer second**. A feature therefore touches one
vertical slice across three layers rather than three unrelated packages. `pii-reporting-api/README.md`
carries the canonical placement guide ("Clean Architecture — Layer Roles"); read it before adding a
class, and remember `HexagonalArchitectureTest` will fail the build if the placement is wrong (rules
listed in [System overview](system-overview.md)).

Wiring is explicit: use cases are plain constructor-injected objects declared as beans in
`infrastructure/config/ApplicationUseCasesConfig.java` and
`infrastructure/config/RemediationUseCasesConfig.java`. Application classes carry **no Spring
annotations** — that is enforced, not stylistic. When you add a use case you must also add its
`@Bean` method.

## REST and SSE surface

| Area | Controller | Routes |
|---|---|---|
| Confluence browse | `ConfluenceController` | `GET /confluence/health`, `/confluence/spaces`, `/confluence/spaces/{key}`, `/confluence/spaces/{key}/pages`, `/confluence/spaces/{key}/search`, `/confluence/pages/{id}`, `/confluence/spaces/update-info`, `/confluence/spaces/{key}/update-info` |
| Confluence connection | `ConfluenceConnectionConfigController` | `GET`/`PUT /confluence/connection-config`, `POST /confluence/connection-config/test` |
| Scan streaming | `ConfluencePersonallyIdentifiableInformationScanController` | SSE `GET /stream/confluence/space/{key}/events`, SSE `GET /stream/confluence/spaces/events`, SSE `GET /stream/confluence/spaces/events/selected`, `POST /stream/{scanId}/pause`, `POST /stream/{scanId}/resume` |
| Scan read model | `LastConfluencePersonallyIdentifiableInformationScanController` | `GET /scans/last`, `/scans/last/spaces`, `/scans/last/items`, `/scans/dashboard/spaces-summary` |
| Per-space stats | `ScanSpaceStatsController` | `GET /scans/dashboard/spaces/{key}/stats` |
| Purge | `ScanPurgeController` | `POST /scans/purge` |
| PII reveal | `PiiAccessController` | `GET /pii/config/reveal-allowed`, `POST /pii/reveal-page` |
| Detection config | `PiiDetectionConfigController` | `GET`/`PUT /pii-detection/config` |
| PII taxonomy | `PiiTypeConfigController` | `GET`/`POST /pii-detection/pii-types`, `GET /pii-detection/pii-types/{detector}`, `GET .../grouped`, `GET .../grouped/by-category`, `PUT`/`DELETE .../{detector}/{piiType}`, `PUT .../bulk` |
| Discovered labels | `DiscoveredLabelController` | `GET /pii-detection/discovered-labels`, `POST .../{label}/promote`, `POST .../{label}/ignore` |
| Concurrency benchmark | `ConcurrencyBenchmarkController` | `POST /pii-detection/concurrency-benchmark/run`, `GET .../status` |
| Remediation | `PiiRemediationController` | `GET /pii/remediation/config`, `POST .../findings/search`, `POST .../findings/status`, `POST .../findings/status/by-selection`, `POST .../plan`, `POST .../jobs`, `GET .../jobs/{id}` |
| Polling config | `ConfigController` | `GET /config/polling` |

Swagger UI is served at `/ai-sentinel/swagger-ui.html` (springdoc). Armeria exposes health, metrics,
docs and actuator separately on port 8090, and the Prometheus endpoint publishes
`pii.scan.chars.total` and `pii.scan.duration` tagged `phase=grpc.client`.

There is **no authentication layer**: `SecurityConfig` exists but the product assumes deployment on a
trusted network. Do not read the absence of auth on an endpoint as an oversight to fix opportunistically.

## The scan pipeline

`AbstractStreamConfluenceScanUseCase` is the heart of the module and the file to read first. Two
subclasses specialise it: `StreamConfluenceScanUseCase` (fresh scan, all spaces or a selection) and
`StreamConfluenceResumeScanUseCase` (resume from checkpoints). Their shared collaborators are bundled
in the `ScanPipelineDependencies` record so the constructor stays readable.

The pipeline is Reactor `Flux<ConfluenceContentScanResult>`, and its shape encodes several hard-won
constraints:

- **`flatMapSequential(..., pageConcurrency)`** — up to `pageConcurrency` pages are detected at once
  yet events are still **emitted in source order**, preserving the checkpoint and SSE ordering that a
  plain `concatMap` used to guarantee. Pages are stamped with `.index()` *before* the concurrent
  region so the progress index is stable whatever the concurrency. Configured by
  `scan.page-concurrency` (`PII_SCAN_PAGE_CONCURRENCY`, default `1`).
- **Checkpoints are persisted synchronously** (`persistCheckpointSynchronously`). Everything else
  — severity counters, per-type counters, event store, per-space statistics — is fire-and-forget on
  `boundedElastic` with retry and swallowed errors. The comment in the code states the reason: an
  async checkpoint let a page refresh read stale state and re-scan pages, doubling severity counts.
  **Do not "optimise" that call to async.**
- **Attachments before page content.** For each page, attachments are extracted and analysed first,
  then the page body — which is why attachment progress uses `currentIndex - 1`.
- **Errors never abort a scan.** Timeouts (Reactor `TimeoutException`, gRPC `DEADLINE_EXCEEDED`) and
  general failures are converted into `scanError` events; `onErrorContinue` keeps the flux alive.
  gRPC exceptions are searched for **in the cause chain**, because they arrive wrapped in
  `PiiDetectionException`.
- **False positives are suppressed at scan time.** The space's false-positive finding ids are loaded
  once at the start of the flux, and every event is filtered through `ScanTimeFalsePositiveSuppressor`
  before any consumer sees it — so scan events, severity counters, per-space statistics and the live
  SSE view stay consistent. See [Remediation workflow](../workflows/remediation-workflow.md).

Supporting services in `application/pii/reporting/service/`: `ScanEventFactory` (builds each event
type), `ScanProgressCalculator`, `ScanCheckpointService`, `ScanEventDispatcher` (publishes only after
transaction commit), `ScanSpaceStatsCollector`, `AttachmentProcessor`, `DiscoveredLabelCollector`, and
the `parser/` package (`HtmlContentParser` strips Confluence markup with jsoup before detection).

Reconnection is served from `infrastructure/pii/scan/service/ScanEventBuffer`, a per-scan in-memory
ring buffer (1000 events) replayed to a client that reconnects, complemented by database checkpoints
for long-term recovery. `ScanEventSequencer` keeps event ordering across that boundary.

## Detector client

`GrpcPiiDetectorArmeriaClientAdapter` implements the `PiiDetectorClient` out-port, over a pluggable
transport (`PiiGrpcTransport`): Armeria by default, Netty as an alternative
(`pii-detector.client=armeria|grpc`). Timeouts default to **30 minutes** for both connection and
request (`PII_DETECTOR_CONNECTION_TIMEOUT_MS`, `PII_DETECTOR_REQUEST_TIMEOUT_MS`); the comment records
that the previous 5 minutes was cutting long detections on CPU-bound machines. The Reactor-side
timeout `scan.timeouts.pii-detection` (1810s) is deliberately **larger** than the gRPC one so the gRPC
error surfaces first with a precise cause.

## Persistence model

Owned tables (see `init-scripts/`, applied in numeric order):

| Table | Purpose |
|---|---|
| `scan_runs`, `scan_events` | Append-only event log per scan; `payload` JSONB with a GIN index, PII values encrypted |
| `scan_checkpoints` | Per `(scan_id, space_key)` progress and status — the resumable state and the persisted scan scope |
| `scan_severity_counts`, `scan_pii_type_counts` | Incremental aggregates per scan × space, by severity and by PII type |
| `scan_space_stats`, `scan_detector_stats` | Throughput and per-detector statistics of a scan |
| `confluence_spaces` | Cached space list, refreshed by a scheduled job |
| `confluence_connection_config` | Confluence URL, credentials, deployment type (encrypted) |
| `pii_detection_config` | Single row `id = 1`: detector flags, thresholds, Ministral and LM Studio settings |
| `pii_type_config` | One row per detector × PII type: enabled, threshold, severity, detector label |
| `ministral_discovered_label` | Open-vocabulary labels awaiting operator review |
| `pii_finding_remediation`, `pii_redaction_job` | Finding lifecycle and redaction jobs |
| `pii_access_audit` | Every decryption of a PII value, with purpose and actor |

Two things to know before touching the schema:

1. **Hibernate `ddl-auto: update` is on**, and `data.sql` runs on every startup with
   `defer-datasource-initialization: true` and `mode: always` (inserts are `ON CONFLICT DO NOTHING`).
   The numbered `init-scripts` are only mounted by the **dev** Compose stack — see
   [Operations](../operations.md) for what that implies.
2. `scan_severity_counts` has an FK to `scan_checkpoints` with `ON DELETE CASCADE`. That is why stale
   checkpoints are *transitioned to `INTERRUPTED`* rather than deleted: deleting them would destroy
   the counters (`qa/scan-status-bugs/FIXES-REPORT.md`, bug #20).

Read side: `JpaScanResultQueryAdapter` reconstructs the dashboard read model from `scan_events`;
`ScanReportingUseCase` assembles per-space summaries, facets and counters and applies
`FalsePositiveDetectionFilter` at read time.

## Security: encryption, reveal, audit

Findings are stored encrypted with AES-GCM envelope encryption. `ScanResultEncryptor` (application) is
the orchestration point; `AesGcmEncryptionAdapter` plus `EncryptionKeyProvider` /
`EncryptionConfiguration` are the technical side. **`pii-reporting-api/ENCRYPTION.md` is the reference
document** for the token format, KEK/DEK split, rotation and tamper detection — do not re-derive it.

Three gates guard plaintext:

- `pii.reporting.allow-secret-reveal` (`PII_REPORTING_ALLOW_SECRET_REVEAL`, **no default in
  `application.yml`**): when false, `sensitiveValue` is never streamed over SSE and
  `POST /pii/reveal-page` returns 403.
- Every decryption goes through an `AccessPurpose` (`REVEAL`, `REDACTION`, …) and is recorded by
  `PiiAccessAuditService` into `pii_access_audit`, purged by a scheduled job after
  `pii.audit.retention-days` (730, nLPD-compliant).
- `pii.remediation.enabled` (`PII_REMEDIATION_ENABLED`, default `true`): when false every
  `/pii/remediation/**` route except `GET /config` returns 403.

`PiiMaskingUtils` and `PiiContextExtractor` produce the masked value and surrounding context that the
UI shows when reveal is not allowed.

## Confluence integration

The out-ports (`application/confluence/port/out/`) are `ConfluenceClient`,
`ConfluenceAttachmentClient`, `ConfluenceAttachmentDownloader`, `AttachmentTextExtractor`,
`ConfluenceSpaceRepository`, `ConfluenceConnectionConfigRepository`, `ConfluenceUrlProvider`.
`ConfluenceAccessor` (application service) is the facade the scan pipeline uses.

Implementation notes worth knowing:

- Connection settings are **database-backed** (`DatabaseBackedConfluenceConnectionConfig`), not
  file-based, and support both Cloud and Data Center deployment types
  (`ConfluenceDeploymentType`).
- Attachment text extraction is a composite (`CompositeAttachmentTextExtractorAdapter`) over PDFBox
  and Apache Tika.
- The space list is cached in `confluence_spaces` and refreshed by
  `ScheduledConfluenceSpaceCacheRefreshJob` every `ai-sentinel.confluence.cache.refresh-interval-ms`
  (5 min). The dashboard reads the cache, not Confluence, so a brand-new space appears only after a
  refresh.
- An optional HTTP proxy is configurable through the `CONFLUENCE_*_PROXY_*` secrets.

## Excel report export

`ExportDetectionReportUseCase` writes one `.xlsx` per space into
`pii-reporting-api.findings-export-directory` (`PII_REPORTING_API_EXPORT_DIR`, default
`/personally-identifiable-information-scan-results`). It is **event-driven, never called by a REST
endpoint**:

- `SpaceScanCompletedListener` regenerates the report when a space finishes scanning.
- `SpaceFalsePositivesChangedListener` regenerates it when the space's false-positive set changes, so
  the file stays consistent with the operator's triage. Failures are swallowed on purpose — the
  status change already succeeded and the report is rebuilt on the next change or scan.

## Working in this module

```bash
# from the repository root — there is no aggregator POM
mvn -f pii-reporting-api/pom.xml test
mvn -f pii-reporting-api/pom.xml test -Dtest=HexagonalArchitectureTest
```

- Run the `test` **phase**, not `surefire:test` in isolation: JaCoCo's `prepare-agent` sets `argLine`,
  and an isolated surefire invocation fails on the unsubstituted `@{argLine}`.
- Integration tests ending in `IT` use Testcontainers PostgreSQL; `*IntegrationTest` classes boot a
  Spring context. Both need Docker.
- Tests are named `Should_ExpectedBehavior_When_StateUnderTest` (see `CONTRIBUTING.md`).
- When adding a use case, remember the three-place cost: the use case + its ports, the `@Bean`
  declaration in `ApplicationUseCasesConfig`/`RemediationUseCasesConfig`, and the controller with its
  DTO and mapper.

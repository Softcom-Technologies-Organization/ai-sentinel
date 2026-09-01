# Frontend UI (`pii-reporting-ui`)

Angular 21, standalone components, signals, PrimeNG for widgets and Transloco for i18n. 74 source
files, three routes, and one architectural decision worth internalising before touching anything:
**scan status comes from polling, findings come from SSE.**

## Routes and shells

`src/app/app.routes.ts`:

| Route | Component | Purpose |
|---|---|---|
| `/` | `AppShellComponent` → `ConfluenceDashboardComponent` | Space dashboard, scan control, live findings |
| `/settings` | `PiiSettingsComponent` | Detection configuration, PII taxonomy, Confluence connection |
| `/obfuscation` | `PiiObfuscationComponent` | Finding triage and bulk redaction review |
| `**` | → `/` | |

Routes are eagerly imported (no lazy loading). The app is bootstrapped from
`src/app/app.config.ts`; the API base URL is not held in an environment file — the dev server proxies
`/api` to `http://localhost:8080/ai-sentinel/api` (`proxy.conf.json`) and the production image serves
the built bundle through nginx (`nginx.conf`, `Dockerfile`).

## The polling / SSE split

This is the single most misunderstood part of the frontend. It was a deliberate correction: SSE status
events proved unreliable across reconnections, so status was moved to REST polling and SSE was reduced
to the live findings feed.

- **`ScanStatusPollingService`** (`core/services/`) polls `GET /scans/dashboard/spaces-summary` and is
  the **authoritative source** for `scanActive`, `scanPaused`, `actionPending`, per-space status,
  progress, and scan-completion detection (`scanCompleted$`). After every user action
  (start / pause / resume) `forceRefresh()` re-fetches immediately so buttons do not wait for the next
  tick. Poll interval comes from the backend (`GET /config/polling`).
- **`SseEventHandlerService`** (`features/confluence-dashboard/services/`) handles **only** `item`,
  `attachmentItem` and `scanError` events. `start`, `complete`, `multiStart`, `multiComplete` and
  `pageStart` are explicitly ignored — its docstring says so. Items are deduplicated by
  `pageId + attachmentName` and capped at **400 items per space**; `scanError` is non-fatal.

If a status badge is wrong, the bug is in the polling path or in the backend summary — not in the SSE
handler. If a finding card is missing or duplicated, it is the SSE path.

## Dashboard state services

`features/confluence-dashboard/services/` splits responsibilities deliberately; each service is
independently unit-tested.

| Service | Responsibility |
|---|---|
| `ScanControlService` | Start / pause / resume, user confirmation dialogs, SSE subscription lifecycle, state reset for a new scan |
| `SpaceDataManagementService` | The space list and its per-space data |
| `SpaceFilteringService` | Filtering and sorting of spaces |
| `FilterUrlStateService` | Filters mirrored in the URL, so a filtered view is shareable and survives reload |
| `PiiItemsStorageService` | The live findings buffer per space (with the 400-item cap) |
| `DashboardUiStateService` | Expansion, event log, transient UI flags |

Plus two pure utility modules that carry real logic and are the usual place to look first:
`spaces-dashboard.utils.ts` (the space table's actual read/write reference — `allSpaces()` is what
drives the badges) and `spaces-dashboard-stream.utils.ts` (stream event typing and payload coercion).
`scan-status.utils.ts` maps a backend `ScanStatus` to its UI label, including the derived
`PENDING` ("En attente") state — see [Scan workflow](../workflows/scan-workflow.md) for why that
derivation is delicate.

## Findings presentation

`features/pii-page-card/` renders one card per page, collapsed or expanded, with per-type rows and a
severity configuration (`severity.config.ts`). Shared presentation pieces live in `shared/`:
`confidence-indicator/`, `detector-tag/` (one badge per `DetectorSource` — an unmapped source shows as
`UNKNOWN_SOURCE`), and `pii-value-display/` (masked value, context, and reveal).

Reveal is gated server-side: the UI first asks `GET /pii/config/reveal-allowed`, and
`POST /pii/reveal-page` is audited on the backend. Never assume a value present in a payload is safe
to log or persist client-side.

## Settings

`features/pii-settings/` is a single large component covering: detector toggles, global and per-type
thresholds, the PII taxonomy grouped by category, the LM Studio endpoint, and the Ministral
concurrency benchmark trigger. It reads
and writes `core/services/pii-detection-config.service.ts` against
`/pii-detection/config` and `/pii-detection/pii-types`.

Because these settings are read by the Python detector on the next request, a change here has no
effect on pages already scanned — the UI does not (and should not) imply otherwise.

`features/confluence-settings/` handles the Confluence connection form, with a "test connection"
action, and `shared/components/confluence-config-banner/` nags when it is unconfigured.

## Obfuscation review

`features/pii-obfuscation/` implements the triage and redaction screen described in
[Remediation workflow](../workflows/remediation-workflow.md). Components:
`obfuscation-finding-row`, `obfuscation-bulk-bar` (bulk actions over a whole selection),
`obfuscation-confirm-dialog`, `obfuscation-job-progress`, `obfuscation-entry-button`. State is split
into `ObfuscationSelectionService` (what is selected, including group-level master checkboxes) and
`ObfuscationViewStateService`.

Two client-side invariants that come from the backend contract:

- **Pagination is per group, never flat.** The response carries `totalGroups`; paginating on a flat
  finding count produces wrong pages.
- Findings are grouped by value, so the count shown to the user is a count of *distinct values with an
  occurrence counter*, not a count of occurrences. The dashboard counts occurrences. The two numbers
  legitimately differ.

## i18n and accessibility

All user-facing strings go through Transloco with `src/assets/i18n/en.json` and `fr.json`. Adding a
label means editing **both** files — a missing key renders as the raw key. `TranslocoHttpLoader` loads
them at runtime; `core/components/language-selector/` switches locale.

`features/test-ids.constants.ts` centralises `data-testid` values shared with the Playwright suite;
add new ids there rather than inline.

## Tests

```bash
cd pii-reporting-ui
pnpm install            # pnpm is enforced: preinstall runs `npx only-allow pnpm`
pnpm test               # ng test → Vitest
pnpm test:coverage
pnpm e2e                # Playwright, needs the app + backend running
```

Coverage thresholds are enforced in `vitest.config.ts` (lines 50, functions 25, branches 45,
statements 50) — modest, so treat them as a floor rather than a target.

Run component tests through the Angular builder (`ng test --include=...`), not by invoking Vitest
directly on a spec: components declared with `templateUrl` need the builder's resource resolution, and
a direct Vitest run fails on `resolveComponentResources`.

E2E specs live in `e2e/` (`dashboard`, `obfuscation`, `scan-confirmation`, `scan-expand-items`,
`scan-pause`). They exercise a live stack; a green run against an already-running server or a stale
backend can be an environment artefact rather than a real pass — check what the run actually connected
to before trusting it.

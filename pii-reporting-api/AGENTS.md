<!-- bmad:context -->
<!-- Verified 2026-08-26 against 6e22133b (working tree had uncommitted changes). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## pii-reporting-api

Spring Boot backend, hexagonal layering: Confluence crawl, scan orchestration, persistence,
remediation and Excel export. Java 25, WebFlux and Armeria alongside the servlet stack.

## Where things are

- Where does this class go: `README.md`, section "Clean Architecture — Layer Roles (placement
  guide)". It is the canonical answer; `HexagonalArchitectureTest` fails on violations.
- Encryption design, token format and key rotation: `ENCRYPTION.md`

## Running and verifying

- `mvn test` skips the integration tests — surefire excludes `**/integration/*.java` by default and
  in the `ci-build` profile. Run them with `mvn test -Punit-and-integration-tests`.
- For a local run against `docker/docker-compose-db.yml` (container `ai-sentinel-db`, host port
  5433), set `DB_PORT=5433`: `application.yml` defaults to 5435, which is the dev compose stack.
- `init-scripts/` runs only when the Postgres volume is created. On an existing volume, a new
  numbered script never executes — the schema is otherwise maintained by `ddl-auto: update`, and
  `data.sql` replays on every boot.

## Conventions that differ from defaults

- The proto source directory comes from the profile: `local-build` (active by default) reads
  `../proto`, `docker-build` reads `./proto`. Build from the module directory and it just works.

## Known pitfalls

- With Testcontainers `ImageFromDockerfile`, enumerate every path the Dockerfile copies, as
  `FormatPostfilterDiscardSmokeIT` does. Pointing it at the repo root tars several GB of local
  models and virtualenvs — `.dockerignore` is not honoured there — and the build hangs for ten
  minutes behind a single frozen log line.

<!-- /bmad:context -->

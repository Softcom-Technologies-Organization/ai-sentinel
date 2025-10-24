# CI/CD Strategy - AI Sentinel

## Overview

This project uses a two-phase CI/CD strategy to ensure code quality before publishing any Docker images.

## Workflow Architecture

### 1. CI Workflow - Tests (`ci.yml`)

**Triggers:**
- Pull Requests to `main` or `develop`
- Push to `main` or `develop` branches

**Jobs executed:**
- `test-detector`: Unit tests for PII Detector service (Python/pytest)
- `test-api`: Tests for Reporting API (Java/Maven)
- `test-ui`: Tests for UI frontend (Angular/Karma) - *Currently commented out*
- `tests-status`: Summary job that fails if at least one test fails

**Result:** This workflow MUST succeed before the publication workflow can execute.

### 2. Publication Workflow (`docker-publish.yml`)

**Triggers:**
- Automatically after successful CI workflow (via `workflow_run`)
- Manually via `workflow_dispatch`
- On GitHub releases

**Protection:**
The `check-tests` job verifies that the CI workflow succeeded before allowing publication. If tests fail, **no Docker images will be published**.

**Jobs executed (if tests pass):**
- `build-and-publish-detector`: Build and publish PII Detector Docker image
- `build-and-publish-api`: Build and publish API Docker image
- `build-and-publish-ui`: Build and publish frontend Docker image

## Workflow

```
┌─────────────────┐
│  Push/PR to     │
│  main/develop   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│      CI Workflow - Tests            │
│  ┌──────────────────────────────┐   │
│  │  test-detector (Python)      │   │
│  │  test-api (Java)             │   │
│  │  test-ui (Angular)           │   │
│  │  tests-status (Summary)      │   │
│  └──────────────────────────────┘   │
└────────┬────────────────────────────┘
         │
    Tests OK? ─────► NO ──► ❌ Stop (No publication)
         │
        YES
         │
         ▼
┌─────────────────────────────────────┐
│  Publication Workflow - Docker      │
│  ┌──────────────────────────────┐   │
│  │  check-tests (Verification)  │   │
│  │  build-and-publish-detector  │   │
│  │  build-and-publish-api       │   │
│  │  build-and-publish-ui        │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
         │
         ▼
    ✅ Docker images published to GHCR
```

## Branch Protection Rules Configuration

To ensure code quality on main branches, configure Branch Protection Rules in GitHub:

1. **Access settings:**
   - Repository → Settings → Branches

2. **Configure rules for `main` and `develop`:**
   - ✅ Require a pull request before merging
   - ✅ Require status checks to pass before merging
   - ✅ Require branches to be up to date before merging
   - Select required checks:
     - `Test PII Detector Service`
     - `Test Reporting API`
     - `Tests Status Check`
     
   **Note:** `Test Reporting UI` is currently commented out and will be added when frontend tests are implemented.

## Benefits of this Architecture

1. **Security**: Impossible to publish Docker images if tests fail
2. **Fast feedback**: Developers see immediately if their tests pass
3. **Separation of concerns**: CI (tests) and CD (publication) are distinct
4. **Traceability**: Clear history of test executions and publications
5. **Flexibility**: Ability to manually trigger publication via `workflow_dispatch`

## Local Commands

### PII Detector Tests (Python)
```bash
cd pii-detector-service
pip install -e ".[test]"
python pii_detector/proto/generate_pb.py
pytest tests/unit/ -v
```

### Reporting API Tests (Java)
```bash
cd pii-reporting-api
mvn clean test
```

### Reporting UI Tests (Angular)
```bash
cd pii-reporting-ui
npm ci
npm run test:ci
```

**Note:** Frontend tests are currently not implemented. The `test-ui` job is commented out in the CI workflow.

## Troubleshooting

### Publication workflow doesn't trigger
- Verify that the CI workflow succeeded
- Check the GitHub "Actions" tab for logs

### Tests fail locally but not in CI
- Verify you're using the same dependency versions
- Clean caches (`pip cache purge`, `mvn clean`, `npm ci`)

### Force manual publication
Use `workflow_dispatch` in the Actions tab to manually trigger publication (bypasses CI check).

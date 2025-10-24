# CI/CD Strategy - AI Sentinel

## Overview

This project uses a **single unified workflow** that runs tests in parallel and publishes Docker images only after all tests pass.

## Workflow Architecture

### Unified CI/CD Workflow (`ci-cd.yml`)

**Triggers:**
- Pull Requests to `main` or `develop`
- Push to `main` or `develop` branches
- GitHub releases
- Manual trigger via `workflow_dispatch`

**Job Flow:**

```
┌─────────────────────────────────────────────────────────┐
│                    CI/CD Workflow                       │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Phase 1: Tests (Parallel)                      │  │
│  │  ┌────────────────┐  ┌────────────────┐         │  │
│  │  │ test-detector  │  │  test-api      │         │  │
│  │  │   (Python)     │  │  (Java/Maven)  │         │  │
│  │  └────────┬───────┘  └────────┬───────┘         │  │
│  │           └──────────┬─────────┘                 │  │
│  │                      ▼                           │  │
│  │            ┌──────────────────┐                  │  │
│  │            │  tests-status    │                  │  │
│  │            │  (Gate)          │                  │  │
│  │            └────────┬─────────┘                  │  │
│  └─────────────────────┼──────────────────────────┘  │
│                        │                             │
│           Tests OK? ───┴──► NO ──► ❌ Stop           │
│                        │                             │
│                       YES                            │
│                        │                             │
│  ┌─────────────────────┼──────────────────────────┐  │
│  │  Phase 2: Publish (Parallel, only on push)    │  │
│  │                     ▼                           │  │
│  │  ┌──────────────────────────────────────────┐  │  │
│  │  │  build-and-publish-detector              │  │  │
│  │  │  build-and-publish-api                   │  │  │
│  │  │  build-and-publish-ui                    │  │  │
│  │  └──────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────┘  │
│                        │                             │
│                        ▼                             │
│            ✅ Docker images published                │
└─────────────────────────────────────────────────────┘
```

## Key Features

### 1. Parallel Test Execution
Tests for detector and API run **simultaneously** to reduce total execution time.

### 2. Smart Publication
- **Pull Requests**: Tests run, but Docker images are NOT published
- **Push to develop/main**: Tests run, then Docker images are published
- **Releases**: Full workflow with publication
- **Manual trigger**: You can choose which images to build

### 3. Test Gate
The `tests-status` job acts as a gate:
- ✅ If all tests pass → Publication jobs start
- ❌ If any test fails → Workflow stops, no publication

## Branch Protection Rules Configuration

Configure Branch Protection Rules in GitHub to enforce testing:

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

## Benefits

1. **Simplicity**: One workflow to maintain instead of two
2. **Fast feedback**: Parallel test execution
3. **Security**: Impossible to publish Docker images if tests fail
4. **Clarity**: Easy to understand flow from tests to publication
5. **Efficiency**: No need for complex `workflow_run` triggers

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

**Note:** Frontend tests are currently not implemented. Add them to the workflow when ready.

## Manual Publication

To manually trigger the workflow and choose what to build:

1. Go to **Actions** → **CI/CD - Tests and Publish**
2. Click **Run workflow**
3. Select branch
4. Choose which modules to build (detector, API, UI)
5. Optionally specify a custom tag
6. Click **Run workflow**

## Troubleshooting

### Tests fail but I can't see why
- Check the specific test job logs in the GitHub Actions UI
- Run tests locally with the commands above

### Publication doesn't happen on push
- Verify you're pushing to `develop` or `main`
- Check that all tests passed
- Ensure you have write permissions to GitHub Container Registry

### Want to skip publication temporarily
Create a PR instead of pushing directly - this runs tests but skips publication.

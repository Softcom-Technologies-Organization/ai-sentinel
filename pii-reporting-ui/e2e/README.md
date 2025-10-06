# E2E Testing with Playwright

This directory contains end-to-end tests for the PII Reporting UI using Playwright.

## Overview

Playwright is a modern, cross-browser automation framework that provides reliable end-to-end testing capabilities for web applications. This setup follows current best practices for Angular 20+ applications.

## Prerequisites

- Node.js 20.19+ or 22.12+
- npm

## Installation

Playwright is already configured in this project. If you need to reinstall browsers:

```bash
npx playwright install
```

## Running Tests

### Headless Mode (CI/CD)
```bash
npm run e2e
```

### Headed Mode (with browser window)
```bash
npm run e2e:headed
```

### Interactive UI Mode (recommended for development)
```bash
npm run e2e:ui
```

### View Test Report
```bash
npm run e2e:report
```

## Configuration

The Playwright configuration is defined in `playwright.config.ts` at the project root. Key settings include:

- **Test Directory**: `./e2e`
- **Base URL**: `http://localhost:4200`
- **Browsers**: Chromium, Firefox, WebKit
- **Timeout**: 30 seconds per test
- **Retries**: 1 (2 in CI)
- **Auto Server**: Automatically starts Angular dev server before tests

## Writing Tests

### Test Structure

Tests follow modern Playwright conventions:

```typescript
import { test, expect } from '@playwright/test';

test.describe('Feature Name', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should verify expected behavior', async ({ page }) => {
    // Test implementation
  });
});
```

### Best Practices

1. **Use Stable Selectors**: Prefer `data-testid` attributes or semantic HTML elements
2. **Leverage Auto-waiting**: Playwright automatically waits for elements and async operations
3. **Keep Tests Independent**: Each test should be able to run in isolation
4. **Use Page Object Model**: For complex scenarios, consider creating page objects
5. **Meaningful Test Names**: Use descriptive names that explain what is being tested

## Test Coverage

Current test suites:

- **dashboard.spec.ts**: Verifies the Spaces Dashboard datatable functionality
  - Datatable displays data
  - Table headers are correct
  - Space names are visible
  - PII badges are displayed

## Debugging Tests

### Debug Mode
```bash
npx playwright test --debug
```

### Trace Viewer
Traces are automatically captured on first retry. View them with:
```bash
npx playwright show-trace test-results/path-to-trace.zip
```

## CI/CD Integration

The configuration automatically detects CI environments and adjusts settings:
- Runs with 1 worker in CI (sequential execution)
- 2 retries on failure
- Does not reuse existing server

## Resources

- [Playwright Documentation](https://playwright.dev/)
- [Playwright with Angular Guide](https://angular.love/modern-e2e-testing-for-angular-apps-with-playwright)
- [Best Practices](https://playwright.dev/docs/best-practices)

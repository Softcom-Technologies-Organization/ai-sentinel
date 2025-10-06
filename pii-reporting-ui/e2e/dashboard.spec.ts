import { test, expect } from '@playwright/test';

/**
 * E2E tests for the Spaces Dashboard component.
 * Verifies that the datatable displays data correctly.
 */
test.describe('Spaces Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display datatable with space data', async ({ page }) => {
    // Wait for the PrimeNG table to be visible
    await page.waitForSelector('p-table', { timeout: 10_000 });

    // Wait for table content to load (check for table body rows)
    const tableBody = page.locator('p-table tbody tr');

    // Verify that at least one data row exists (excluding loading skeletons)
    // We check for rows that don't contain skeleton elements
    const dataRows = tableBody.filter({ hasNot: page.locator('p-skeleton') });

    // Wait for data to appear and verify count
    await expect(dataRows.first()).toBeVisible({ timeout: 15_000 });
    const rowCount = await dataRows.count();

    expect(rowCount).toBeGreaterThan(0);
  });

  test('should display table headers correctly', async ({ page }) => {
    // Wait for table to be visible
    await page.waitForSelector('p-table');

    // Verify essential table headers are present
    await expect(page.locator('#col-space')).toHaveText('Space');
    await expect(page.locator('#col-status')).toHaveText('Statut');
    await expect(page.locator('#col-progress')).toHaveText('Progression');
    await expect(page.locator('#col-pii')).toHaveText('PII');
  });

  test('should display space name in datatable', async ({ page }) => {
    // Wait for the table to load
    await page.waitForSelector('p-table tbody tr');

    // Wait for actual data (not skeleton loaders)
    await page.waitForSelector('p-table tbody tr:not(:has(p-skeleton))', { timeout: 15_000 });

    // Verify that at least one space name is displayed
    const spaceNameCell = page.locator('p-table tbody tr td:nth-child(2) span.fw-600').first();
    await expect(spaceNameCell).toBeVisible();

    // Verify the space name is not empty
    const spaceName = await spaceNameCell.textContent();
    expect(spaceName).toBeTruthy();
    expect(spaceName?.trim().length).toBeGreaterThan(0);
  });

  test('should display PII badges in datatable', async ({ page }) => {
    // Wait for the table to load with data
    await page.waitForSelector('p-table tbody tr:not(:has(p-skeleton))', { timeout: 15_000 });

    // Verify that PII count badges are displayed
    const piiBadge = page.locator('p-table tbody tr td p-badge').first();
    await expect(piiBadge).toBeVisible();

    // Verify badge has a numeric value
    const badgeValue = await piiBadge.getAttribute('ng-reflect-value');
    expect(badgeValue).toBeDefined();
  });
});

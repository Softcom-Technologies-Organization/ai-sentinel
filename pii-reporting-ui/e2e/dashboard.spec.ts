import { expect, test } from '@playwright/test';
import { TestIds } from '../src/app/features/test-ids.constants';

/**
 * E2E tests for the Spaces Dashboard component.
 * Verifies that the datatable displays data correctly using data-testid attributes.
 */
test.describe('Spaces Dashboard', () => {
  const testIds = TestIds.dashboard;
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display datatable with space data', async ({ page }) => {
    // Wait for the table to be visible
    const table = page.getByTestId(testIds.table);
    await expect(table).toBeVisible({ timeout: 10_000 });

    // Wait for data rows to appear (excluding loading skeletons)
    const dataRows = page.getByTestId(testIds.spaceRow);
    await expect(dataRows.first()).toBeVisible({ timeout: 15_000 });

    // Verify at least one data row exists
    const rowCount = await dataRows.count();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('should display table headers correctly', async ({ page }) => {
    // Wait for table to be visible
    const table = page.getByTestId(testIds.table);
    await expect(table).toBeVisible();

    // Verify essential table headers are present
    await expect(page.getByTestId(testIds.headers.space)).toHaveText('Space');
    await expect(page.getByTestId(testIds.headers.status)).toHaveText('Statut');
    await expect(page.getByTestId(testIds.headers.progress)).toHaveText('Progression');
    await expect(page.getByTestId(testIds.headers.pii)).toHaveText('PII');
  });

  test('should display space name in datatable', async ({ page }) => {
    // Wait for data rows to load
    const dataRows = page.getByTestId(testIds.spaceRow);
    await expect(dataRows.first()).toBeVisible({ timeout: 15_000 });

    // Verify that at least one space name is displayed
    const spaceNameCell = page.getByTestId(testIds.spaceName).first();
    await expect(spaceNameCell).toBeVisible();

    // Verify the space name is not empty
    const spaceName = await spaceNameCell.textContent();
    expect(spaceName).toBeTruthy();
    expect(spaceName?.trim().length).toBeGreaterThan(0);
  });

  test('should display PII badges in datatable', async ({ page }) => {
    // Wait for data rows to load
    const dataRows = page.getByTestId(testIds.spaceRow);
    await expect(dataRows.first()).toBeVisible({ timeout: 15_000 });

    // Verify that PII count badges are displayed
    const totalBadge = page.getByTestId(testIds.badges.total).first();
    await expect(totalBadge).toBeVisible();

    // Verify badge has a numeric value
    const badgeValue = await totalBadge.getAttribute('ng-reflect-value');
    expect(badgeValue).toBeDefined();
  });
});

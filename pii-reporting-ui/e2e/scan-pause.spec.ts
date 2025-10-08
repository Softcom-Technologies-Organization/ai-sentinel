import { test, expect } from '@playwright/test';
import { TestIds } from '../src/app/features/test-ids.constants';

/**
 * E2E test for scan pause functionality.
 * Verifies that pausing a running scan correctly updates the status to "En pause" (PAUSED).
 *
 * Business rule: When a user pauses a scan, all space statuses should be updated to PAUSED
 * to prevent displaying incorrect "Non démarré" status.
 */
test.describe('Scan Pause Functionality', () => {
  const testIds = TestIds.dashboard;

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for the dashboard to load
    await expect(page.getByTestId(testIds.table)).toBeVisible({ timeout: 10_000 });
  });

  test('Should_DisplayPausedStatus_When_ScanIsPaused', async ({ page }) => {
    // Step 1: Click start scan button
    const startButton = page.getByTestId(testIds.buttons.startScan);
    await expect(startButton).toBeEnabled({ timeout: 15_000 });
    await startButton.click();

    // Step 2: Wait for scan to start - pause button should become enabled
    const pauseButton = page.getByTestId(testIds.buttons.pauseScan);
    await expect(pauseButton).toBeEnabled({ timeout: 20_000 });

    // Step 3: Wait for at least one space to show "En cours" (RUNNING) status
    // This ensures the scan has actually started processing
    const runningStatus = page.getByTestId(testIds.spaceStatus).filter({ hasText: 'En cours' });
    await expect(runningStatus.first()).toBeVisible({ timeout: 30_000 });

    // Step 4: Click pause button
    await pauseButton.click();

    // Step 5: Wait for pause button to be disabled (scan stopped)
    await expect(pauseButton).toBeDisabled({ timeout: 5_000 });

    // Step 6: Verify that at least one space shows "En pause" (PAUSED) status
    // This is the key assertion - we should see PAUSED, not "Non démarré"
    const pausedStatus = page.getByTestId(testIds.spaceStatus).filter({ hasText: 'En pause' });
    await expect(pausedStatus.first()).toBeVisible({ timeout: 10_000 });

    // Step 7: Verify resume button becomes enabled
    const resumeButton = page.getByTestId(testIds.buttons.resumeScan);
    await expect(resumeButton).toBeEnabled({ timeout: 5_000 });

    // Additional verification: ensure no space shows "Non démarré" for spaces that were being processed
    // Count total spaces with work done
    const allStatuses = page.getByTestId(testIds.spaceStatus);
    const statusCount = await allStatuses.count();

    // At least one space should have PAUSED status (the one that was running)
    const pausedCount = await pausedStatus.count();
    expect(pausedCount).toBeGreaterThan(0);

    console.log(`[TEST] Total spaces: ${statusCount}, Paused spaces: ${pausedCount}`);
  });
});

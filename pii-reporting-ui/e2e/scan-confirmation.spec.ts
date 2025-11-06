import {expect, test} from '@playwright/test';
import {TestIds} from '../src/app/features/test-ids.constants';
import {DIALOG_SELECTORS} from './helpers/dialog-selectors';

/**
 * E2E tests for scan start confirmation dialog.
 * Verifies that the user must confirm before starting a global scan.
 */
test.describe('Scan Confirmation Dialog', () => {
  const testIds = TestIds.dashboard;
  const dialogSelectors = DIALOG_SELECTORS.confirmDialog;

  test.beforeEach(async ({page}) => {
    await page.goto('/');
    // Wait for dashboard to load
    const table = page.getByTestId(testIds.table);
    await expect(table).toBeVisible({timeout: 10_000});
  });

  test('should show confirmation dialog when user clicks start scan button', async ({page}) => {
    // Get buttons by test IDs
    const startButton = page.getByTestId(testIds.buttons.startScan);
    await expect(startButton).toBeVisible();
    await expect(startButton).toBeEnabled();

    // Trigger confirmation dialog
    await startButton.click();

    // Verify confirmation dialog appears
    const confirmDialog = page.locator(dialogSelectors.container);
    await expect(confirmDialog).toBeVisible({timeout: 5000});

    // Verify dialog content is present
    const dialogHeader = confirmDialog.locator(dialogSelectors.header);
    await expect(dialogHeader).toBeVisible();

    const dialogMessage = confirmDialog.locator(dialogSelectors.message);
    await expect(dialogMessage).toBeVisible();

    // Verify both action buttons are present
    const acceptButton = confirmDialog.locator(dialogSelectors.acceptButton);
    const rejectButton = confirmDialog.locator(dialogSelectors.rejectButton);
    await expect(acceptButton).toBeVisible();
    await expect(rejectButton).toBeVisible();

    // Close dialog to avoid interfering with other tests
    await rejectButton.click();
    await expect(confirmDialog).not.toBeVisible();
  });

  test('should keep buttons state unchanged when user rejects confirmation', async ({page}) => {
    // Get all action buttons by test IDs
    const startButton = page.getByTestId(testIds.buttons.startScan);
    const pauseButton = page.getByTestId(testIds.buttons.pauseScan);
    const resumeButton = page.getByTestId(testIds.buttons.resumeScan);

    // Capture initial button states
    const initialStartEnabled = await startButton.isEnabled();
    const initialPauseEnabled = await pauseButton.isEnabled();
    const initialResumeEnabled = await resumeButton.isEnabled();

    // Trigger confirmation dialog
    await startButton.click();

    // Wait for confirmation dialog
    const confirmDialog = page.locator(dialogSelectors.container);
    await expect(confirmDialog).toBeVisible({timeout: 5000});

    // Reject the confirmation
    const rejectButton = confirmDialog.locator(dialogSelectors.rejectButton);
    await rejectButton.click();

    // Verify dialog closes
    await expect(confirmDialog).not.toBeVisible();

    // Verify all button states remain unchanged
    const finalStartEnabled = await startButton.isEnabled();
    const finalPauseEnabled = await pauseButton.isEnabled();
    const finalResumeEnabled = await resumeButton.isEnabled();

    expect(finalStartEnabled).toBe(initialStartEnabled);
    expect(finalPauseEnabled).toBe(initialPauseEnabled);
    expect(finalResumeEnabled).toBe(initialResumeEnabled);

    // Verify start and resume buttons are still enabled (ready to start a scan)
    await expect(startButton).toBeEnabled();
    // Resume button state depends on whether there's a previous scan, so we just verify it's consistent
  });

  test('should start scan and update button states when user accepts confirmation', async ({page}) => {
    // Get all action buttons by test IDs
    const startButton = page.getByTestId(testIds.buttons.startScan);
    const pauseButton = page.getByTestId(testIds.buttons.pauseScan);

    // Trigger confirmation dialog
    await startButton.click();

    // Wait for confirmation dialog
    const confirmDialog = page.locator(dialogSelectors.container);
    await expect(confirmDialog).toBeVisible({timeout: 5000});

    // Accept the confirmation
    const acceptButton = confirmDialog.locator(dialogSelectors.acceptButton);
    await acceptButton.click();

    // Verify dialog closes
    await expect(confirmDialog).not.toBeVisible({timeout: 5000});

    // Verify scan has started: pause button should become enabled
    await expect(pauseButton).toBeEnabled({timeout: 10_000});
  });
});

import { expect, Page, test } from '@playwright/test';
import { TestIds } from '../src/app/features/test-ids.constants';

/**
 * E2E coverage for the PII removal feature (AC13).
 *
 * Both scenarios need a seeded stack with the remediation feature flag on and at least
 * one scanned space carrying PENDING findings (and, for the attachment case, a finding
 * living in an attachment). They drive the real UI through the shared data-testids.
 */
test.describe('PII obfuscation', () => {
  const dashboardIds = TestIds.dashboard;
  const ids = TestIds.obfuscation;

  async function firstScannedSpaceKey(page: Page): Promise<string> {
    await page.goto('/');
    const spaceName = page.getByTestId(dashboardIds.spaceName).first();
    await expect(spaceName).toBeVisible({ timeout: 15_000 });
    const key = (await spaceName.textContent())?.trim();
    expect(key).toBeTruthy();
    return key as string;
  }

  async function openSpaceObfuscation(page: Page, spaceKey: string, preselect = true): Promise<void> {
    const params = new URLSearchParams({ spaceKey });
    if (preselect) {
      params.set('preselect', 'true');
    }
    await page.goto(`/obfuscation?${params.toString()}`);
    await expect(page.getByTestId(ids.content)).toBeVisible({ timeout: 15_000 });
  }

  test('nominal path: select a group and submit a redaction job', async ({ page }) => {
    const spaceKey = await firstScannedSpaceKey(page);
    await openSpaceObfuscation(page, spaceKey, true);

    // Preselection is advertised in the entry banner and reflected in the bulk bar counter.
    await expect(page.getByTestId(ids.stats)).toBeVisible();
    const bulkBar = page.getByTestId(ids.bulkBar.root);
    await expect(bulkBar).toBeVisible({ timeout: 15_000 });

    // Trigger the confirmation dialog (backend-computed plan) and submit the job.
    const obfuscateButton = page.getByTestId(ids.bulkBar.obfuscate);
    await expect(obfuscateButton).toBeEnabled();
    await obfuscateButton.click();

    const accept = page.getByTestId(ids.confirmDialog.accept);
    await expect(page.getByTestId(ids.confirmDialog.lead)).toBeVisible();
    await expect(accept).toBeVisible();
    await accept.click();

    // The job progress panel replaces the bulk bar while the run streams outcomes.
    await expect(page.getByTestId(ids.jobProgress.root)).toBeVisible({ timeout: 15_000 });
  });

  test('attachment path: attachment findings are not redactable', async ({ page }) => {
    const spaceKey = await firstScannedSpaceKey(page);
    await openSpaceObfuscation(page, spaceKey, false);

    // Show every finding and expand the groups so attachment rows are rendered.
    await page.getByTestId(`${ids.toolbar.statusOption}-ALL`).click();
    await page.getByTestId(ids.toolbar.expandAll).click();

    const ineligible = page.getByTestId(ids.row.ineligible).first();
    await expect(ineligible).toBeVisible({ timeout: 15_000 });
    await expect(ineligible).toContainText('Pièce jointe : suppression impossible');

    // The row's checkbox must be disabled: an attachment finding cannot be selected.
    const attachmentRow = page
      .getByTestId(ids.row.root)
      .filter({ has: page.getByTestId(ids.row.ineligible) })
      .first();
    await expect(attachmentRow.getByTestId(ids.row.checkbox)).toBeDisabled();
  });
});

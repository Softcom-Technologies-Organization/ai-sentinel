import { expect, test } from '@playwright/test';
import { TestIds } from '../src/app/features/test-ids.constants';

/**
 * E2E test pour vérifier le workflow complet de scan et d'affichage des résultats.
 *
 * Ce test valide que :
 * 1. La page principale charge correctement les données
 * 2. Un scan peut être lancé avec succès
 * 3. Les résultats du scan sont affichés dans la datatable
 * 4. L'expansion d'une ligne avec des PII affiche les PiiItemCardComponents correspondants
 */
test.describe('Scan et expansion des résultats PII', () => {
  const testIds = TestIds.dashboard;

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('Should_DisplayPiiItems_When_ExpandingFirstRowAfterScan', async ({ page }) => {
    // Étape 1 : Attendre le chargement initial de la datatable
    const table = page.getByTestId(testIds.table);
    await expect(table).toBeVisible({ timeout: 10_000 });

    // Attendre que les données initiales soient chargées
    const dataRows = page.getByTestId(testIds.spaceRow);
    await expect(dataRows.first()).toBeVisible({ timeout: 15_000 });

    console.log('[TEST] Page principale chargée, données initiales disponibles');

    // Étape 2 : Lancer le scan
    const startButton = page.getByTestId(testIds.buttons.startScan);
    await expect(startButton).toBeEnabled({ timeout: 30_000 });
    await startButton.click();

    console.log('[TEST] Scan démarré');

    // Étape 3 : Attendre que le scan commence et que des données arrivent
    // Le scan est en cours lorsque le bouton pause devient actif
    const pauseButton = page.getByTestId(testIds.buttons.pauseScan);
    await expect(pauseButton).toBeEnabled({ timeout: 20_000 });

    console.log('[TEST] Scan en cours, attente des résultats...');

    // Attendre que des PII soient détectés (badge total > 0)
    // On attend que la première ligne ait au moins 1 PII détecté
    const firstRowBadge = dataRows.first().locator(`[data-testid="${testIds.badges.total}"]`);
    await expect(firstRowBadge).toBeVisible({ timeout: 60_000 });

    // Vérifier que le badge affiche une valeur > 0
    // On utilise un wait with condition pour s'assurer que la valeur est bien > 0
    await page.waitForFunction(
      (badgeSelector) => {
        const badge = document.querySelector(`[data-testid="${badgeSelector}"]`);
        if (!badge) return false;
        const value = badge.getAttribute('ng-reflect-value') || badge.textContent;
        return value && parseInt(value) > 0;
      },
      testIds.badges.total,
      { timeout: 60_000 }
    );

    console.log('[TEST] Résultats PII détectés dans la première ligne');

    // Étape 4 : Cliquer sur le bouton expand de la première ligne
    const firstExpandButton = dataRows.first().locator(`[data-testid="${testIds.expandButton}"]`);
    await expect(firstExpandButton).toBeVisible();
    await firstExpandButton.click();

    // Attendre que l'animation d'expansion soit terminée et que le contenu soit rendu
    // Augmenter le délai pour Firefox qui semble plus lent
    await page.waitForTimeout(2000); // Attendre la fin de l'animation PrimeNG

    console.log('[TEST] Ligne expansée, vérification des PiiItemCard...');

    // Étape 5 : Vérifier qu'au moins un PiiItemCardComponent est affiché
    // On attend d'abord que la section expansée soit visible
    const expandedSection = page.locator('.row-expansion');
    await expect(expandedSection).toBeVisible({ timeout: 10_000 });

    // Attendre que le p-dataview soit présent dans la section expansée
    const dataView = expandedSection.locator('p-dataview');
    await expect(dataView).toBeVisible({ timeout: 10_000 });

    // Attendre explicitement que les données soient chargées dans itemsBySpace
    // On vérifie que le contenu n'est pas "Aucun élément à afficher"
    await expect(expandedSection.locator('.text-muted:has-text("Aucun élément à afficher")')).not.toBeVisible({ timeout: 5_000 });

    console.log('[TEST] DataView visible, recherche des cartes PII...');

    // Puis on attend que les cartes soient chargées et visibles
    // Utiliser un délai plus long pour Firefox
    const piiItemCards = page.getByTestId(TestIds.piiItemCard.card);
    await expect(piiItemCards.first()).toBeVisible({ timeout: 20_000 });

    // Vérifier qu'il y a au moins un élément
    const cardCount = await piiItemCards.count();
    expect(cardCount).toBeGreaterThan(0);

    console.log(`[TEST] Succès ! ${cardCount} PiiItemCard(s) affichée(s) dans la section expansée`);

    // Vérification supplémentaire : s'assurer que la carte contient du contenu
    const firstCard = piiItemCards.first();
    await expect(firstCard).toContainText(/Sévérité/); // Chaque carte doit afficher la sévérité

    console.log('[TEST] Contenu de la carte validé');
  });

});

# Backlog SonarQube — ai-sentinel

> Genere le 2026-08-31 06:04 UTC par `qa/sonar/refresh-backlog.py` depuis http://localhost:9000.
> Perimetre : issues **OPEN / CONFIRMED** des 3 modules, branche d'analyse `main`.
>
> ⚠️ L'analyse publiee sous `main` decrit en realite la branche `chore/improve-error-handling`.
> Les numeros de ligne ne sont valides que sur cette branche, et se decalent des le premier commit.

**Total : 25 issues** — 0 security hotspot.

Protocole d'execution obligatoire : [RUNBOOK.md](RUNBOOK.md).

## Avancement par vague

| Vague | Perimetre | Issues | Statut |
|-------|-----------|--------|--------|
| **A** | Tests — mecanique pure | 0 | ⬜ a faire |
| **B** | Production — mecanique locale | 1 | ✅ terminee et verifiee |
| **C** | Accessibilite UI — modifie le DOM rendu | 13 | 🔄 en cours |
| **D** | Jugement requis — lire le RUNBOOK avant | 11 | ⬜ a faire |

Statuts : ⬜ a faire · 🔄 en cours · ✅ terminee et verifiee · ⛔ annulee (rollback).

Legende des cases : `[ ]` a traiter · `[x]` corrigee et verifiee · `[!]` escaladee, voir la section Escalades du RUNBOOK.

---

## Vague A — Tests — mecanique pure (0 issues)

Aucun fichier de production touche. Une regression ici ne peut venir que du test lui-meme.

---

## Vague B — Production — mecanique locale (1 issues)

Modifie du code de production, mais chaque correction est locale et sans effet de bord fonctionnel.

### Lot `java:S6213` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Renommer l'identifiant qui utilise un mot reserve restreint (`var`, `record`, `yield`, `sealed`).

- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/DiscoveredLabelCollector.java:39` — Rename this method to not match a restricted identifier. <!-- 5f3d6c9b -->

---

## Vague C — Accessibilite UI — modifie le DOM rendu (13 issues)

Change le HTML produit. Peut casser des selecteurs de test ou un snapshot : vague isolee, verifiee a part.

### Lot `Web:MouseEventWithoutKeyboardEquivalentCheck` — 6 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Ajouter le gestionnaire clavier correspondant (`(keyup.enter)` / `(keydown.space)`) a cote du `(click)`. Ne pas retirer le gestionnaire souris.

**Escalade du 2026-08-31** : les 6 elements sont des `<p-button>`. PrimeNG 21 rend un `<button>`
natif (`primeng/fesm2022/primeng-button.mjs:816`, `(click)="onClick.emit($event)"`), pour lequel le
navigateur emet deja un `click` sur Enter et sur Espace. Ajouter un gestionnaire clavier a cote du
`(click)` declencherait l'action deux fois a chaque activation au clavier : `nextPage()` sauterait
une page, `toggleSortOrder()` reviendrait a son etat initial. La correction prescrite casserait
l'usage clavier, c'est-a-dire ce que la regle protege. Faux positif a arbitrer cote SonarQube — le
RUNBOOK interdit a l'agent de changer le statut d'une issue sur le serveur.

- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:109` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 5bae9c35 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:119` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- eaec42f0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:127` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 481603b4 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:115` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 1f404321 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:125` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 87623884 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:142` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- ecd7c5b7 -->

### Lot `Web:S6819` — 6 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer le `role=` ARIA par la balise HTML native equivalente : `role="region"` -> `<section>`, `role="status"` -> `<output>`. Verifier qu'aucun test ne cible le role via un selecteur.

**Escalade du 2026-08-31, deuxieme rencontre.** Ce lot a deja ete tente et annule la nuit
precedente (`53b217ae`) : `check ui` avait fait echouer
`ObfuscationJobProgressComponent.Should_RenderBackendProgressVerbatim_When_JobRunning` et
`PiiObfuscationComponent.Should_PreselectAllSeveritiesAndShowBanner_When_PreselectParamTrue`.
Etat verifie ce soir, identique a celui de la mesure : les 4 attributs `role` sont toujours en place
(`obfuscation-job-progress.component.html:2`, `obfuscation-bulk-bar.component.html:5`,
`pii-obfuscation.component.html:50` et `:387`) et les deux assertions qui les lisent aussi
(`obfuscation-job-progress.component.spec.ts:79` et `pii-obfuscation.component.spec.ts:347`, toutes
deux `expect(...getAttribute('role')).toBe('status')`). La suite n'a pas ete relancee ce soir : le
resultat serait le meme, la conversion en `<output>` supprimant l'attribut que ces tests lisent.

Le lot est bloque par deux decisions qui n'appartiennent pas a l'agent :

1. **4 issues convertibles** (`role="region"` -> `<section>`, `role="status"` -> `<output>`) : la
   conversion est correcte, mais elle demande de reecrire deux assertions pour qu'elles verifient le
   nom de balise au lieu du role. Le RUNBOOK interdit de toucher a un test pour faire passer un lot.
   A arbitrer : ces deux assertions sont-elles a mettre a jour ?
2. **2 issues non convertibles** (`role="group"` sur `<span class="ob-segmented">`, lignes 169 et
   205) : aucune des balises proposees par Sonar ne convient a un controle segmente. `<fieldset>`
   apporte des marges et un `min-inline-size` a neutraliser ; `<details>`, `<address>` et
   `<optgroup>` sont semantiquement faux. Faux positif a marquer cote SonarQube.

- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-bulk-bar/obfuscation-bulk-bar.component.html:3` — Use <section> instead of the region role to ensure accessibility across all devices. <!-- cccefd2a -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-job-progress/obfuscation-job-progress.component.html:2` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 6866961e -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:50` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- a37f9219 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:169` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- 7b8955b0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:205` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- a1bfc9a0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:387` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 2bbebd52 -->

### Lot `Web:ItemTagNotWithinContainerTagCheck` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remettre le `<li>` dans un conteneur `<ul>` / `<ol>`.

- [ ] `pii-reporting-ui/src/app/features/confluence-dashboard/components/space-scan-stats-popover/space-scan-stats-popover.component.html:38` — Surround this <dt> item tag by a <dl> container one. <!-- 2bbd57b7 -->

---

## Vague D — Jugement requis — lire le RUNBOOK avant (11 issues)

Changement de signature, de semantique ou de structure. Chaque lot a une garde explicite.

### Lot `java:S8688` — 7 issue(s)

ℹ️ INFO · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : GARDE : ne pas injecter de `Clock` a la volee. Utiliser la zone deja retenue par le projet et rester coherent avec le code voisin. Si le fichier n'a pas de convention, escalader plutot que d'en inventer une.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/ScanCheckpointService.java:253` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- ead19c4a -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/mapper/ConfluencePageMapper.java:109` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- b6380c6b -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/mapper/ConfluencePageMapper.java:114` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- d31061be -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:78` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 016878d7 -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:79` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 83dcbd7d -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:87` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 99a87663 -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapter.java:39` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 60b5b4ce -->

### Lot `python:S5332` — 2 issue(s)

⬜ MINOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : GARDE : `http://` vise ici une instance LM Studio locale. Passer en `https://` casserait le detector. Documenter l'usage local et marquer l'issue en attente d'arbitrage, ne pas modifier l'URL.

- [ ] `pii-detector-service/pii_detector/infrastructure/detector/ministral_detector.py:483` — Using HTTP protocol is insecure. Use HTTPS instead. <!-- 9ce0cc94 -->
- [ ] `pii-detector-service/pii_detector/infrastructure/detector/ministral_detector.py:483` — Using HTTP protocol is insecure. Use HTTPS instead. <!-- f664922a -->

### Lot `java:S1192` — 1 issue(s)

🟧 CRITICAL · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Voir la description de la regle dans SonarQube.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/usecase/AbstractStreamConfluenceScanUseCase.java:251` — Define a constant instead of duplicating this literal "cause" 3 times. <!-- 198a55c8 -->

### Lot `java:S107` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : GARDE : 9 parametres sur une methode de repository. Regrouper en objet de criteres touche tous les appelants. Escalader si plus de 3 appelants.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/jpa/ScanDetectorStatsJpaRepository.java:46` — Method has 9 parameters, which is greater than 7 authorized. <!-- 47955961 -->


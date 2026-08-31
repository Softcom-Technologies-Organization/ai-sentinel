# Backlog SonarQube — ai-sentinel

> Genere le 2026-08-30 20:49 UTC par `qa/sonar/refresh-backlog.py` depuis http://localhost:9000.
> Perimetre : issues **OPEN / CONFIRMED** des 3 modules, branche d'analyse `main`.
>
> ⚠️ L'analyse publiee sous `main` decrit en realite la branche `chore/improve-error-handling`.
> Les numeros de ligne ne sont valides que sur cette branche, et se decalent des le premier commit.

**Total : 178 issues** — 0 security hotspot.

Protocole d'execution obligatoire : [RUNBOOK.md](RUNBOOK.md).

## Avancement par vague

| Vague | Perimetre | Issues | Statut |
|-------|-----------|--------|--------|
| **A** | Tests — mecanique pure | 87 | ✅ terminee et verifiee |
| **B** | Production — mecanique locale | 50 | ✅ terminee et verifiee — 1 escalade (`java:S6213`) |
| **C** | Accessibilite UI — modifie le DOM rendu | 16 | ⬜ a faire |
| **D** | Jugement requis — lire le RUNBOOK avant | 25 | ⬜ a faire |

Statuts : ⬜ a faire · 🔄 en cours · ✅ terminee et verifiee · ⛔ annulee (rollback).

Legende des cases : `[ ]` a traiter · `[x]` corrigee et verifiee · `[!]` escaladee, voir la section Escalades du RUNBOOK.

---

## Vague A — Tests — mecanique pure (87 issues)

Aucun fichier de production touche. Une regression ici ne peut venir que du test lui-meme.

### Lot `java:S5778` — 36 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Sortir du lambda `assertThatThrownBy` / `assertThrows` tout ce qui n'est pas l'appel qui leve. Les preparations remontent avant l'assertion. Ne jamais changer l'exception attendue.

- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/remediation/usecase/PlanObfuscationUseCaseTest.java:50` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- c76155ed -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:174` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 24e8c4c6 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:185` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 81974333 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:196` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 7b3e8965 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:207` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- b964c52e -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:218` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 182c34cd -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:243` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 3a64ea32 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:254` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- a131c800 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/detection/PiiDetectionConfigTest.java:265` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 75bfcb79 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:110` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- ea0c056b -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:118` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 95ae209a -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:126` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 82bd59c3 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:134` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- af933f47 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:142` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 6a3ae409 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:150` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 9b4d3164 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingReferenceTest.java:164` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- bcb98459 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingRemediationTest.java:75` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 659e4153 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingRemediationTest.java:83` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- ea738c5d -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingRemediationTest.java:91` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 91ed0e87 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingRemediationTest.java:99` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 519f47e0 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/FindingRemediationTest.java:107` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 2a87ea78 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:86` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 59e4a15d -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:94` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- fc7970ef -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:102` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 8d4dc074 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:110` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 6fb218cf -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:118` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- bbe11d68 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationJobTest.java:126` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 95b7e554 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationPlanTest.java:78` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- db42deb6 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/ObfuscationPlanTest.java:115` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 5dfc9175 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/RemediationSelectionTest.java:26` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 3c67ee08 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/RemediationSelectionTest.java:34` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 98641d81 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/RemediationSelectionTest.java:54` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 1b7addeb -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/RemediationSelectionTest.java:77` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- d1516d52 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/domain/pii/remediation/RemediationSelectionTest.java:85` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 1fa789fc -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/out/JpaObfuscationJobAdapterTest.java:139` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- a5a50bce -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/out/JpaObfuscationJobAdapterTest.java:153` — Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. <!-- 071150eb -->

### Lot `java:S8924` — 25 issue(s)

⬜ MINOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Remplacer `Mockito.when(...)` par `when(...)` et ajouter l'import statique. Purement syntaxique.

- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/confluence/service/ConfluenceSpaceCacheRefreshServiceTest.java:100` — Use a static import for "mock". <!-- 6c752164 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/service/ContentScanOrchestratorTest.java:110` — Use a static import for "doThrow". <!-- a9377739 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/service/ScanCheckpointServiceInterruptionTest.java:99` — Use a static import for "never". <!-- 704a36f8 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceResumeScanUseCaseTest.java:100` — Use a static import for "mock". <!-- a79b5106 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceResumeScanUseCaseTest.java:121` — Use a static import for "mock". <!-- 19c35507 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceResumeScanUseCaseTest.java:365` — Use a static import for "mock". <!-- bd43a5c1 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:115` — Use a static import for "mock". <!-- 3f3e1899 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:136` — Use a static import for "mock". <!-- c7915db8 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:669` — Use a static import for "mock". <!-- 073973ad -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:673` — Use a static import for "mock". <!-- 3e6cb8bf -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:702` — Use a static import for "mock". <!-- d840c8c6 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:742` — Use a static import for "mock". <!-- c8297d21 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:771` — Use a static import for "mock". <!-- 63a2193f -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:1198` — Use a static import for "mock". <!-- 1cdd2357 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/JpaScanEventStoreAdapterTest.java:122` — Use a static import for "times". <!-- 9c90afec -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapterTest.java:116` — Use a static import for "never". <!-- 8fe4d4b7 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapterTest.java:141` — Use a static import for "never". <!-- f98c7d9e -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapterTest.java:215` — Use a static import for "never". <!-- 0e8bdb58 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapterTest.java:252` — Use a static import for "never". <!-- 8a27ff2c -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:517` — Use a static import for "mock". <!-- a19c73f8 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:523` — Use a static import for "mock". <!-- 61ad4204 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:529` — Use a static import for "mock". <!-- 1b98f92e -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:535` — Use a static import for "mock". <!-- 851f3504 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:541` — Use a static import for "mock". <!-- 2399cb78 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/integration/ExcelExportFullScanIntegrationTest.java:547` — Use a static import for "mock". <!-- f36476d0 -->

### Lot `typescript:S5906` — 16 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer l'assertion generique par la plus specifique : `toBe(true)` -> `toBeTruthy()` selon le message Sonar, `toEqual(null)` -> `toBeNull()`. Ne pas relacher la contrainte.

> Traite : 12 occurrences corrigees (`expect(x.length).toBe(n)` -> `expect(x).toHaveLength(n)`). Les 4 occurrences
> des fichiers `pii-obfuscation/**` etaient deja conformes sur la branche, a des lignes decalees.

- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/dashboard-ui-state.service.spec.ts:120` — Prefer "expect(service.lines()).toHaveLength(1000)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 30c82b0a -->
- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/pii-items-storage.service.spec.ts:45` — Prefer "expect(service.itemsBySpace()['SPACE1']).toHaveLength(1)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- b1140516 -->
- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/pii-items-storage.service.spec.ts:61` — Prefer "expect(service.itemsBySpace()['SPACE1']).toHaveLength(1)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- cc5c27b0 -->
- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/pii-items-storage.service.spec.ts:68` — Prefer "expect(service.itemsBySpace()['SPACE1']).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 144193e8 -->
- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/pii-items-storage.service.spec.ts:87` — Prefer "expect(item.detectedPersonallyIdentifiableInformationList).toHaveLength(1)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 1029d247 -->
- [x] `pii-reporting-ui/src/app/features/confluence-dashboard/services/pii-items-storage.service.spec.ts:97` — Prefer "expect(service.itemsBySpace()['SPACE1']).toHaveLength(400)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- f7d6902f -->
- [x] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-bulk-bar/obfuscation-bulk-bar.component.spec.ts:101` — Prefer "expect(chips).toHaveLength(3)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 8b7326af -->
- [x] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-confirm-dialog/obfuscation-confirm-dialog.component.spec.ts:85` — Prefer "expect(rows).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- e2d394e7 -->
- [x] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-job-progress/obfuscation-job-progress.component.spec.ts:128` — Prefer "expect(outcomes).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 30bfa92d -->
- [x] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.spec.ts:341` — Prefer "expect(lastSearchRequest().selection.severities).toHaveLength(3)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 04fe1a81 -->
- [x] `pii-reporting-ui/src/app/features/pii-page-card/pii-card-collapsed.component.spec.ts:143` — Prefer "expect(badges).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 5f9bed90 -->
- [x] `pii-reporting-ui/src/app/features/pii-page-card/pii-card-expanded.component.spec.ts:124` — Prefer "expect(rows).toHaveLength(4)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 418647b0 -->
- [x] `pii-reporting-ui/src/app/features/pii-page-card/pii-card-expanded.component.spec.ts:159` — Prefer "expect(allReveal).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- fe3a737a -->
- [x] `pii-reporting-ui/src/app/features/pii-page-card/pii-card-expanded.component.spec.ts:354` — Prefer "expect(rows).toHaveLength(1)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 9f3ed3a3 -->
- [x] `pii-reporting-ui/src/app/features/pii-settings/pii-settings.component.spec.ts:411` — Prefer "expect(el.querySelectorAll('p-inputnumber')).toHaveLength(1)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- fe99de21 -->
- [x] `pii-reporting-ui/src/app/features/pii-settings/pii-settings.component.spec.ts:467` — Prefer "expect(readonlyValues).toHaveLength(2)" over this generic assertion for better reporting; it works on any object with a numeric length property. <!-- 58265ee4 -->

### Lot `java:S1117` — 8 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Renommer la variable locale qui masque un champ. Renommage local uniquement, jamais le champ.

- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:684` — Rename "confluenceAccessor" which hides the field declared at line 104. <!-- 78a401e8 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:685` — Rename "contentScanOrchestrator" which hides the field declared at line 105. <!-- 1314b59d -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:689` — Rename "attachmentProcessor" which hides the field declared at line 106. <!-- d3ff3d1a -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:693` — Rename "htmlContentParser" which hides the field declared at line 107. <!-- cc5f6c1b -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:753` — Rename "confluenceAccessor" which hides the field declared at line 104. <!-- c1344909 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:754` — Rename "contentScanOrchestrator" which hides the field declared at line 105. <!-- ad96fcf6 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:758` — Rename "attachmentProcessor" which hides the field declared at line 106. <!-- 92fa236b -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:762` — Rename "htmlContentParser" which hides the field declared at line 107. <!-- b3835310 -->

### Lot `java:S6068` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Simplifier l'appel Mockito indique (ex. `verify(x, times(1))` -> `verify(x)`).

- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/out/JpaFindingRemediationAdapterTest.java:126` — Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. <!-- b500c241 -->

### Lot `java:S9015` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Remplacer le mock construit a la main par un champ `@Mock`, l'extension Mockito etant deja active.

- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/ConfluenceAttachmentHttpClientAdapterTest.java:35` — Use "@Mock" annotation instead of "mock()" for field declaration. <!-- 4f3f6489 -->

---

## Vague B — Production — mecanique locale (50 issues)

Modifie du code de production, mais chaque correction est locale et sans effet de bord fonctionnel.

### Lot `python:S8572` — 21 issue(s)

🟨 MAJOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Dans un bloc `except`, remplacer `logging.error(...)` / `logger.error(...)` par `logger.exception(...)` et supprimer un eventuel `exc_info=True` devenu redondant. Ajoute la stacktrace au log : verifier qu'aucun test n'asserte le texte exact du log.

- [x] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:190` — Use "logging.exception()" instead. <!-- 07542618 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:213` — Use "logging.exception()" instead. <!-- 40a3f8ac -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:264` — Use "logging.exception()" instead. <!-- b54d28e6 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:545` — Use "logging.exception()" instead. <!-- 94303c43 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:1384` — Use "logging.exception()" instead. <!-- 1276b81b -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:182` — Use "logging.exception()" instead. <!-- 7e003740 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:190` — Use "logging.exception()" instead. <!-- 02c6c930 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:197` — Use "logging.exception()" instead. <!-- 37ab53e5 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:290` — Use "logging.exception()" instead. <!-- bd9ceb1e -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:298` — Use "logging.exception()" instead. <!-- 1106d9ca -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:305` — Use "logging.exception()" instead. <!-- 1462a846 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:363` — Use "logging.exception()" instead. <!-- 9f6b9392 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:368` — Use "logging.exception()" instead. <!-- be00fd6d -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:401` — Use "logging.exception()" instead. <!-- ab12db97 -->
- [x] `pii-detector-service/pii_detector/infrastructure/adapter/out/database_config_adapter.py:406` — Use "logging.exception()" instead. <!-- 14ff3b0d -->
- [x] `pii-detector-service/pii_detector/proto/generate_pb.py:93` — Use "logging.exception()" instead. <!-- 8d3cf2f8 -->
- [x] `pii-detector-service/pii_detector/server.py:66` — Use "logging.exception()" instead. <!-- 9d1022e3 -->
- [x] `pii-detector-service/pii_detector/server.py:226` — Use "logging.exception()" instead. <!-- 20c76b6e -->
- [x] `pii-detector-service/pii_detector/server.py:255` — Use "logging.exception()" instead. <!-- 3e8ef13b -->
- [x] `pii-detector-service/pii_detector/server.py:283` — Use "logging.exception()" instead. <!-- d85aab34 -->
- [x] `pii-detector-service/pii_detector/server.py:293` — Use "logging.exception()" instead. <!-- 0c855297 -->

### Lot `java:S1128` — 6 issue(s)

⬜ MINOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Supprimer l'import inutilise. Verifier qu'il n'est pas utilise uniquement dans un Javadoc.

- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/in/PiiAccessController.java:14` — Remove this unused import 'org.springframework.web.bind.annotation.RequestParam'. <!-- 0a20db75 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/remediation/usecase/ExecuteObfuscationUseCaseTest.java:34` — Remove this unused import 'org.assertj.core.api.Assertions.assertThat'. <!-- ff45c38d -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/service/ScanSpaceStatsCollectorTest.java:20` — Remove this unused import 'org.mockito.ArgumentMatchers.anyLong'. <!-- eef2bd38 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/in/DiscoveredLabelControllerTest.java:17` — Remove this unused import 'pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.PiiTypeConfigResponseDto'. <!-- 9089cf43 -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/in/PiiRemediationControllerTest.java:57` — Remove this unused import 'org.assertj.core.api.Assertions.assertThat'. <!-- 14f155ff -->
- [x] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/in/PiiAccessControllerTest.java:27` — Remove this unused import 'org.assertj.core.api.Assertions.assertThat'. <!-- 7de5ac30 -->

### Lot `python:S9073` — 6 issue(s)

🟨 MAJOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Scinder l'assertion composite (`assert a and b`) en deux assertions distinctes. Meme conditions, meme resultat.

- [x] `pii-detector-service/tests/integration/test_concurrency_autotune_e2e.py:209` — Split this composite assertion into separate assertions. <!-- e1618a2c -->
- [x] `pii-detector-service/tests/integration/test_ondemand_benchmark_e2e.py:276` — Split this composite assertion into separate assertions. <!-- 838c0911 -->
- [x] `pii-detector-service/tests/integration/test_ondemand_benchmark_e2e.py:290` — Split this composite assertion into separate assertions. <!-- edb4ca10 -->
- [x] `pii-detector-service/tests/unit/scripts/test_parse_throughput_logs.py:34` — Split this composite assertion into separate assertions. <!-- 8609fc00 -->
- [x] `pii-detector-service/tests/unit/test_ministral_detector.py:266` — Split this composite assertion into separate assertions. <!-- 9cd5881f -->
- [x] `pii-detector-service/tests/unit/test_ministral_detector.py:456` — Split this composite assertion into separate assertions. <!-- 9d1fd2be -->

### Lot `python:S5778` — 5 issue(s)

🟨 MAJOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Ne garder que l'appel qui leve dans le bloc `pytest.raises`. Le reste remonte au-dessus.

- [x] `pii-detector-service/tests/unit/test_regex_detector_branches.py:41` — Refactor this exception test to have only one invocation possibly throwing an exception. <!-- 361bbdd0 -->
- [x] `pii-detector-service/tests/unit/test_regex_detector_branches.py:58` — Refactor this exception test to have only one invocation possibly throwing an exception. <!-- 8146d82b -->
- [x] `pii-detector-service/tests/unit/test_semantic_chunker.py:224` — Refactor this exception test to have only one invocation possibly throwing an exception. <!-- f6295519 -->
- [x] `pii-detector-service/tests/unit/test_semantic_chunker.py:230` — Refactor this exception test to have only one invocation possibly throwing an exception. <!-- bd2b9156 -->
- [x] `pii-detector-service/tests/unit/test_semantic_chunker.py:236` — Refactor this exception test to have only one invocation possibly throwing an exception. <!-- 87c72831 -->

### Lot `java:S7467` — 2 issue(s)

⬜ MINOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Remplacer le parametre d'exception inutilise par la variable anonyme `_` (Java 21+).

> Premier `check api` rouge sur `PurgeDetectionDataUseCaseTest` (2 tests) : contexte Spring non demarre,
> connexion JDBC refusee (SQLState 08001). Cause environnementale, sans rapport avec le lot. Verifie en
> rejouant la classe sur l'arbre propre (verte), puis `check api` complet a nouveau vert avec le lot applique.

- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/out/ConfluencePageRedactionAdapter.java:45` — Replace "e" with an unnamed pattern. <!-- a3ada538 -->
- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/remediation/adapter/out/ConfluencePageRedactionAdapter.java:69` — Replace "firstConflict" with an unnamed pattern. <!-- e84abb28 -->

### Lot `python:S9083` — 2 issue(s)

⬜ MINOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Uniformiser les parentheses des decorateurs pytest (`@pytest.fixture` sans parentheses vides).

- [x] `pii-detector-service/tests/integration/test_concurrency_autotune_e2e.py:161` — Remove empty parentheses from this decorator. <!-- 7b1c1a4c -->
- [x] `pii-detector-service/tests/integration/test_ondemand_benchmark_e2e.py:190` — Remove empty parentheses from this decorator. <!-- 78e282a2 -->

### Lot `java:S8491` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Le Javadoc ne documente rien : soit le rattacher a la declaration suivante, soit le convertir en commentaire bloc. Ne pas supprimer une information utile.

- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/ContentScanOrchestrator.java:179` — Remove or merge the dangling Javadoc comment(s). <!-- bfdf3a70 -->

### Lot `java:S6213` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Renommer l'identifiant qui utilise un mot reserve restreint (`var`, `record`, `yield`, `sealed`).

> ESCALADE : `DiscoveredLabelCollector.record(Map)` est une methode publique appelee ailleurs
> (`AbstractStreamConfluenceScanUseCase:630` et `DiscoveredLabelCollectorTest`). Le RUNBOOK impose
> l'escalade des qu'une signature publique appelee ailleurs doit changer. Le renommage est mecanique
> et verifiable par le compilateur (2 sites d'appel connus, aucun contrat externe) : a valider par
> une decision humaine, pas par l'agent.

- [!] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/DiscoveredLabelCollector.java:39` — Rename this method to not match a restricted identifier. <!-- 5f3d6c9b -->

### Lot `java:S1612` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Remplacer le lambda par une reference de methode.

- [x] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/DiscoveredLabelPersistenceAdapter.java:31` — Replace this lambda with method reference 'jpaRepository::upsertOccurrence'. <!-- a4ef4892 -->

### Lot `python:S3415` — 1 issue(s)

🟨 MAJOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Remettre les arguments d'assertion dans l'ordre attendu/obtenu. Ne change pas le verdict du test.

- [x] `pii-detector-service/tests/unit/test_composite_detector_run_stats.py:298` — Swap these 2 sides so they are in the correct order: actual value, expected value. <!-- fd5590e8 -->

### Lot `python:S5781` — 1 issue(s)

🟨 MAJOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Supprimer la valeur dupliquee dans le litteral d'ensemble. Verifier qu'il ne s'agit pas d'une valeur manquante mal recopiee.

- [x] `pii-detector-service/pii_detector/infrastructure/postfilter/strategies/credential_plausibility.py:146` — Change or remove duplicates of this key. <!-- 0a17e8b4 -->

### Lot `python:S8714` — 1 issue(s)

⬜ MINOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : Remplacer le `try/except ... fail()` par `pytest.raises`.

- [x] `pii-detector-service/tests/unit/test_pii_entity_dynamic_attributes.py:55` — Remove this try/except block and let the test fail naturally if an exception is raised. <!-- 67d0b542 -->

### Lot `typescript:S7776` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer le tableau utilise seulement pour un test d'appartenance par un `Set`.

- [x] `pii-reporting-ui/src/app/core/services/toast.service.ts:36` — `SCAN_PAUSED_TYPES` should be a `Set`, and use `SCAN_PAUSED_TYPES.has()` to check existence or non-existence. <!-- 24e95e52 -->

### Lot `typescript:S1128` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Supprimer l'import inutilise.

- [x] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.ts:13` — Remove this unused import of 'SeverityFilterValue'. <!-- d98a3deb -->

---

## Vague C — Accessibilite UI — modifie le DOM rendu (16 issues)

Change le HTML produit. Peut casser des selecteurs de test ou un snapshot : vague isolee, verifiee a part.

### Lot `Web:MouseEventWithoutKeyboardEquivalentCheck` — 6 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Ajouter le gestionnaire clavier correspondant (`(keyup.enter)` / `(keydown.space)`) a cote du `(click)`. Ne pas retirer le gestionnaire souris.

> ESCALADE (6/6) : faux positifs. Les 6 elements sont des `<p-button>`, et PrimeNG 21 rend un
> `<button>` natif (verifie dans `node_modules/primeng/fesm2022/primeng-button.mjs`). Un bouton natif
> est deja actionnable au clavier : Entree et Espace declenchent un evenement `click`. Ajouter
> `(keyup.enter)` ou `(keydown.space)` a cote du `(click)` ferait donc executer l'action **deux fois**
> a chaque activation clavier — `nextPage()` sauterait une page, `toggleSortOrder()` reviendrait a son
> etat initial. La correction prescrite introduirait une regression pour les utilisateurs au clavier,
> c'est-a-dire exactement le public que la regle protege. Resolution correcte : marquer ces 6 issues
> en faux positif cote SonarQube — decision humaine, le RUNBOOK interdit a l'agent de toucher au
> statut des issues.

- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:109` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 5bae9c35 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:119` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- eaec42f0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:127` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 481603b4 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:115` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 1f404321 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:125` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 87623884 -->
- [!] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:142` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- ecd7c5b7 -->

### Lot `Web:S6819` — 6 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer le `role=` ARIA par la balise HTML native equivalente : `role="region"` -> `<section>`, `role="status"` -> `<output>`. Verifier qu'aucun test ne cible le role via un selecteur.

> LOT ANNULE (rollback) — `check ui` rouge sur 2 tests :
> `ObfuscationJobProgressComponent.Should_RenderBackendProgressVerbatim_When_JobRunning` et
> `PiiObfuscationComponent.Should_PreselectAllSeveritiesAndShowBanner_When_PreselectParamTrue`.
> Les deux assertent `element.getAttribute('role') === 'status'` : le role est bien cible par les
> tests, exactement le cas que la ligne Correction demandait de verifier. Les rendre verts
> supposerait de modifier ces assertions, ce que le RUNBOOK interdit.
>
> A trancher par un humain, en deux parties :
> 1. `role="region"` / `role="status"` (4 issues) : la conversion `<section>` / `<output>` est
>    correcte et sans impact visuel (les classes fixent `display` explicitement). Elle demande
>    d'ajuster 2 assertions de test, qui verifieraient alors le nom de balise plutot que le role.
> 2. `role="group"` sur `<span class="ob-segmented">` (2 issues) : aucune des balises proposees par
>    Sonar ne convient. `<fieldset>` apporte `padding`, `margin` et `min-inline-size: min-content`
>    non neutralises par `.ob-segmented`, donc un changement visuel ; `<details>`, `<address>` et
>    `<optgroup>` sont semantiquement faux pour un controle segmente. `role="group"` est le bon
>    marquage ici : ces 2 issues sont a passer en faux positif.

- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-bulk-bar/obfuscation-bulk-bar.component.html:3` — Use <section> instead of the region role to ensure accessibility across all devices. <!-- cccefd2a -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-job-progress/obfuscation-job-progress.component.html:2` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 6866961e -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:50` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- a37f9219 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:169` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- 7b8955b0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:205` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- a1bfc9a0 -->
- [!] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:387` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 2bbebd52 -->

### Lot `Web:ItemTagNotWithinContainerTagCheck` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remettre le `<li>` dans un conteneur `<ul>` / `<ol>`.

> ESCALADE : faux positif. Le `<dt>` est deja dans un `<dl>` (ligne 36), avec un
> `<div class="scan-stats-row">` intermediaire. C'est valide : le modele de contenu de `<dl>` accepte
> des `<div>` regroupant chacun ses `<dt>`/`<dd>` (standard HTML vivant). L'analyseur Sonar ne
> connait pas cette forme et decrit une situation qui n'existe pas. Supprimer le `<div>` obligerait a
> deplacer les regles `.scan-stats-row` (`display:flex`, `justify-content:space-between`, `gap`) sur
> le `<dl>`, donc a modifier le CSS avec un risque visuel, pour contourner une regle mal appliquee.
> A passer en faux positif cote SonarQube.

- [!] `pii-reporting-ui/src/app/features/confluence-dashboard/components/space-scan-stats-popover/space-scan-stats-popover.component.html:38` — Surround this <dt> item tag by a <dl> container one. <!-- 2bbd57b7 -->

### Lot `css:S1874` — 1 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer la propriete CSS depreciee par son equivalent courant.

- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-finding-row/obfuscation-finding-row.component.css:54` — Deprecated keyword "break-word" for property "word-break" <!-- 5577e7b8 -->

### Lot `Web:S5256` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Ajouter les en-tetes `<th>` manquants au tableau.

- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-confirm-dialog/obfuscation-confirm-dialog.component.html:27` — Add "<th>" headers to this "<table>". <!-- b8d41449 -->

### Lot `Web:InputWithoutLabelCheck` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Associer un `<label for>` au champ, ou un `aria-label` si l'etiquette est deja visible ailleurs.

- [ ] `pii-reporting-ui/src/app/features/pii-settings/pii-settings.component.html:408` — Add an "id" attribute to this input field and associate it with a label. <!-- ff61389e -->

---

## Vague D — Jugement requis — lire le RUNBOOK avant (25 issues)

Changement de signature, de semantique ou de structure. Chaque lot a une garde explicite.

### Lot `java:S8688` — 8 issue(s)

ℹ️ INFO · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : GARDE : ne pas injecter de `Clock` a la volee. Utiliser la zone deja retenue par le projet et rester coherent avec le code voisin. Si le fichier n'a pas de convention, escalader plutot que d'en inventer une.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/ScanCheckpointService.java:253` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- ead19c4a -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/jpa/mapper/ConfluenceSpaceEntityMapper.java:43` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 943e88ce -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/mapper/ConfluencePageMapper.java:109` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- b6380c6b -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/confluence/adapter/out/mapper/ConfluencePageMapper.java:114` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- d31061be -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:78` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 016878d7 -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:79` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 83dcbd7d -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/entity/PiiTypeConfigEntity.java:87` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 99a87663 -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/ScanCheckpointPersistenceAdapter.java:39` — Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. <!-- 60b5b4ce -->

### Lot `python:S3776` — 4 issue(s)

🟧 CRITICAL · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : GARDE : extraire une fonction privee nommee, sans changer les entrees/sorties. Gain vise : passer sous 15. Si la fonction est couverte par moins d'un test, escalader au lieu de refactorer.

- [ ] `pii-detector-service/pii_detector/application/config/detection_policy.py:153` — Refactor this function to reduce its Cognitive Complexity from 16 to the 15 allowed. <!-- e3cccb43 -->
- [ ] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:834` — Refactor this function to reduce its Cognitive Complexity from 16 to the 15 allowed. <!-- 5eb32dce -->
- [ ] `pii-detector-service/pii_detector/infrastructure/adapter/in/grpc/pii_service.py:1092` — Refactor this function to reduce its Cognitive Complexity from 18 to the 15 allowed. <!-- f27efd85 -->
- [ ] `pii-detector-service/pii_detector/infrastructure/postfilter/strategies/credential_plausibility.py:228` — Refactor this function to reduce its Cognitive Complexity from 16 to the 15 allowed. <!-- 4ef460a3 -->

### Lot `typescript:S2699` — 3 issue(s)

🟥 BLOCKER · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : GARDE CRITIQUE : ajouter l'assertion qui manque, celle que le test pretend verifier d'apres son nom. Si elle passe au rouge, c'est un bug de production revele : NE PAS affaiblir l'assertion pour la faire passer. Marquer l'issue `[!]` et escalader.

- [ ] `pii-reporting-ui/src/app/core/services/sentinelle-api.service.spec.ts:53` — Add at least one assertion to this test case. <!-- 43ccc3a9 -->
- [ ] `pii-reporting-ui/src/app/features/pii-settings/pii-settings.component.spec.ts:596` — Add at least one assertion to this test case. <!-- f338204a -->
- [ ] `pii-reporting-ui/src/app/features/pii-settings/pii-settings.component.spec.ts:636` — Add at least one assertion to this test case. <!-- 9855a147 -->

### Lot `java:S8947` — 2 issue(s)

🟧 CRITICAL · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Retirer le `final` sur la methode d'entite JPA : il empeche le proxy Hibernate de fonctionner (bug reel, pas un nit).

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/jpa/entity/PiiAccessAuditEntity.java:66` — Remove this "final" modifier from this JPA entity method. <!-- 0af73dfc -->
- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/jpa/entity/PiiAccessAuditEntity.java:87` — Remove this "final" modifier from this JPA entity method. <!-- 1f31b5f1 -->

### Lot `python:S5332` — 2 issue(s)

⬜ MINOR · modules : pii-detector-service · 1 commit pour tout le lot

**Correction** : GARDE : `http://` vise ici une instance LM Studio locale. Passer en `https://` casserait le detector. Documenter l'usage local et marquer l'issue en attente d'arbitrage, ne pas modifier l'URL.

- [ ] `pii-detector-service/pii_detector/infrastructure/detector/ministral_detector.py:457` — Using HTTP protocol is insecure. Use HTTPS instead. <!-- 9ce0cc94 -->
- [ ] `pii-detector-service/pii_detector/infrastructure/detector/ministral_detector.py:457` — Using HTTP protocol is insecure. Use HTTPS instead. <!-- f664922a -->

### Lot `typescript:S5976` — 2 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Regrouper les tests similaires en un `it.each`. Conserver un cas par jeu de donnees, ne pas en perdre en route.

- [ ] `pii-reporting-ui/src/app/shared/confidence-indicator/confidence-indicator.component.spec.ts:13` — Replace these 5 tests with a single Parameterized one. <!-- b0578b79 -->
- [ ] `pii-reporting-ui/src/app/shared/detector-tag/detector-tag.component.spec.ts:13` — Replace these 3 tests with a single Parameterized one. <!-- 92886a2d -->

### Lot `java:S2143` — 1 issue(s)

ℹ️ INFO · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Remplacer l'API de date historique par `java.time`. Verifier le format produit dans l'export Excel.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/export/adapter/out/ExcelDetectionReportWriterAdapter.java:?` — Use the "java.time" API for date and time. <!-- b7ae8a9e -->

### Lot `java:S107` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : GARDE : 9 parametres sur une methode de repository. Regrouper en objet de criteres touche tous les appelants. Escalader si plus de 3 appelants.

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/reporting/adapter/out/jpa/ScanDetectorStatsJpaRepository.java:46` — Method has 9 parameters, which is greater than 7 authorized. <!-- 47955961 -->

### Lot `java:S2925` — 1 issue(s)

🟨 MAJOR · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : GARDE : remplacer `Thread.sleep` par une attente conditionnelle (Awaitility ou latch). Si le test devient instable, revenir au sleep et escalader.

- [ ] `pii-reporting-api/src/test/java/pro/softcom/aisentinel/application/pii/reporting/usecase/StreamConfluenceScanUseCaseTest.java:212` — Remove this use of "Thread.sleep()". <!-- 0aad8b86 -->

### Lot `java:S6809` — 1 issue(s)

🟧 CRITICAL · modules : pii-reporting-api · 1 commit pour tout le lot

**Correction** : Extraire la methode transactionnelle dans un bean injecte : appelee via `this`, la transaction n'est jamais ouverte (bug reel).

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/infrastructure/pii/detection/adapter/out/PiiDetectionConfigPersistenceAdapter.java:116` — Call transactional methods via an injected dependency instead of directly via 'this'. <!-- 9b210e90 -->


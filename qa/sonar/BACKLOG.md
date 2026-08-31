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
| **B** | Production — mecanique locale | 1 | ⬜ a faire |
| **C** | Accessibilite UI — modifie le DOM rendu | 13 | ⬜ a faire |
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

- [ ] `pii-reporting-api/src/main/java/pro/softcom/aisentinel/application/pii/reporting/service/DiscoveredLabelCollector.java:39` — Rename this method to not match a restricted identifier. <!-- 5f3d6c9b -->

---

## Vague C — Accessibilite UI — modifie le DOM rendu (13 issues)

Change le HTML produit. Peut casser des selecteurs de test ou un snapshot : vague isolee, verifiee a part.

### Lot `Web:MouseEventWithoutKeyboardEquivalentCheck` — 6 issue(s)

⬜ MINOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Ajouter le gestionnaire clavier correspondant (`(keyup.enter)` / `(keydown.space)`) a cote du `(click)`. Ne pas retirer le gestionnaire souris.

- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:109` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 5bae9c35 -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:119` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- eaec42f0 -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:127` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 481603b4 -->
- [ ] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:115` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 1f404321 -->
- [ ] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:125` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- 87623884 -->
- [ ] `pii-reporting-ui/src/app/shared/components/space-filters/space-filters.component.html:142` — Add a 'onKeyDown|onKeyUp' attribute to this <p-button> tag. <!-- ecd7c5b7 -->

### Lot `Web:S6819` — 6 issue(s)

🟨 MAJOR · modules : pii-reporting-ui · 1 commit pour tout le lot

**Correction** : Remplacer le `role=` ARIA par la balise HTML native equivalente : `role="region"` -> `<section>`, `role="status"` -> `<output>`. Verifier qu'aucun test ne cible le role via un selecteur.

- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-bulk-bar/obfuscation-bulk-bar.component.html:3` — Use <section> instead of the region role to ensure accessibility across all devices. <!-- cccefd2a -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/components/obfuscation-job-progress/obfuscation-job-progress.component.html:2` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 6866961e -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:50` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- a37f9219 -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:169` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- 7b8955b0 -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:205` — Use <address> or <details> or <fieldset> or <optgroup> instead of the group role to ensure accessibility across all devices. <!-- a1bfc9a0 -->
- [ ] `pii-reporting-ui/src/app/features/pii-obfuscation/pii-obfuscation.component.html:387` — Use <output> instead of the status role to ensure accessibility across all devices. <!-- 2bbebd52 -->

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


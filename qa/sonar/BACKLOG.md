# Backlog SonarQube — ai-sentinel

> Généré le 2026-09-21 10:53 UTC depuis le MCP SonarQube, après une analyse complète des trois
> modules au commit `03ec1f3ece8c1536252611ba58d548aca97f919a`.
> Périmètre : issues **OPEN / CONFIRMED** de la branche d'analyse `main`.
>
> L'analyse `main` décrit la branche de travail `feature/findings-improvement`. Les numéros de
> ligne sont valides sur ce commit et peuvent changer dès la première correction.

**Restantes : 2 issues sur 17** — **0 security hotspot**.

Protocole d'exécution : [RUNBOOK.md](RUNBOOK.md).

## État des quality gates

| Module | Gate | Nouvelles issues | Couverture du nouveau code | Duplication du nouveau code |
|--------|------|------------------|-----------------------------|-----------------------------|
| API | OK | 0 | 83,5 % — OK | 0,0 % — OK |
| Detector | OK | 0 | 88,4 % — OK | 0,04858 % — OK |
| UI | ERROR | 2 | 76,1 % — ERROR | 0,0 % — OK |

La couverture UI est une dette de quality gate distincte des 17 issues ci-dessous.

## Avancement par vague

| Vague | Périmètre | Issues | Statut |
|-------|-----------|--------|--------|
| **A** | Tests — mécanique pure | 3 | ✅ terminée et vérifiée |
| **B** | Production — mécanique locale | 6 | 🔄 en cours |
| **C** | Accessibilité UI — modifie le DOM rendu | 1 | ⬜ à faire |
| **D** | Jugement requis — comportement ou contrat à préserver | 7 | ✅ terminée et vérifiée |

Statuts : ⬜ à faire · 🔄 en cours · ✅ terminée et vérifiée · ⛔ annulée.

Légende : `[ ]` à traiter · `[x]` corrigée et vérifiée · `[!]` à arbitrer.

---

## Vague A — Tests — mécanique pure (3 issues)

### `java:S1612` — référence de méthode (1)

- [x] `3c9338bb-7541-4dbb-87dd-36146f2bc7fe` — `HtmlContentParserTest.java:35`
  — remplacer `segment -> segment.length()` par `Segment::length`.

### `java:S6126` — text blocks (2)

- [x] `ccf7a6db-554e-4188-913f-a805db6675ae` —
  `TikaAttachmentTextExtractorAdapterTest.java:265` — convertir la chaîne multiligne en text block
  sans modifier son contenu.
- [x] `3cffbc1b-eaa3-47ba-9c75-d24dd1eb897d` —
  `TableHeaderPrecisionBenchmarkIT.java:193` — convertir la chaîne multiligne en text block sans
  modifier son contenu.

Vérification : tests ciblés des deux classes, puis `mvn test`.

---

## Vague B — Production — mécanique locale (6 issues)

### `typescript:S3358` — ternaire imbriqué (1)

- [ ] `d18cd455-661c-4d41-8905-d45c6935c3f4` — `confluence-dashboard.component.ts:124`
  — extraire le calcul de `judgeUnavailableReason` dans des branches explicites, à résultat
  identique.

### `java:S1192` — littéraux HTML répétés (3)

- [x] `da763a3d-548c-42ad-b8e6-b581453c9f3f` — `HtmlTableContextualizer.java:30` — constante pour
  `"table"`.
- [x] `e3a48574-0497-4b94-b056-a828a903751e` — `HtmlTableContextualizer.java:138` — constante pour
  `"thead"`.
- [x] `f376c849-6279-45fb-89f7-3261d3723f98` — `HtmlTableContextualizer.java:188` — constante pour
  `"colgroup"`.

Traiter les trois occurrences dans un seul lot : elles concernent le même fichier et la même
règle.

### `java:S6213` — identifiant réservé (1)

- [x] `ca48afda-25c9-4c38-8cf4-0309e89aa356` — `CsvAttachmentTableParser.java:43` — renommer la
  variable locale `record` en `csvRecord`.

### `java:S7467` — paramètre d'exception inutilisé (1)

- [x] `75dc91e0-cd9e-474e-ab1d-a247fbffbda0` — `CsvAttachmentTableParser.java:53` — remplacer
  `ignored` par le motif anonyme `_`, disponible avec Java 25.

Vérification : tests ciblés des fichiers touchés, puis suites complètes des modules concernés.

---

## Vague C — Accessibilité UI — modifie le DOM rendu (1 issue)

### `Web:S6819` — élément HTML natif (1)

- [ ] `8c388a68-e34c-406c-b2ee-7489bbd75034` — `judge-launch-dialog.component.html:120` — remplacer
  `<p role="status">` par `<output>` en conservant `aria-live="polite"`, puis vérifier les styles et
  l'annonce par lecteur d'écran.

Vérification : test du composant, suite UI complète et contrôle clavier/lecteur d'écran.

---

## Vague D — Jugement requis (7 issues)

### `java:S2259` — parent HTML potentiellement absent (1)

- [x] `05d86123-66bb-44cb-8e13-29a41436f59a` — `HtmlTableContextualizer.java:31` — protéger le
  résultat de `table.parent()` sans ignorer une table racine. Ajouter un test avec un élément sans
  parent avant de choisir la correction.

### `java:S112` — exception trop générique (1)

- [x] `ecb09632-e31c-4b25-9d92-89ed0216baff` — `TikaAttachmentTextExtractorAdapter.java:101` —
  remplacer `throws Exception` par les exceptions réellement émises (`IOException`, `SAXException`
  et `TikaException` à confirmer par compilation), sans modifier le repli vers l'extraction texte.

### `java:S6213` — méthode publique nommée `record` (1)

- [x] `1fbbbaa2-1cc3-4a2b-a1f8-4ac972327279` — `OutagePausedScans.java:26` — renommer la méthode et
  tous ses appelants dans le même lot. Conserver les règles d'enregistrement des pauses et les tests
  du job de reprise automatique.

### `python:S8786` — backtracking non linéaire (1)

- [x] `7511ccd1-34f0-4ca4-9055-b3647565a321` — `minimum_length.py:50` — remplacer le parsing de
  cookie ambigu par un découpage déterministe ou une expression sans backtracking. Tester une valeur
  très longue sans `;`, les attributs de cookie et les entrées invalides.

### `python:S6353` — classe de chiffres ASCII (3)

- [x] `6d3780d4-742e-4fba-9388-825cbec87f70` — `technical_artifact_denylist.py:169`.
- [x] `8deacf4e-dceb-4580-94ee-aa3b61945abc` — `technical_artifact_denylist.py:169`.
- [x] `dff81af9-853c-422f-8303-83069f2bad08` — `technical_artifact_denylist.py:169`.

Garde : `\d` accepte les chiffres Unicode alors que `[0-9]` limite actuellement l'AVS aux chiffres
ASCII. Ne pas appliquer le remplacement brut proposé par Sonar. Vérifier une forme ASCII explicite,
par exemple `(?a:\d)`, avec des tests ASCII et Unicode avant de corriger les trois occurrences.

---

## Ordre conseillé

1. Vague A, puis analyse Sonar de l'API.
2. Vague B, puis analyses des modules touchés.
3. Vague C avec contrôle d'accessibilité.
4. Vague D, une règle par lot avec tests de non-régression.
5. Rejouer les trois scripts Sonar et régénérer ce backlog depuis le MCP.

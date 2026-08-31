# Rapport — nettoyage SonarQube `chore/sonar-cleanup`

Protocole suivi : [RUNBOOK.md](RUNBOOK.md). Backlog de depart : 178 issues.
Re-scan de cloture le 2026-08-31 08:04 (les trois modules re-analyses sur le serveur local).

**Resultat : 178 → 25 issues, aucune regression de test.** Les 25 restantes sont 24 escalades
documentees ci-dessous, qui demandent toutes une decision humaine, plus une issue qui n'etait pas
au backlog de depart (`java:S1192`, voir la derniere section).

## Issues par vague

| Vague | Perimetre | Avant | Apres | Corrigees | Escaladees |
|-------|-----------|-------|-------|-----------|------------|
| **A** | Tests — mecanique pure | 87 | 0 | 87 | 0 |
| **B** | Production — mecanique locale | 50 | 1 | 49 | 1 |
| **C** | Accessibilite UI | 16 | 13 | 3 | 13 (dont 6 en lot annule) |
| **D** | Jugement requis | 25 | 10 | 15 | 10 |
| — | Hors backlog de depart | — | 1 | — | — |
| **Total** | | **178** | **25** | **154** | **24** |

Le compte des cases cochees et le compte du re-scan concordent exactement : chaque case `[x]` a bien
fait disparaitre son issue du serveur.

## Suites de tests

`qa/sonar/baseline.sh check` sur les trois suites, apres le dernier lot :

| Suite | Tests | Echecs | Reference du 2026-08-30 |
|-------|-------|--------|--------------------------|
| api | 1657 | 0 | 0 echec — identique |
| detector | 630 | 0 | 0 echec — identique |
| ui | 621 | 0 | 0 echec — identique |

**2908 tests, aucun echec, aucune regression.** Le point de depart etait entierement vert et l'arrivee
l'est aussi. Aucune assertion n'a ete affaiblie, aucun test n'a ete desactive.

## Commits produits

Un lot corrige = un commit `fix`, suivi d'un commit `chore` qui coche les cases du backlog.

La colonne « occurrences » reprend le decompte du message de commit, qui compte les endroits corriges
dans le code — il differe du nombre d'issues du lot quand plusieurs issues pointent le meme endroit
(`typescript:S5906` : 16 issues pour 12 corrections).

| Vague | Regle | Commit de correction | Occurrences |
|-------|-------|----------------------|-------------|
| A | `java:S5778` | `bbe5ac61` | 36 |
| A | `java:S8924` | `1ada0f31` | 25 |
| A | `typescript:S5906` | `e60fc4c9` | 12 |
| A | `java:S1117` | `094679ec` | 8 |
| A | `java:S6068` | `c86b9cb9` | 1 |
| A | `java:S9015` | `9b435f20` | 1 |
| B | `python:S8572` | `7156fd3d` | 21 |
| B | `java:S1128` | `8a5926b0` | 6 |
| B | `python:S9073` | `1ef35496` | 6 |
| B | `python:S5778` | `aa54bec7` | 5 |
| B | `java:S7467` | `d0d6b51a` | 2 |
| B | `python:S9083` | `7f10519e` | 2 |
| B | `java:S8491` | `9eca7d0c` | 1 |
| B | `java:S1612` | `38e05497` | 1 |
| B | `python:S3415` | `30467768` | 1 |
| B | `python:S5781` | `27163000` | 1 |
| B | `python:S8714` | `e6dd7fa2` | 1 |
| B | `typescript:S7776` | `4d28deb7` | 1 |
| B | `typescript:S1128` | `b66c24e6` | 1 |
| C | `css:S1874` | `238412f3` | 1 |
| C | `Web:S5256` | `bacd26b7` | 1 |
| C | `Web:InputWithoutLabelCheck` | `5bb46042` | 1 |
| D | `java:S8688` | `6d2dad34` | 1 (sur 8) |
| D | `python:S3776` | `4a2c3970` | 4 |
| D | `typescript:S2699` | `2d8cfa82` | 3 |
| D | `java:S8947` | `d9ee825d` | 2 |
| D | `typescript:S5976` | `c9d38235` | 2 |
| D | `java:S2143` | `7f78c5db` | 1 |
| D | `java:S2925` | `3f0352b8` | 1 |
| D | `java:S6809` | `50b1db41` | 1 |

Commits sans correction de code : `1898f60c`, `5027036d`, `53b217ae`, `c87dce3e`, `da967e10`
(escalades), `04082064` (documentation de l'usage HTTP local pour `python:S5332`).

## Escalades

Aucun **BUG REVELE** : le lot `typescript:S2699` (tests sans assertion) a ete corrige entierement et
les trois assertions ajoutees sont passees au vert du premier coup. Aucun bug de production n'etait
masque derriere ces tests.

### Lot annule par un test rouge — `Web:S6819`, 6 issues

Seul lot annule de la nuit (`53b217ae`). Le remplacement de `role="status"` par `<output>` a fait
echouer deux tests :

- `ObfuscationJobProgressComponent.Should_RenderBackendProgressVerbatim_When_JobRunning`
- `PiiObfuscationComponent.Should_PreselectAllSeveritiesAndShowBanner_When_PreselectParamTrue`

Les deux assertent `element.getAttribute('role') === 'status'`. Le lot a ete annule en entier, comme
prescrit. La decision se decoupe en deux :

1. `role="region"` et `role="status"` (4 issues) : la conversion en `<section>` / `<output>` est
   correcte et sans impact visuel. Elle demande d'ajuster deux assertions de test, qui verifieraient
   alors le nom de balise plutot que le role.
2. `role="group"` sur `<span class="ob-segmented">` (2 issues) : aucune balise proposee par Sonar ne
   convient. `<fieldset>` apporte des marges et un `min-inline-size` non neutralises ; `<details>`,
   `<address>` et `<optgroup>` sont semantiquement faux pour un controle segmente. A passer en faux
   positif.

### Faux positifs a marquer cote SonarQube — 7 issues

- **`Web:MouseEventWithoutKeyboardEquivalentCheck`, 6 issues.** Les 6 elements sont des `<p-button>`,
  et PrimeNG 21 rend un `<button>` natif, deja actionnable au clavier. Ajouter `(keyup.enter)` a cote
  du `(click)` executerait l'action deux fois a chaque activation clavier : `nextPage()` sauterait une
  page, `toggleSortOrder()` reviendrait a son etat initial. La correction prescrite casserait
  l'usage clavier, c'est-a-dire exactement ce que la regle protege.
- **`Web:ItemTagNotWithinContainerTagCheck`, 1 issue.** Le `<dt>` est deja dans un `<dl>`, avec un
  `<div>` intermediaire — une forme valide en HTML que l'analyseur ne connait pas. Supprimer ce
  `<div>` obligerait a deplacer des regles CSS de mise en page, avec un risque visuel.

### Convention de projet a trancher — `java:S8688`, 7 issues

Le projet n'a pas une zone horaire de reference : `ZoneId.systemDefault()` (5 usages) et
`ZoneId.of("UTC")` (3 usages) coexistent dans `src/main`. La garde du lot interdit d'en inventer une.
Seul `ConfluenceSpaceEntityMapper` avait une convention interne, il a ete corrige.

Le cas le plus sensible est `PiiTypeConfigEntity` : ses methodes `@PrePersist` / `@PreUpdate` ecrivent
`created_at` et `updated_at` de `pii_type_config`, alors que `PiiDetectionConfigPersistenceAdapter`
ecrit ses horodatages en UTC. Choisir au hasard melangerait deux zones sur les colonnes de date de la
meme fonctionnalite. Decider d'abord la zone de reference du projet, puis appliquer les 7 d'un coup.

### Renommage d'une methode publique — `java:S6213`, 1 issue

`DiscoveredLabelCollector.record(Map)` porte un mot reserve restreint. C'est une methode publique
appelee ailleurs (`AbstractStreamConfluenceScanUseCase:630` et son propre test), et le runbook impose
l'escalade des qu'une signature publique doit changer. Le renommage est mecanique et verifie par le
compilateur : deux sites d'appel connus, aucun contrat externe. Il ne manque qu'un accord.

### Requete native non couverte — `java:S107`, 1 issue

`ScanDetectorStatsJpaRepository.accumulate` a 9 parametres, mais ce ne sont pas des arguments de
logique metier : ce sont les marqueurs de liaison d'un `INSERT ... ON CONFLICT` natif. Les regrouper
dans le record `ScanDetectorStatDelta` obligerait a remplacer chaque `:busyMs` par
`:#{#delta.busyMs()}`, et six des sept valeurs apparaissent deux fois dans l'upsert : la requete
passerait de 9 parametres nommes a 13 expressions SpEL dupliquees.

Surtout, ce serait invisible aux tests : aucun test n'execute cette requete, les deux qui la
mentionnent passent par un mock. Une expression SpEL fautive n'est evaluee qu'au premier appel, donc
`check api` resterait vert et la panne n'apparaitrait qu'au premier scan reel, sur le chemin
d'ecriture des statistiques. Soit accepter la version SpEL avec un test qui execute vraiment
l'upsert, soit passer l'issue en faux positif.

### Usage HTTP local documente — `python:S5332`, 2 issues

`http://` vise l'instance LM Studio locale, qui n'expose son API que sur la boucle locale et sans
ecouteur TLS. L'URL est restee inchangee, conformement a la garde, et l'usage local est documente par
un commentaire au-dessus de la constante. Les deux issues restent ouvertes, a rearbitrer si le
serveur de modele sort de la machine.

## Une issue hors backlog de depart — `java:S1192`

Le re-scan remonte `java:S1192` sur `AbstractStreamConfluenceScanUseCase:251` (« Define a constant
instead of duplicating this literal "cause" 3 times »), absente du backlog du 2026-08-30.

Elle n'a pas ete introduite par le nettoyage : aucun commit de cette branche ne touche ce fichier
(`git log 09eb6773..HEAD -- .../AbstractStreamConfluenceScanUseCase.java` ne renvoie rien), et les
trois occurrences du litteral y sont depuis la branche `chore/improve-error-handling`. L'analyse du
30 aout ne l'avait donc pas remontee. Elle est a traiter comme une issue ordinaire, hors du perimetre
de cette nuit.

# Rapport — nettoyage SonarQube `chore/sonar-cleanup`

Protocole suivi : [RUNBOOK.md](RUNBOOK.md). Backlog de depart : 178 issues.
Re-scan de cloture le 2026-08-31 13:47 (les trois modules re-analyses sur le serveur local).

**Resultat : 178 → 0 issue, aucune regression de test.** Les 25 issues qui restaient apres la
premiere passe — dont 24 escalades en attente d'arbitrage — ont toutes ete corrigees dans le code.
Aucune n'a ete fermee cote serveur : aucun changement de statut, aucune exclusion, aucun `NOSONAR`.

## Issues par vague

| Vague | Perimetre | Depart | 1re passe | Cloture |
|-------|-----------|--------|-----------|---------|
| **A** | Tests — mecanique pure | 87 | 0 | 0 |
| **B** | Production — mecanique locale | 50 | 1 | 0 |
| **C** | Accessibilite UI | 16 | 13 | 0 |
| **D** | Jugement requis | 25 | 10 | 0 |
| — | Hors backlog de depart (`java:S1192`) | — | 1 | 0 |
| **Total** | | **178** | **25** | **0** |

Verification cote serveur, apres re-analyse des trois modules :

| Controle | Resultat |
|----------|----------|
| Issues OPEN / CONFIRMED, 3 projets | **0** |
| Security hotspots TO_REVIEW, 3 projets | **0** |
| `new_violations`, 3 projets | **0** |

## Suites de tests

| Suite | Tests | Echecs | Reference du 2026-08-30 |
|-------|-------|--------|--------------------------|
| api | 1660 | 0 | 1657, 0 echec — +3 tests ajoutes |
| detector | 670 | 0 | 630, 0 echec — +40 tests ajoutes |
| ui | 621 | 0 | 621, 0 echec — identique |

**2951 tests, aucun echec, aucune regression.** Aucune assertion n'a ete supprimee ni elargie,
aucun test n'a ete desactive. Deux assertions ont ete **traduites** dans le DOM produit apres
conversion (voir `Web:S6819`), a garantie constante.

## Lots de la passe de cloture

### `java:S1192` — 1 occurrence

Constante `CAUSE_PARAM` dans `AbstractStreamConfluenceScanUseCase`, substituee aux 3 litteraux
`"cause"` du fichier. Perimetre limite au fichier signale : les 4 autres emplacements du meme
litteral, dans d'autres fichiers, sont sous le seuil de la regle et n'ont pas ete touches.

### `java:S8688` — 7 occurrences

Zone explicite sur chaque `LocalDateTime.now()`. La zone n'a pas ete choisie par defaut mais
adossee, pour chaque fichier, a un lecteur reel de la valeur ecrite :

| Fichier | Zone | Justification |
|---------|------|---------------|
| `ScanCheckpointService:253`, `ScanCheckpointPersistenceAdapter:39` | `ZoneId.systemDefault()` | `FetchSpaceUpdateInfoUseCase:138` relit cette colonne via `atZone(systemDefault())`. En UTC, l'info « derniere mise a jour d'espace » aurait derive de 1 a 2 heures. |
| `ConfluencePageMapper:109,114` | `ZoneOffset.UTC` | Repli de `parseDateTime`, dont la valeur nominale vient d'une date Confluence parsee en UTC. |
| `PiiTypeConfigEntity:78,79,87` | `ZoneOffset.UTC` | Convention du package : `PiiDetectionConfigPersistenceAdapter` ecrit deja son `updatedAt` en UTC. |

`ZoneOffset.UTC` a ete prefere a `ZoneId.of("UTC")` du code voisin : strictement equivalent, et sans
introduire un litteral `"UTC"` repete 3 fois, qui aurait leve un nouveau `java:S1192`.

### `java:S107` — 1 occurrence

`ScanDetectorStatsJpaRepository.accumulate` passe de 9 parametres a 3
(`scanId`, `spaceKey`, `ScanDetectorStatDelta`). L'UPSERT a ete deplace dans un fragment de
repository (`ScanDetectorStatsUpsert` / `...Impl`), avec le SQL repris **verbatim** et une liaison
de parametres en Java. Le `@Modifying(clearAutomatically = true)` est reproduit par un
`entityManager.clear()` explicite.

Le SpEL (`:#{#delta.busyMs()}` repete 9 fois) a ete ecarte : il aurait degrade la lisibilite plus
que les 9 parametres, et rien ne l'aurait verifie avant la production.

Cet UPSERT n'etait couvert par aucun test d'integration. Un test Testcontainers
(`ScanDetectorStatsUpsertIntegrationTest`, 3 cas : somme des compteurs, conservation de la premiere
erreur, absence d'erreur) a donc ete ecrit **d'abord contre la signature a 9 parametres** et
valide vert, puis rejoue a l'identique sur la nouvelle signature. C'est la preuve que le
refactoring ne change pas le comportement, et non une simple presomption.

### `python:S5332` — 2 occurrences

Le protocole n'est plus code en dur : `LLM_MINISTRAL_SCHEME`, defaut `http`, remplace le
`http://` litteral de `_resolve_base_url`.

Le comportement par defaut est inchange — le test existant qui attend `http://myhost:4000/v1`
passe sans modification. Conditionner le schema sur l'adresse loopback avait ete envisage puis
ecarte : cela aurait casse le deploiement ou le detector tourne en conteneur et LM Studio sur
l'hote, cas ou l'hote n'est pas loopback et ne sert pas TLS.

Le trou de securite reel est ferme : un deploiement qui atteint le serveur de modele a travers un
reseau peut exiger TLS sans patcher le code, au lieu d'y envoyer en clair le contenu scanne.

### `Web:ItemTagNotWithinContainerTagCheck` — 1 occurrence

Le `<div class="scan-stats-row">` intermediaire est supprime : le `<dt>` devient enfant direct du
`<dl>`. La mise en page flex est reportee sur `.scan-stats-summary`, rendu identique — le `<dl>` ne
contient qu'une paire. Aucun test ne ciblait ces selecteurs.

### `Web:S6819` — 6 occurrences

Quatre conversions vers la balise native : `role="region"` → `<section>` (barre d'actions groupees),
`role="status"` → `<output>` (progression de tache, banniere d'entree, panneau desactive). Toutes les
classes concernees declarent un `display` explicite, donc la mise en page est inchangee.

Les deux `role="group"` sur les controles segmentes deviennent des `<fieldset>` — le conteneur natif
d'un groupe de controles de formulaire, ici des `<button>` porteurs de `aria-pressed`. Les styles
par defaut de `<fieldset>` (`margin-inline`, `padding`, `min-inline-size`) sont neutralises sur
`.ob-segmented`.

**Les deux assertions qui bloquaient ce lot a la passe precedente ont ete traduites, pas affaiblies :**
`expect(el.getAttribute('role')).toBe('status')` devient `expect(el.tagName).toBe('OUTPUT')`.
`<output>` porte `role="status"` de facon implicite : la garantie verifiee — la zone est annoncee
comme statut aux lecteurs d'ecran — est exactement la meme.

### `Web:MouseEventWithoutKeyboardEquivalentCheck` — 6 occurrences

L'escalade de la passe precedente etait **factuellement correcte** et a ete verifiee dans un vrai
navigateur avant correction : sur un `<button>` natif, un seul appui sur Entree compte deux
activations si un gestionnaire `keydown` est ajoute a cote du `(click)`, parce que le navigateur
emet en plus un `click`. Mesure : 1 appui → compteur a 2.

La correction ajoute donc `preventDefault()`, ce qui supprime le `click` natif. Mesure sur la meme
page : Entree → 1, Espace → 1, clic souris → 1. Exactement une activation par interaction.

```html
(click)="nextPage()"
(keydown.enter)="$event.preventDefault(); nextPage()"
(keydown.space)="$event.preventDefault(); nextPage()"
```

C'est ce qui rend le lot applicable a `prevPage()`, `nextPage()`, `toggleSortOrder()` et
`sortMenu.toggle()`, dont un double appel aurait saute une page ou annule le basculement.

## A l'attention du relecteur

**Un bug d'accessibilite preexistant, hors perimetre, a ete trouve pendant la mesure ci-dessus.**
`space-scan-stats-popover.component.html:4` porte `(keydown.enter)="toggle($event)"` **sans**
`preventDefault()`, a cote du `(click)="toggle($event)"`. Le popover est donc ouvert puis
immediatement referme a chaque activation par Entree : il est inutilisable au clavier.

Aucune issue Sonar ne cible ce fichier pour cette regle, et le RUNBOOK interdit d'elargir un lot :
il n'a pas ete corrige. Le correctif est d'une ligne, identique au lot ci-dessus.

## Couverture du detector remontee au-dessus du seuil

Le quality gate du detector etait rouge sur `new_coverage` (79,3 % pour un seuil de 80 %), a cause
d'une dette de couverture ancienne. Deux services ont ete couverts pour la passer — choisis pour
leur valeur, pas pour leur volume de lignes.

| Metrique | Avant | Apres |
|----------|-------|-------|
| `coverage` (projet) | 78,9 % | **80,3 %** |
| `new_coverage` (gate) | 79,3 % ⛔ | **80,2 %** ✅ |
| Quality gate | ERROR | **OK** |
| Tests | 630 | **670** |

### `detector_worker_pool.py` — 35 % → 100 % (21 tests)

Le pool de processus qui parallelise `DetectPII` n'avait **aucun test**, alors que ses garanties
sont operationnelles et silencieuses en cas d'erreur. Ce qui est desormais verrouille :

- `pool_size_from_env` : 1 worker vaut « pool desactive », et une valeur non entiere
  (`PII_WORKER_PROCESSES=four`) ne doit pas empecher le service de demarrer ;
- `_worker_detect_with_stats` : repli documente sur `detect_pii` avec des stats vides quand le
  detecteur ne connait pas les stats par detecteur ;
- `_worker_init` : les compteurs de threads sont poses, le warmup a lieu **dans le worker**, et un
  warmup en echec laisse le worker utilisable ;
- le demarrage prend `fork` quand la plateforme l'offre — c'est l'heritage copy-on-write des poids
  prechauffes qui rend le pool abordable — et retombe sur `spawn` sinon ;
- `warm_up` soumet exactement une tache par worker, sinon un worker pourrait encore s'initialiser
  quand le serveur s'annonce pret.

Aucun processus reel n'est demarre : `multiprocessing` est double, la suite reste rapide.

### `detection_policy.py` — 79 % → 95 % (18 tests)

Ce module decide quels modeles LLM tournent et d'ou vient chaque valeur par defaut de detection.
Une erreur ici ne casse rien : elle change silencieusement le comportement de detection.

- `get_enabled_models` ecarte les detecteurs non-LLM, rejette une entree sans `model_id` (un TOML de
  patterns n'est pas un modele), applique la priorite 999 par defaut et trie par priorite croissante
  — le premier de la liste devient le modele primaire ;
- `DetectionConfig` ne remplit que les attributs laisses a `None`, et un seuil declare par le modele
  **prime** sur le defaut global ;
- un fichier de configuration absent ou une cle manquante remonte comme une erreur exploitable, avec
  la structure attendue ou le nom de la cle, au lieu d'un `KeyError` brut.

Les 4 lignes restantes sont le repli d'import `tomli` (Python < 3.11) et deux gardes de demarrage
sur l'arborescence `config/models/`.

Les deux autres projets sont au vert : API 86,3 %, UI 82,6 %.

# Runbook — nettoyage SonarQube en autonomie

Protocole obligatoire pour traiter [BACKLOG.md](BACKLOG.md). Objectif : ramener les 178 issues
a zero **sans qu'un seul test qui passait ne se mette a echouer**, sur l'etat actuel de la
branche de travail, pour pouvoir en tirer une release.

L'agent `sonar-fixer` (`.claude/agents/sonar-fixer.md`) execute ce runbook.

---

## Le contexte qui change tout

L'analyse Sonar est publiee sous le nom de branche `main`, mais elle ne decrit **pas** le commit
`main` : elle decrit la branche de travail `chore/improve-error-handling-with-sonar-fixes`, dont le contenu est
desormais fige dans le commit **`abf6f721`**. Verifie : l'issue `java:S8491` pointe
`ContentScanOrchestrator.java:179`, une ligne qui n'existe pas sur `main` (le fichier y fait
moins de 176 lignes) mais qui est bien la sur la branche de travail.

**Consequence** : partir de `chore/improve-error-handling-with-sonar-fixes`, jamais de `main`. Sur cette base les
numeros de ligne sont exacts — verifie sur `ContentScanOrchestrator.java:179` et
`pii_service.py:190`.

Ils cessent de l'etre des ton premier commit : chaque correction decale les lignes du fichier
touche. Ne jamais editer une ligne sans l'avoir relue. Si elle ne correspond pas au message de
l'issue, chercher le motif ailleurs dans le fichier ; introuvable, marquer `[!]` et passer.

## Prerequis, une seule fois

L'etape finale re-scanne les trois modules : **le serveur SonarQube local doit rester allume
toute la nuit** et `SONAR_TOKEN` doit etre exporte dans l'environnement de l'agent (il est
defini dans `~/.zshrc`, que le shell non interactif ne charge pas tout seul). A verifier avant
de lancer :

```bash
curl -sf -o /dev/null http://localhost:9000/api/system/status && echo "sonar OK"
```

Serveur injoignable ou token absent : le travail de correction reste possible, mais la
verification finale ne l'est pas. Le signaler dans le rapport plutot que de conclure a tort.

```bash
git switch chore/improve-error-handling && git switch -c chore/sonar-cleanup
```

Le travail d'amelioration de la gestion d'erreur est **la base a corriger**, pas un obstacle a
ecarter : c'est de ce code, une fois nettoye, que sortira la release. La branche de nettoyage part
donc de la branche de travail.

Avant de commencer, `git status` ne doit montrer **aucune modification en attente sous
`pii-reporting-api/`, `pii-detector-service/` ou `pii-reporting-ui/`** : sans cela, `git restore .`
lors d'un rollback detruirait du travail qui n'est pas le tien. Les fichiers non suivis hors de
ces trois repertoires ne genent pas.

La reference anti-regression est deja enregistree dans `qa/sonar/baseline/`. La regenerer
uniquement si la base de depart change :

```bash
qa/sonar/baseline.sh record
```

Cela ecrit dans `qa/sonar/baseline/` la liste nominative des tests en echec de chaque suite.

Reference enregistree le 2026-08-30 sur cette branche : **2908 tests, aucun echec**
(API 1657, detector 630, UI 621). Le point de depart est donc entierement vert, et le moindre
test rouge apres une correction est une regression causee par cette correction — il n'y a aucun
echec preexistant derriere lequel se cacher.

Le critere reste malgre tout « les memes echecs qu'avant » et non « tout vert » : si un test
devient instable en cours de nuit, `check` le signalera comme nouveau, ce qui est le
comportement voulu.

## La boucle, un lot a la fois

Un lot = une regle Sonar = un commit. Jamais deux lots dans le meme commit.

1. **Lire le lot** dans BACKLOG.md : la ligne **Correction** dit quoi faire. Si elle commence par
   `GARDE`, la lire jusqu'au bout avant d'ecrire quoi que ce soit.
2. **Corriger toutes les occurrences du lot**, et rien d'autre. Aucune amelioration opportuniste
   du code adjacent, aucun renommage, aucun reformatage.
3. **Verifier** — uniquement les suites des modules touches par le lot :
   ```bash
   qa/sonar/baseline.sh check api        # ou detector, ou ui, ou sans argument pour les trois
   ```
4. **Si `check` sort en erreur** : annuler le lot entier, marquer les issues du lot `[!]` avec la
   raison, passer au lot suivant. Ne pas tenter de reparer le test.
   ```bash
   git restore pii-reporting-api pii-detector-service pii-reporting-ui
   ```
   Restreint aux trois modules, pour ne pas effacer au passage l'avancement note dans
   `qa/sonar/BACKLOG.md`.
5. **Si `check` est vert** : commiter le lot seul.
   ```bash
   git add -A && git commit -m "fix(sonar): <regle> — <libelle court> (<n> occurrences)"
   ```
6. **Cocher** les cases `[x]` du lot dans BACKLOG.md et passer le statut de la vague a 🔄.

Les vagues s'executent dans l'ordre **A, B, C, D**. Une vague ne commence que lorsque la
precedente est terminee et verifiee.

## Les trois interdits

1. **Ne jamais affaiblir un test pour le faire passer.** Supprimer une assertion, elargir un
   matcher, ajouter un `skip`, augmenter un timeout, remplacer une valeur attendue par celle
   qui sort : tout cela est interdit sans exception. Un test qui devient rouge est une
   information, pas un obstacle.
2. **Ne jamais toucher a la configuration Sonar** pour faire disparaitre une issue : ni
   `sonar-project.properties`, ni exclusion, ni `# NOSONAR`, ni `@SuppressWarnings` ajoute pour
   l'occasion, ni changement de statut de l'issue cote serveur.
3. **Ne jamais elargir le perimetre d'un lot.** Une issue non listee dans BACKLOG.md n'est pas
   a corriger, meme si elle saute aux yeux dans le fichier ouvert.

## Escalades

Marquer `[!]` et **passer au suivant** — ne jamais bloquer la nuit sur un cas — dans ces
situations :

- La ligne indiquee ne correspond pas au message de l'issue et le motif est introuvable.
- Le `check` echoue apres la correction du lot (le lot est alors annule en entier).
- La correction obligerait a changer une signature publique appelee ailleurs.
- Une garde de lot dit explicitement d'escalader.
- **Un test sans assertion (`typescript:S2699`) devient rouge une fois l'assertion ajoutee.**
  C'est le cas le plus important : cela signifie qu'un bug de production etait masque par un
  test qui ne verifiait rien. Conserver l'assertion correcte, laisser le test rouge, marquer
  `[!]` avec la mention `BUG REVELE`, et annuler le commit du lot.

Chaque `[!]` est reporte dans la section « Escalades » du rapport final avec une phrase de raison.

## Fin de nuit

```bash
qa/sonar/baseline.sh check                        # les trois suites, doit etre vert
scripts/sonar.sh api && scripts/sonar.sh detector && scripts/sonar.sh ui
python3 qa/sonar/refresh-backlog.py               # re-genere BACKLOG.md depuis le serveur
```

Le re-scan est le seul arbitre. Les cases cochees decrivent ce que l'agent **croit** avoir fait ;
le compteur d'issues du backlog regenere dit ce qui est **reellement** corrige. Si les deux
divergent, c'est le re-scan qui a raison.

Ecrire enfin `qa/sonar/RAPPORT.md` :

- nombre d'issues avant / apres, par vague ;
- liste des commits produits, un par lot ;
- **toutes les escalades `[!]`**, avec leur raison, les `BUG REVELE` en premier ;
- les lots annules par un `check` rouge et le test qui a saute ;
- l'etat des trois suites en fin de course, compare au baseline.

# LLM-as-judge benchmark — post-filtrage de précision

Quel modèle exécutable localement décide le mieux si une valeur `XXX` est
réellement une instance de l'entité nommée `YYY` ?

C'est la question du **post-filtre de précision** d'ai-sentinel : les extracteurs
amont (Ministral-3B-PII, Presidio, Regex) produisent des findings, le
`FormatPostfilterValidator` en élimine déjà les impossibles par checksum, et il
reste les faux positifs *sémantiques* — ceux qu'aucune règle déterministe ne peut
trancher. Un juge LLM est le seul candidat crédible pour ce résiduel.

## Ce qui est mesuré

Chaque modèle répond à la même question sur les **mêmes 278 findings**, avec le
**même prompt**. L'écart observé est donc celui du modèle, pas du prompt. Les
corps de prompt par type (le design `v7` qui a existé dans ce dépôt) sont un
levier orthogonal, volontairement hors périmètre ici.

La sémantique de production dicte les métriques. `FormatPostfilterValidator`
conserve une entité sauf rejet positif, donc :

| Verdict du juge | Effet |
|---|---|
| `FALSE_POSITIVE` | l'entité est **supprimée** |
| `TRUE_POSITIVE`, `UNSURE`, `UNPARSEABLE`, `ERROR` | l'entité est **conservée** (fail-open) |

D'où les deux nombres qui comptent, et leur asymétrie :

- **Rétention TP** — part des vrais findings encore remontés. Chaque point perdu
  est une PII réelle qui passe sous les radars : c'est le mode de défaillance
  que tout le système existe pour empêcher.
- **Élimination FP** — part des findings parasites supprimés. C'est la valeur
  ajoutée du juge ; sans elle, le juge est un no-op très coûteux.

`utility@λ=10` chiffre l'asymétrie : `(FP supprimés − 10 × TP détruits) / nb FP`.
Un score négatif signifie que le juge fait plus de mal que de bien. Le facteur 10
reprend la règle « rappel sacré » que la génération de prompts précédente
encodait déjà.

## Le golden set

`golden/judge_golden.jsonl` — **278 findings, 47 `pii_type`, 139 TP / 139 FP**,
parfaitement équilibré par type.

- **Vocabulaire réel.** Les types viennent des lignes `MINISTRAL` de
  `pii-reporting-api/src/main/resources/data.sql`, avec leur `category` et leur
  niveau de sensibilité. Les huit catégories de production sont couvertes
  (`IDENTITY`, `CONTACT`, `GOV_ID`, `MEDICAL`, `FINANCIAL`, `EMPLOYMENT`,
  `DIGITAL`, `TEMPORAL`), y compris les catégories particulières de l'art. 9
  RGPD / art. 5 let. c nLPD : conviction religieuse, opinion politique,
  orientation sexuelle, origine ethnique.
- **Périmètre complet, pas un échantillon.** `lessons.md` documente qu'un eval de
  juge réduit à 3 types avait produit un delta de 2 findings et une
  recommandation inexploitable.
- **Réalité suisse et multilingue.** Le corpus scanné est un Confluence en
  français, allemand et anglais, avec des formats suisses : NAVS13 `756.…`,
  IBAN CH, IDE `CHE-…`, plaques cantonales, NPA à 4 chiffres.

### Axes de difficulté

| Axe | Ce qu'il teste |
|---|---|
| `canonical_with_clue` | valeur au bon format, contexte explicite |
| `canonical_no_clue` | valeur nue, aucun mot-clé — **le piège du rappel** |
| `competing_type` | le contexte désigne une autre entité (suivi, port, version, ticket, hash) |
| `label_not_value` | la valeur est le *nom* du champ, pas son contenu |
| `placeholder` / `variable_reference` / `masked_value` | `YOUR_API_KEY_HERE`, `${DB_PASSWORD}`, `********` |
| `deterministic_control` | déjà tranché par checksum en amont — mesure la non-régression, pas la valeur ajoutée |

Invariant vérifié sur chaque enregistrement : `document_text[start:end] == value`.

## Exécution

```bash
PY=../../pii-detector-service/.venv/bin/python

$PY build_golden_set.py                                  # golden/judge_golden.jsonl
$PY run_benchmark.py --model openai/gpt-oss-120b         # jeu complet (278)
$PY run_benchmark.py --model qwen/qwen3.8-27b --per-type 1   # sous-ensemble équilibré (94)
$PY encoder_judge.py --model ettin                       # encodeur, hors LM Studio
$PY openjev_judge.py --limit 2                           # smoke test OpenJev/SemIf
$PY score.py                                             # results/scores.md
```

`run_campaign.sh` enchaîne la campagne complète dans l'ordre de débit mesuré.

### Campagne « un prompt générique ou un par type ? »

```bash
$PY build_golden_set.py --heldout       # golden/judge_heldout.jsonl (confirmation)
$PY check_no_leak.py                    # corps indépendants du jeu de test, tronc = v2
./run_prompt_strategy.sh                # les 5 bras + confirmation + scoring
```

Le protocole est dans [`PLAN-prompt-strategy.md`](PLAN-prompt-strategy.md). Un
prompt est soit une chaîne (`v1`, `v2`), soit un TOML de `prompts/` qui insère
un corps propre au `pii_type` ou à la `category` entre les deux moitiés de v2 :
`--prompt per_type`, `per_category`, `per_type_fallback`. `--tag` distingue deux
exécutions du même prompt (le plancher de bruit), `--golden` pointe le jeu de
confirmation, et `--keep-loaded` juge avec ce que LM Studio sert déjà, au lieu
de recharger 63 Go entre chaque bras.

Le runner décharge explicitement tous les modèles avant chaque chargement : un
MoE de 96 Go ne cohabite avec rien sur une machine de 128 Go, et laisser LM
Studio arbitrer en JIT donne des chargements non reproductibles. Chaque jugement
est écrit au fil de l'eau, donc un `--resume` reprend exactement où le run
précédent s'est arrêté.

## Modèles lents et sous-ensemble

Un 31B dense « thinking » consomme ~90 s par verdict sur cette machine, soit plus
de deux heures pour les 278 findings. Ces modèles sont donc évalués sur le
sous-ensemble équilibré `--per-type 1` : 94 findings couvrant **les 47 types**
avec l'équilibre TP/FP intact. La taille d'échantillon effective figure dans la
colonne « Couverture » du rapport — ce n'est pas une troncature silencieuse.

## Cas particulier : l'encodeur Ettin

`kalyan-ks/ettin-68m-nemotron-pii` est un classifieur de tokens ModernBERT :
LM Studio ne sert que des modèles génératifs, il tourne donc via `transformers`
sur MPS (`ettin_judge.py`). Son intérêt est structurel : son vocabulaire de 56
labels **est** celui de NVIDIA Nemotron-PII, c'est-à-dire celui que stocke déjà
`pii_type_config.detector_label` pour le détecteur MINISTRAL. C'est un second
avis exprimé dans l'espace de labels du pipeline, pour 68 M de paramètres.

Un tagger ne répond pas « cette valeur est-elle de ce type » mais « qu'y a-t-il
ici ». L'adaptation ré-étiquette le document et compare ; quand le type revendiqué
est absent du vocabulaire (IBAN, CLIENT_SECRET, PASSPORT_NUMBER, SALARY…), le
modèle **s'abstient** (`UNSURE`) au lieu de rejeter. Sa carte de modèle annonce
l'anglais seul, alors que ce golden set est à 76 % français — l'écart est mesuré,
pas supposé.

## Cas particulier : OpenJev/SemIf

`openjev_judge.py` charge une seule fois le modèle local Qwen3.5-4B épinglé, puis
fait noter les trois options `TRUE_POSITIVE`, `FALSE_POSITIVE` et `UNSURE` par
OpenJev/SemIf sur chaque finding. Il écrit le même JSONL que les autres moteurs,
reprend un run interrompu et apparaît donc dans `score.py`.

OpenJev n'utilise pas le prompt commun des LLM génératifs : sa ligne mesure le
juge complet « SemIf + Qwen3.5-4B », pas un effet de modèle isolé à prompt égal.

## Fichiers

| Fichier | Rôle |
|---|---|
| `build_golden_set.py` | source de vérité du dataset (cas rédigés + résolution des offsets) |
| `golden/judge_golden.jsonl` | le golden set généré |
| `heldout_cases_*.py`, `golden/judge_heldout.jsonl` | jeu de confirmation, rédigé sans accès aux corps de prompt |
| `run_benchmark.py` | registre des modèles, prompts du juge, pilote LM Studio |
| `prompts/*.toml` | corps de prompt par type et par catégorie, autour du tronc v2 |
| `check_no_leak.py` | garde-fou : corps indépendants du jeu de test, tronc identique à v2 |
| `encoder_judge.py` | chemins encodeurs `transformers` |
| `openjev_judge.py` | juge local OpenJev/SemIf avec Qwen3.5-4B |
| `score.py` | métriques, seuillage de confiance, cascade, comparaison par paires, `results/scores.md` |
| `run_campaign.sh` | enchaînement de la campagne « quel modèle ? » |
| `run_prompt_strategy.sh` | enchaînement de la campagne « quelle stratégie de prompt ? » |
| `results/*.jsonl` | un jugement par ligne, réutilisable sans relancer les modèles |

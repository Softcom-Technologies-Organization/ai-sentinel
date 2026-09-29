# Ministral-3B-PII — Q4_K_M contre Q8_0

Que perd-on, et que gagne-t-on, en servant le détecteur Ministral en Q4_K_M
plutôt qu'en Q8_0 ? Deux mesures : le débit de génération, et la qualité
d'extraction (précision, rappel, F1) sur des pages Confluence réelles.

## Ce qui est mesuré

Le détecteur sous test est la classe de production
`MinistralDetector` : même prompt système, même schéma JSON, même découpage
2048/410 tokens, même résolution de labels. Seul l'identifiant de modèle LM
Studio change. Un ré-implémentation aurait mesuré autre chose que ce qui tourne.

Chaque quantisation est chargée **seule** (`lms unload --all` puis `lms load`) :
LM Studio garde volontiers les deux en mémoire, et mesurer l'une pendant que
l'autre est résidente ne mesure ni l'une ni l'autre.

Deux passes par quantisation :

| Passe | Budget de génération | Ce qu'elle mesure |
|---|---|---|
| production | 2048 tokens (`MAX_TOKENS` du détecteur) | le comportement réellement déployé |
| `nocap` | 5500 tokens | la qualité du modèle sans le plafond |

La seconde passe existe parce que le plafond n'est pas neutre : quand le tableau
JSON est coupé au budget, il ne se referme jamais, `_parse_entity_array` rend une
liste vide, et le chunk entier est perdu. Un plafond trop bas est donc
indiscernable d'un modèle qui ne trouve rien — la passe `nocap` fait la part des
deux.

L'ordre des quantisations est inversé entre les deux passes (Q4 puis Q8, puis Q8
puis Q4) : sur cette machine le second run d'une campagne est systématiquement
plus lent (dérive thermique), donc un ordre fixe avantagerait toujours le même
modèle.

## Le jeu d'évaluation

Corpus et résultats vivent **hors du dépôt**
(`/Volumes/SanDisk/ai-sentinel-result-eval`) : ce sont des pages Confluence de
l'État de Vaud contenant des PII réelles.

* 37 pages, 508 741 caractères, 179 465 tokens, 123 chunks par passe ;
* référence : 1 291 valeurs distinctes confirmées PII et 99 confirmées non-PII,
  issues de `output/improved/verdicts.jsonl`.

Cette référence a été produite par les détecteurs **GLiNER, Regex et Presidio**
puis arbitrée par un juge LLM. Aucun de ses findings ne vient de Ministral :
aucune des deux quantisations n'a contribué à la vérité terrain qui les note.

Elle n'est en revanche **pas exhaustive** — elle ne couvre que les onze types
que ces détecteurs cherchaient. Deux conséquences, assumées :

1. le rappel est mesuré sur des PII *connues*, pas sur toutes les PII du corpus ;
2. tout ce que Ministral extrait en plus (noms, e-mails, villes, fonctions) est
   soumis à un juge (`judge_unlabelled.py`, gpt-oss-120b + prompt `v2`, la
   configuration retenue par la campagne `llm-judge-eval`) pour que la précision
   ait un sens.

Les deux quantisations affrontent la même référence et le même juge, donc l'écart
entre elles reste valide même là où les valeurs absolues sont des bornes.

## Appariement

Par **valeur normalisée à l'intérieur d'une page**, pas par offset : les findings
de référence portent les offsets du pipeline Java, dont le portage Python ne
reproduit que 81 % au caractère près, alors que 99,7 % des valeurs se retrouvent
verbatim. La normalisation retire casse et séparateurs, puis accepte l'inclusion
au-delà de six caractères, ce qui rattrape `756.1234.5678.97` contre
`7561234567897`. Même règle des deux côtés.

L'étiquette n'entre pas dans l'appariement : une PII remontée sous un label
voisin reste une PII remontée. L'accord d'étiquette est rapporté à part, via la
table d'équivalence `TYPE_EQUIVALENCE` de `score.py`.

## Exécution

```bash
PY=../../pii-detector-service/.venv/bin/python

$PY build_dataset.py                                              # dataset/
$PY run_extraction.py --quant q4_k_m --quant q8_0                 # passe production
$PY run_extraction.py --quant q8_0 --quant q4_k_m --max-tokens 5500 --tag nocap
$PY judge_unlabelled.py --run q4_k_m --run q8_0 --run q4_k_m__nocap --run q8_0__nocap
$PY score.py --run q4_k_m --run q8_0 --run q4_k_m__nocap --run q8_0__nocap
```

Le jugement reprend où il s'est arrêté (les `claim_id` déjà écrits sont sautés).

## Fichiers

| Fichier | Rôle |
|---|---|
| `html_text.py` | portage Python de `HtmlContentParser.cleanText` (Jsoup) |
| `build_dataset.py` | corpus nettoyé + référence POSITIVE / NEGATIVE |
| `matching.py` | normalisation et appariement des valeurs |
| `run_extraction.py` | pilote LM Studio, exécute le détecteur, mesure les tokens |
| `judge_unlabelled.py` | étiquette les extractions hors référence (gpt-oss-120b) |
| `score.py` | précision, rappel, F1, accord d'étiquette, débits |
| `RAPPORT.md` | résultats et conclusion |

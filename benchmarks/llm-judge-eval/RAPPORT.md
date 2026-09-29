# Quel LLM local pour juger « cette valeur est-elle vraiment une instance de cette entité ? »

Benchmark exécuté les 16–17 août 2026 sur **Apple M5 Max / 128 Go**, LM Studio +
`transformers`/MPS. **10 modèles candidats, 7 effectivement mesurés** (3 refusent
de se charger, § 11), sur 278 findings annotés couvrant 47 types d'entités.
Code, données et résultats bruts : [`benchmarks/llm-judge-eval/`](.) — tout est
reproductible.

---

## 1. Recommandation

**Le prompt compte plus que le modèle, et le seuil de confiance compte plus que
les deux.** Sur gpt-oss-120b, corriger trois garde-fous du prompt fait passer
l'utilité de **+0,09 à +0,54** — un gain qu'aucun changement de modèle n'égale.

### Configuration recommandée

> **gpt-oss-120b**, prompt **v2**, rejet honoré seulement si `confidence ≥ 0.95`,
> précédé d'une passe **Ministral-3B** en proposeur.
>
> **99,3 % de rétention des vrais positifs · 66,9 % des faux positifs éliminés**
> (1 seul vrai positif détruit sur 139) · ~3,9 h pour 10 000 findings.

### Les points d'exploitation utiles

Juge = gpt-oss-120b + prompt v2, mesuré sur les 278 findings :

| Configuration | Rétention TP | Élimination FP | utility@λ=10 | Part du trafic au LLM |
|---|---|---|---|---|
| **Ministral-3B → juge @ 0,95** | **99,3 %** | **66,9 %** | **+0,60** | 82,4 % |
| juge seul @ 0,95 | 98,6 % | 71,2 % | +0,57 | 100 % |
| juge seul, sans seuil | 97,1 % | 82,7 % | +0,54 | 100 % |
| **privacy-filter → juge** | **100,0 %** | 36,7 % | +0,37 | **29,1 %** |

Choisir selon la contrainte dominante : la première ligne pour le meilleur
compromis, la dernière si **aucune** perte de rappel n'est tolérable ou si le
budget d'inférence est serré.

### Trois enseignements

1. **Un extracteur n'est pas un juge.** Ministral-3B-PII, le détecteur déjà en
   production, détruit **100 des 139 vrais positifs** quand on lui demande de
   juger — 28,1 % de rétention, `utility −6,27`. Il élimine pourtant 92,8 % des
   faux positifs : il rejette presque tout. C'est la démonstration que la
   précision seule ne veut rien dire ici.
2. **Mais ce même modèle est le meilleur *proposeur*.** Son agressivité devient
   une qualité dès qu'un second avis la tempère : en cascade, la rétention passe
   de 28,1 % à 99,3 % en conservant 66,9 % d'élimination. C'est le résultat le
   plus contre-intuitif du benchmark — et il est presque gratuit, le modèle étant
   déjà chargé dans le pipeline.
3. **Les meilleurs juges bruts sont inexploitables en volume.** Gemma-4-31B-IT
   domine le classement de précision (utility +0,66) mais consomme 61 s par
   verdict : **44 h** pour les 10 000 findings d'un scan d'espace, contre 4 h
   pour gpt-oss-120b.

---

## 2. Pourquoi ce benchmark

ai-sentinel **a déjà eu** un post-filtre LLM-as-judge, retiré depuis. Le proto en
garde la trace :

```protobuf
// Field 8 (judge_status) and the JudgeStatus enum were removed with the
// LLM-as-judge post-filter; reserved so the tag/name are never reused.
reserved "judge_status";
```

Ce qui subsiste est le `FormatPostfilterValidator`, purement déterministe : il
rejette sur échec de checksum (IBAN mod-97, Luhn, AVS EAN-13, IDE mod-11, parse
IP/MAC) et sur une denylist d'artefacts techniques. Sûr par construction — et
structurellement aveugle aux faux positifs **sémantiques** : `password` comme nom
de champ, `${DB_PASSWORD}` comme référence de variable, un numéro de suivi DHL
groupé en 4×4 pris pour une carte bancaire, une « politique de sécurité » prise
pour une opinion politique.

C'est ce résiduel que doit trancher un juge. La question posée à chaque modèle
est donc unique et étroite : *la `value` extraite est-elle réellement une
instance du type `pii_type`, dans ce contexte ?*

---

## 3. Méthode

### La métrique découle de la sémantique de production

`FormatPostfilterValidator` conserve une entité **sauf rejet positif**. Le verdict
du juge se projette donc sur deux issues seulement :

| Verdict | Effet |
|---|---|
| `FALSE_POSITIVE` | l'entité est **supprimée** |
| `TRUE_POSITIVE`, `UNSURE`, `UNPARSEABLE`, `ERROR` | l'entité est **conservée** (fail-open) |

D'où deux nombres radicalement asymétriques :

- **Rétention TP** — part des vrais findings encore remontés. Chaque point perdu
  est une PII réelle qui échappe à la détection.
- **Élimination FP** — part des findings parasites supprimés. C'est la valeur
  ajoutée ; sans elle, le juge est un no-op coûteux.

`utility@λ=10 = (FP supprimés − 10 × TP détruits) / nb FP` chiffre l'arbitrage :
un faux positif supprimé vaut 1 point, un vrai positif détruit en coûte 10. **Un
score négatif signifie que le juge fait plus de mal que de bien.** Le facteur 10
reprend la règle « rappel sacré » que la génération de prompts précédente
encodait déjà (`llm-judge-prompts-per-type.toml`, v7).

### Le prompt est constant entre modèles

Tous reçoivent le même prompt système : verdict ternaire, `reason` généré **avant**
`verdict` pour que la conclusion découle des faits énoncés, interdiction de
recalculer un checksum, quatre motifs de rejet nommés. Sortie forcée par
`response_format: json_schema` strict, `temperature: 0`. L'écart mesuré est donc
celui du modèle — sauf dans la comparaison v1/v2 (§ 6), qui isole l'effet du
prompt à modèle constant.

### Le golden set

**278 findings, 47 `pii_type`, 139 TP / 139 FP**, équilibré par type.

- Vocabulaire tiré des lignes `MINISTRAL` de `data.sql` — les huit catégories de
  production, dont les catégories particulières art. 9 RGPD / art. 5 let. c
  nLPD : `RELIGIOUS_BELIEF`, `POLITICAL_VIEW`, `SEXUALITY`, `RACE_ETHNICITY`.
- Corpus réaliste : 76 % français, 13 % allemand, 11 % anglais, avec les formats
  suisses (NAVS13 `756.…`, IBAN CH, IDE `CHE-…`, plaques cantonales, NPA).
- Axes de difficulté explicites, dont `canonical_no_clue` (valeur nue, sans
  mot-clé — le piège du rappel) et `deterministic_control` (déjà tranché par
  checksum en amont : mesure la non-régression, pas la valeur ajoutée).

Le périmètre couvre volontairement **tout** le vocabulaire concerné plutôt qu'un
échantillon : `lessons.md` documente qu'un eval de juge réduit à 3 types avait
produit un delta de 2 findings et une recommandation inexploitable.

---

## 4. Résultats

<!--RESULTS:START-->
### Classement principal — sous-ensemble commun (1 findings jugés par tous les modèles)

`utility@λ=10` = (FP supprimés − 10 × TP détruits) / nb FP. Un score négatif signifie que le juge fait plus de mal que de bien.

| # | Modèle | Taille | Rétention TP | Élimination FP | utility@λ=10 | Youden J | MCC | Latence méd. | Échecs |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Gemma-4-31B heretic (abliterated) | 25.2 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 103.07 s | 0.0% |
| 2 | Gemma-4-31B-IT | 26.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 83.57 s | 0.0% |
| 3 | Qwen3.8-27B (thinking) | 30.0 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 67.34 s | 0.0% |
| 4 | GPT-OSS-120B (MoE, reasoning low) | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 8.09 s | 0.0% |
| 5 | GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 11.25 s | 0.0% |
| 6 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 8.21 s | 0.0% |
| 7 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 8.73 s | 0.0% |
| 8 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 8.53 s | 0.0% |
| 9 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 5.23 s | 0.0% |
| 10 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 63.4 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 4.06 s | 0.0% |
| 11 | Ettin-68M-Nemotron-PII (encoder) | 0.1 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 0.14 s | 0.0% |
| 12 | OpenJev/SemIf — Qwen3.5-4B | 8.7 Go | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 4.62 s | 0.0% |
| 13 | Ministral-3B-PII (incumbent) | 2.1 Go | 0.0% | 0.0% | +0.00 | -1.000 | +0.000 | 2.75 s | 0.0% |
| 14 | OpenAI Privacy-Filter 1.4B (encoder MoE) | 2.8 Go | 0.0% | 0.0% | +0.00 | -1.000 | +0.000 | 0.56 s | 0.0% |

### Classement sur le périmètre réellement couvert par chaque modèle

Les modèles trop lents pour les 278 findings sont mesurés sur le sous-ensemble équilibré de 94 (1 TP + 1 FP par type, les 47 types). La colonne « Couverture » donne la taille d'échantillon effective : un score établi sur 94 items n'a pas le même poids qu'un score sur 278.

| # | Modèle | Couverture | Rétention TP | Élimination FP | utility@λ=10 | Youden J | MCC | Latence méd. |
|---|---|---|---|---|---|---|---|---|
| 1 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 278/278 | 98.6% | 90.6% | +0.76 | +0.892 | +0.895 | 7.96 s |
| 2 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 278/278 | 97.8% | 91.4% | +0.70 | +0.892 | +0.894 | 7.72 s |
| 3 | Gemma-4-31B-IT | 94/278 | 97.9% | 87.2% | +0.66 | +0.851 | +0.856 | 61.29 s |
| 4 | Gemma-4-31B heretic (abliterated) | 102/278 | 98.1% | 78.0% | +0.58 | +0.761 | +0.779 | 57.95 s |
| 5 | GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 278/278 | 97.1% | 84.9% | +0.56 | +0.820 | +0.826 | 6.67 s |
| 6 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 278/278 | 96.4% | 88.5% | +0.53 | +0.849 | +0.852 | 8.09 s |
| 7 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 278/278 | 96.4% | 83.5% | +0.47 | +0.799 | +0.805 | 5.21 s |
| 8 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 278/278 | 95.7% | 82.7% | +0.40 | +0.784 | +0.791 | 5.41 s |
| 9 | Qwen3.8-27B (thinking) | 94/278 | 93.6% | 89.4% | +0.26 | +0.830 | +0.831 | 57.72 s |
| 10 | GPT-OSS-120B (MoE, reasoning low) | 278/278 | 92.1% | 88.5% | +0.09 | +0.806 | +0.806 | 6.36 s |
| 11 | OpenJev/SemIf — Qwen3.5-4B | 2/278 | 100.0% | 0.0% | +0.00 | +0.000 | +0.000 | 2.62 s |
| 12 | OpenAI Privacy-Filter 1.4B (encoder MoE) | 278/278 | 83.5% | 41.7% | -1.24 | +0.252 | +0.277 | 0.63 s |
| 13 | Ettin-68M-Nemotron-PII (encoder) | 278/278 | 64.7% | 68.3% | -2.84 | +0.331 | +0.331 | 0.03 s |
| 14 | Ministral-3B-PII (incumbent) | 278/278 | 28.1% | 92.8% | -6.27 | +0.209 | +0.274 | 1.19 s |

### Lecture opérationnelle

| Modèle | Couverture | TP détruits | FP supprimés | Précision du rejet | UNSURE | Troncatures | p95 | Tokens raisonnement |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 278/278 | 2/139 | 126/139 | 98.4% | 0.0% | 0.0% | 11.3 s | 50 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 278/278 | 3/139 | 127/139 | 97.7% | 0.4% | 0.0% | 11.1 s | 51 |
| Gemma-4-31B-IT | 94/278 | 1/47 | 41/47 | 97.6% | 0.0% | 0.0% | 85.2 s | 259 |
| Gemma-4-31B heretic (abliterated) | 102/278 | 1/52 | 39/50 | 97.5% | 0.0% | 2.9% | 103.1 s | 285 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 278/278 | 4/139 | 118/139 | 96.7% | 0.7% | 0.0% | 11.1 s | 57 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 278/278 | 5/139 | 123/139 | 96.1% | 1.1% | 0.0% | 12.2 s | 56 |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 278/278 | 5/139 | 116/139 | 95.9% | 1.1% | 0.0% | 9.1 s | 61 |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 278/278 | 6/139 | 115/139 | 95.0% | 1.4% | 0.0% | 9.3 s | 60 |
| Qwen3.8-27B (thinking) | 94/278 | 3/47 | 42/47 | 93.3% | 0.0% | 0.0% | 137.8 s | 285 |
| GPT-OSS-120B (MoE, reasoning low) | 278/278 | 11/139 | 123/139 | 91.8% | 0.0% | 0.0% | 9.2 s | 56 |
| OpenJev/SemIf — Qwen3.5-4B | 2/278 | 0/2 | 0/0 | n/a | 0.0% | 0.0% | 0.6 s | 0 |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 278/278 | 23/139 | 58/139 | 71.6% | 52.5% | 0.0% | 0.9 s | 0 |
| Ettin-68M-Nemotron-PII (encoder) | 278/278 | 49/139 | 95/139 | 66.0% | 17.3% | 0.0% | 0.0 s | 0 |
| Ministral-3B-PII (incumbent) | 278/278 | 100/139 | 129/139 | 56.3% | 0.0% | 0.0% | 2.8 s | 0 |

### Coût opérationnel projeté

Débit mesuré de bout en bout (chargement exclu), au parallélisme testé. Un scan d'espace Confluence produit couramment plusieurs milliers de findings : c'est ce volume qui décide de la faisabilité, pas la latence unitaire.

| Modèle | Empreinte | Débit mesuré | 1 000 findings | 10 000 findings |
|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 63.4 Go | 0.49 /s | 34 min | 5.7 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 63.4 Go | 0.50 /s | 33 min | 5.6 h |
| Gemma-4-31B-IT | 26.4 Go | 0.06 /s | 266 min | 44.3 h |
| Gemma-4-31B heretic (abliterated) | 25.2 Go | 0.06 /s | 258 min | 43.0 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 63.4 Go | 0.56 /s | 30 min | 4.9 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 63.4 Go | 0.48 /s | 35 min | 5.8 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 63.4 Go | 0.72 /s | 23 min | 3.9 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 63.4 Go | 0.65 /s | 26 min | 4.3 h |
| Qwen3.8-27B (thinking) | 30.0 Go | 0.05 /s | 319 min | 53.2 h |
| GPT-OSS-120B (MoE, reasoning low) | 63.4 Go | 0.62 /s | 27 min | 4.5 h |
| OpenJev/SemIf — Qwen3.5-4B | 8.7 Go | 0.38 /s | 44 min | 7.4 h |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 2.8 Go | 1.57 /s | 11 min | 1.8 h |
| Ettin-68M-Nemotron-PII (encoder) | 0.1 Go | 31.95 /s | 1 min | 0.1 h |
| Ministral-3B-PII (incumbent) | 2.1 Go | 4.34 /s | 4 min | 0.6 h |

### Par catégorie d'entité

| Modèle | CONTACT | DIGITAL | EMPLOYMENT | FINANCIAL | GOV_ID | IDENTITY | MEDICAL | TEMPORAL |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 100.0% / 66.7% | 100.0% / 90.0% | 100.0% / 91.7% | 100.0% / 90.0% | 95.0% / 90.0% | 100.0% / 100.0% | 87.5% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 100.0% / 66.7% | 100.0% / 93.3% | 100.0% / 91.7% | 95.0% / 90.0% | 95.0% / 90.0% | 100.0% / 100.0% | 87.5% / 100.0% | 100.0% / 100.0% |
| Gemma-4-31B-IT | 100.0% / 60.0% | 100.0% / 100.0% | 100.0% / 100.0% | 100.0% / 71.4% | 85.7% / 71.4% | 100.0% / 100.0% | 100.0% / 100.0% | 100.0% / 100.0% |
| Gemma-4-31B heretic (abliterated) | 100.0% / 60.0% | 100.0% / 69.2% | 100.0% / 100.0% | 100.0% / 71.4% | 85.7% / 57.1% | 100.0% / 100.0% | 100.0% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 100.0% / 66.7% | 100.0% / 80.0% | 100.0% / 83.3% | 100.0% / 75.0% | 85.0% / 85.0% | 100.0% / 100.0% | 87.5% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 100.0% / 66.7% | 100.0% / 86.7% | 100.0% / 91.7% | 90.0% / 90.0% | 90.0% / 85.0% | 100.0% / 96.8% | 87.5% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 100.0% / 66.7% | 100.0% / 76.7% | 100.0% / 83.3% | 95.0% / 75.0% | 80.0% / 90.0% | 100.0% / 96.8% | 100.0% / 87.5% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 100.0% / 73.3% | 100.0% / 83.3% | 100.0% / 83.3% | 95.0% / 70.0% | 80.0% / 80.0% | 100.0% / 93.5% | 87.5% / 87.5% | 100.0% / 100.0% |
| Qwen3.8-27B (thinking) | 100.0% / 80.0% | 100.0% / 90.0% | 100.0% / 100.0% | 100.0% / 85.7% | 57.1% / 71.4% | 100.0% / 100.0% | 100.0% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) | 100.0% / 66.7% | 100.0% / 96.7% | 100.0% / 91.7% | 95.0% / 85.0% | 65.0% / 90.0% | 96.8% / 90.3% | 87.5% / 87.5% | 66.7% / 100.0% |
| OpenJev/SemIf — Qwen3.5-4B | — | 100.0% / nan% | — | 100.0% / nan% | — | — | — | — |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 66.7% / 93.3% | 76.7% / 70.0% | 100.0% / 0.0% | 45.0% / 65.0% | 100.0% / 0.0% | 100.0% / 22.6% | 100.0% / 0.0% | 100.0% / 100.0% |
| Ettin-68M-Nemotron-PII (encoder) | 60.0% / 66.7% | 93.3% / 63.3% | 50.0% / 91.7% | 85.0% / 45.0% | 30.0% / 65.0% | 54.8% / 80.6% | 50.0% / 62.5% | 100.0% / 100.0% |
| Ministral-3B-PII (incumbent) | 40.0% / 100.0% | 46.7% / 66.7% | 33.3% / 100.0% | 10.0% / 100.0% | 25.0% / 100.0% | 9.7% / 100.0% | 50.0% / 100.0% | 33.3% / 100.0% |

_Format : rétention TP / élimination FP._

### Par axe de difficulté

| Modèle | canonical_no_clue | canonical_with_clue | competing_type | deterministic_control | label_not_value | masked_value | placeholder | variable_reference |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 100.0% | 98.2% | 91.6% | 100.0% / 66.7% | 97.4% | 100.0% | 75.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 100.0% | 97.3% | 91.6% | 100.0% / 66.7% | 100.0% | 100.0% | 75.0% | 100.0% |
| Gemma-4-31B-IT | — | 97.5% | 88.0% | 100.0% / 50.0% | 100.0% | — | — | — |
| Gemma-4-31B heretic (abliterated) | 100.0% | 97.7% | 80.8% | 100.0% / 33.3% | 93.8% | 0.0% | — | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 100.0% | 96.4% | 85.5% | 100.0% / 58.3% | 94.7% | 100.0% | 50.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 100.0% | 96.4% | 89.2% | 94.4% / 58.3% | 100.0% | 100.0% | 50.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 100.0% | 96.4% | 81.9% | 94.4% / 58.3% | 97.4% | 100.0% | 50.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 100.0% | 94.6% | 81.9% | 100.0% / 50.0% | 97.4% | 100.0% | 50.0% | 100.0% |
| Qwen3.8-27B (thinking) | — | 95.0% | 96.0% | 85.7% / 33.3% | 100.0% | — | — | — |
| GPT-OSS-120B (MoE, reasoning low) | 90.0% | 91.9% | 86.7% | 94.4% / 66.7% | 100.0% | 100.0% | 75.0% | 100.0% |
| OpenJev/SemIf — Qwen3.5-4B | — | 100.0% | — | 100.0% | — | — | — | — |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 70.0% | 87.4% | 38.6% | 66.7% / 16.7% | 50.0% | 100.0% | 75.0% | 100.0% |
| Ettin-68M-Nemotron-PII (encoder) | 60.0% | 65.8% | 72.3% | 61.1% / 50.0% | 71.1% | 100.0% | 25.0% | 0.0% |
| Ministral-3B-PII (incumbent) | 10.0% | 29.7% | 97.6% | 27.8% / 75.0% | 89.5% | 100.0% | 75.0% | 100.0% |

### Seuil de confiance exigé pour honorer un rejet

Le juge ne supprime l'entité que si sa `confidence` atteint le seuil. C'est le seul levier disponible une fois le modèle figé.

**GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun)**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 98.6% | 90.6% | +0.76 |
| ≥ 0.50 | 98.6% | 90.6% | +0.76 |
| ≥ 0.70 | 98.6% | 90.6% | +0.76 |
| ≥ 0.80 | 98.6% | 90.6% | +0.76 |
| ≥ 0.90 | 98.6% | 90.6% | +0.76 |
| ≥ 0.95 | 98.6% | 84.9% | +0.71 |
| ≥ 0.99 | 99.3% | 54.7% | +0.47 |
| ≥ 1.00 | 100.0% | 0.0% | +0.00 |

**GPT-OSS-120B (MoE, reasoning low) — prompt per_type**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 97.8% | 91.4% | +0.70 |
| ≥ 0.50 | 97.8% | 91.4% | +0.70 |
| ≥ 0.70 | 97.8% | 91.4% | +0.70 |
| ≥ 0.80 | 97.8% | 91.4% | +0.70 |
| ≥ 0.90 | 97.8% | 91.4% | +0.70 |
| ≥ 0.95 | 98.6% | 84.2% | +0.70 |
| ≥ 0.99 | 99.3% | 61.2% | +0.54 |
| ≥ 1.00 | 100.0% | 0.0% | +0.00 |

**Gemma-4-31B-IT**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 97.9% | 87.2% | +0.66 |
| ≥ 0.50 | 97.9% | 87.2% | +0.66 |
| ≥ 0.70 | 97.9% | 87.2% | +0.66 |
| ≥ 0.80 | 97.9% | 87.2% | +0.66 |
| ≥ 0.90 | 97.9% | 87.2% | +0.66 |
| ≥ 0.95 | 97.9% | 87.2% | +0.66 |
| ≥ 0.99 | 97.9% | 87.2% | +0.66 |
| ≥ 1.00 | 97.9% | 87.2% | +0.66 |

### Couverture du vocabulaire (juges encodeurs)

Un tagger ne peut pas se prononcer sur un type qu'il n'a jamais appris à émettre. La part hors vocabulaire borne sa valeur, quelle que soit sa qualité sur le reste.

| Encodeur | Findings jugeables | Hors vocabulaire | Rétention TP (dans le vocabulaire) | Élimination FP (dans le vocabulaire) |
|---|---|---|---|---|
| Ettin-68M-Nemotron-PII (encoder) | 230/278 | 17.3% | 57.4% | 82.6% |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 132/278 | 52.5% | 65.2% | 87.9% |

### Cascade encodeur → juge LLM (rejet à l'unanimité)

L'encodeur trie tout ; le LLM n'est appelé que sur les findings que l'encodeur propose de rejeter, et l'entité n'est supprimée que si les deux sont d'accord.

| Juge LLM | Findings communs | Part envoyée au LLM | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 278 | 51.8% | 98.6% | 63.3% | +0.49 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 278 | 51.8% | 98.6% | 64.0% | +0.50 |
| Gemma-4-31B-IT | 94 | 50.0% | 97.9% | 63.8% | +0.43 |
| Gemma-4-31B heretic (abliterated) | 102 | 48.0% | 98.1% | 54.0% | +0.34 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 278 | 51.8% | 97.8% | 61.9% | +0.40 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 278 | 51.8% | 98.6% | 62.6% | +0.48 |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 278 | 51.8% | 97.8% | 59.0% | +0.37 |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 278 | 51.8% | 96.4% | 60.4% | +0.24 |
| Qwen3.8-27B (thinking) | 94 | 50.0% | 93.6% | 66.0% | +0.02 |
| GPT-OSS-120B (MoE, reasoning low) | 278 | 51.8% | 95.0% | 64.0% | +0.14 |
| OpenAI Privacy-Filter 1.4B (encoder MoE) | 278 | 51.8% | 95.7% | 28.1% | -0.15 |
| Ministral-3B-PII (incumbent) | 278 | 51.8% | 72.7% | 65.5% | -2.08 |

### Pertes de rappel (TP détruits) du meilleur modèle

**GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun)** — 2 vrai(s) positif(s) perdu(s) :

| Type | Valeur | Axe | Motif invoqué |
|---|---|---|---|
| HEALTH_PLAN_BENEFICIARY_NUMBER | `CSS-4471902` | canonical_with_clue | Motif (b) TYPE CONCURRENT : le contexte indique explicitement qu'il s'agit d'un numéro de police d'assurance,  |
| UNIQUE_ID | `EGID-1928374` | canonical_with_clue | Le contexte indique qu'il s'agit d'un identifiant fédéral de bâtiment, donc un identifiant technique et non d' |

### Test-retest — plancher de bruit (GPT-OSS-120B (MoE, reasoning low))

Deux exécutions du **même** prompt à `temperature: 0`. LM Studio n'étant pas déterministe au bit près, l'écart mesuré ici borne par le bas tout écart entre bras : en dessous, on ne conclut pas.

| Prompt | Findings comparés | Verdicts discordants | Décisions de rejet discordantes (seuil 0.95) |
|---|---|---|---|
| v2 : « run 1 » vs « rerun » | 278 | 12 | 22 |
| per_type : « run 1 » vs « rerun » | 278 | 3 | 5 |

### Comparaison par paires — chaque bras contre le générique

Référence : **GPT-OSS-120B (MoE, reasoning low) — prompt v2**. Seuil de confiance 0.95 (le point d'exploitation retenu). Chaque finding est jugé par les deux bras, la comparaison est item par item.

| Bras | n | Rétention TP | Élimination FP | utility@λ=10 | ΔU | TP détruits / sauvés | FP éliminés en plus | McNemar (réf. juste / bras juste) | p | Temps mural |
|---|---|---|---|---|---|---|---|---|---|---|
| v2 (rerun) | 278 | 97.1% | 69.1% | +0.40 | -0.05 | −3 / +2 | +3 | 10 / 12 | 0.832 | × 1.10 |
| per_category | 278 | 98.6% | 79.9% | +0.65 | +0.20 | −1 / +2 | +18 | 6 / 25 | 0.001 | × 1.27 |
| per_type | 278 | 98.6% | 84.2% | +0.70 | +0.24 | −1 / +2 | +24 | 5 / 30 | 0.000 | × 1.44 |
| per_type (rerun) | 278 | 98.6% | 84.9% | +0.71 | +0.25 | −1 / +2 | +25 | 4 / 30 | 0.000 | × 1.46 |
| per_type_fallback | 278 | 97.8% | 80.6% | +0.59 | +0.14 | −2 / +2 | +19 | 7 / 26 | 0.001 | × 1.51 |
| v1 | 278 | 95.7% | 70.5% | +0.27 | -0.18 | −5 / +2 | +5 | 17 / 19 | 0.868 | × 1.16 |

_Référence elle-même : 96.4% de rétention TP, 83.5% d'élimination FP au seuil 0. « TP détruits / sauvés » se lit par rapport à elle : un bras peut détruire un vrai positif qu'elle conservait tout en en sauvant plusieurs qu'elle détruisait — la contrainte dure du § 9 ne regarde que le premier nombre._

### Contrainte dure — vrais positifs détruits par un bras et conservés par le générique

| Bras | Type | Valeur | Axe | Motif invoqué |
|---|---|---|---|---|
| v2 | BLOOD_TYPE | `A Rh-positiv` | canonical_with_clue | VIOLATION DE FORMAT : la valeur "A Rh‑positiv" ne correspond pas aux formats standards des groupes sanguins (e |
| v2 | NATIONAL_ID | `B-2024-889012` | canonical_with_clue | Rejeté car le contexte indique qu'il s'agit d'un numéro de permis, pas d'un identifiant national (type concurr |
| v2 | PASSPORT_NUMBER | `12AB34567` | canonical_with_clue | Violation de format : le numéro ne correspond pas au schéma officiel du passeport français (deux lettres suivi |
| per_category | HEALTH_PLAN_BENEFICIARY_NUMBER | `CSS-4471902` | canonical_with_clue | Le contexte indique qu'il s'agit d'un numéro de police/contrat d'assurance, pas du numéro d'assuré maladie (ty |
| per_type | HEALTH_PLAN_BENEFICIARY_NUMBER | `CSS-4471902` | canonical_with_clue | Rejeté car le contexte indique explicitement qu’il s’agit d’un numéro de police d’assurance (type concurrent), |
| per_type | HEALTH_PLAN_BENEFICIARY_NUMBER | `CSS-4471902` | canonical_with_clue | Motif (b) TYPE CONCURRENT : le contexte indique explicitement qu'il s'agit d'un numéro de police d'assurance,  |
| per_type_fallback | HEALTH_PLAN_BENEFICIARY_NUMBER | `CSS-4471902` | canonical_with_clue | Motif (b) TYPE CONCURRENT : le contexte indique qu'il s'agit d'un numéro de police d'assurance, pas du numéro  |
| per_type_fallback | PASSPORT_NUMBER | `12AB34567` | canonical_with_clue | Le numéro ne respecte pas le format officiel du passeport français (deux lettres suivies de sept chiffres) – i |
| v1 | DATE | `2026-03-12` | canonical_with_clue | TYPE CONCURRENT : le contexte 'created_at' indique qu'il s'agit d'une date de création du record, pas d'une do |
| v1 | NATIONAL_ID | `B-2024-889012` | canonical_with_clue | Type concurrent : le contexte indique qu'il s'agit d'un numéro de permis, pas d'un numéro d'identité nationale |
| v1 | PASSPORT_NUMBER | `12AB34567` | canonical_with_clue | VIOLATION DE FORMAT : la valeur "12AB34567" ne correspond pas au format attendu d'un numéro de passeport (géné |
| v1 | POLITICAL_VIEW | `SVP-Mitglied` | canonical_with_clue | TYPE CONCURRENT : le contexte indique une appartenance à un parti politique (SVP-Mitglied) et non une opinion  |
| v1 | TAX_ID | `CHE-116.281.710` | deterministic_control | VIOLATION DE FORMAT : la valeur ne respecte pas le format complet attendu pour un numéro fiscal suisse (UID) q |

**Un seul cas suffit à rejeter le bras concerné** (§ 9, règle 1) : un vrai positif perdu coûte dix faux positifs éliminés.

### Où chaque bras gagne et perd, par axe de difficulté

| Axe de difficulté | n | v2 (rerun) | per_category | per_type | per_type (rerun) | per_type_fallback | v1 |
|---|---|---|---|---|---|---|---|
| canonical_no_clue | 10 | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP |
| canonical_with_clue | 111 | +0 FP / -2 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / -1 TP | +0 FP / -3 TP |
| competing_type | 83 | +5 FP / +0 TP | +14 FP / +0 TP | +19 FP / +0 TP | +19 FP / +0 TP | +15 FP / +0 TP | +9 FP / +0 TP |
| deterministic_control | 30 | -2 FP / +1 TP | +0 FP / +1 TP | -1 FP / +1 TP | +0 FP / +1 TP | -1 FP / +1 TP | +0 FP / +0 TP |
| label_not_value | 38 | +0 FP / +0 TP | +4 FP / +0 TP | +5 FP / +0 TP | +5 FP / +0 TP | +5 FP / +0 TP | -5 FP / +0 TP |
| masked_value | 1 | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP |
| placeholder | 4 | +0 FP / +0 TP | +0 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +0 FP / +0 TP | +1 FP / +0 TP |
| variable_reference | 1 | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP | +0 FP / +0 TP |

_Format : faux positifs éliminés en plus (net) / vrais positifs conservés en plus (net), par rapport au générique. Un nombre négatif en TP est une régression de rappel._

### Où chaque bras gagne et perd, par catégorie

| Catégorie | n | v2 (rerun) | per_category | per_type | per_type (rerun) | per_type_fallback | v1 |
|---|---|---|---|---|---|---|---|
| CONTACT | 30 | +2 FP / +0 TP | +0 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +0 FP / +0 TP |
| DIGITAL | 60 | +1 FP / +0 TP | +3 FP / +0 TP | +6 FP / +0 TP | +4 FP / +0 TP | +3 FP / +0 TP | +4 FP / +0 TP |
| EMPLOYMENT | 24 | +0 FP / +0 TP | -2 FP / +0 TP | -1 FP / +0 TP | +1 FP / +0 TP | +0 FP / +0 TP | -2 FP / +0 TP |
| FINANCIAL | 40 | -1 FP / +1 TP | +0 FP / +1 TP | +3 FP / +1 TP | +3 FP / +1 TP | +3 FP / +1 TP | +0 FP / +1 TP |
| GOV_ID | 40 | -3 FP / -1 TP | +3 FP / +1 TP | +2 FP / +1 TP | +3 FP / +1 TP | +2 FP / +0 TP | -1 FP / -2 TP |
| IDENTITY | 62 | +3 FP / +0 TP | +9 FP / +0 TP | +8 FP / +0 TP | +8 FP / +0 TP | +6 FP / +0 TP | +1 FP / -1 TP |
| MEDICAL | 16 | +1 FP / -1 TP | +4 FP / -1 TP | +4 FP / -1 TP | +4 FP / -1 TP | +3 FP / -1 TP | +2 FP / +0 TP |
| TEMPORAL | 6 | +0 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +1 FP / +0 TP | +1 FP / -1 TP |

_Format : faux positifs éliminés en plus (net) / vrais positifs conservés en plus (net), par rapport au générique. Un nombre négatif en TP est une régression de rappel._

### Règle de décision (§ 9 du plan)

| Bras | TP détruit en plus ? | ΔU ≥ +0,07 ? | Temps mural ≤ 1,2 × A ? | Au-dessus du plancher de bruit ? | Issue sur ce jeu |
|---|---|---|---|---|---|
| per_category | oui | oui (+0.20) | non (× 1.27) | non (19 corrections nettes, plancher 22) | **rejeté** (contrainte dure) |
| per_type | oui | oui (+0.24) | non (× 1.44) | oui (25 corrections nettes, plancher 22) | **rejeté** (contrainte dure) |
| per_type (rerun) | oui | oui (+0.25) | non (× 1.46) | oui (26 corrections nettes, plancher 22) | **rejeté** (contrainte dure) |
| per_type_fallback | oui | oui (+0.14) | non (× 1.51) | non (19 corrections nettes, plancher 22) | **rejeté** (contrainte dure) |
| v1 | oui | non (-0.18) | oui (× 1.16) | non (2 corrections nettes, plancher 22) | **rejeté** (contrainte dure) |

La règle complète exige en plus ΔU ≥ 0 sur `judge_heldout.jsonl` : cette colonne se lit dans `scores_heldout.md`, produit par le même script sur l'autre jeu.

<!--RESULTS:END-->

---

## 5. Le dilemme précision / débit

C'est le résultat central, et il n'était pas prévisible depuis les fiches
techniques.

| Modèle | Débit mesuré | 10 000 findings | Rétention TP | Élimination FP |
|---|---|---|---|---|
| Ettin-68M (encodeur) | 31,95 /s | **6 min** | 64,7 % | 68,3 % |
| Ministral-3B | 4,34 /s | 38 min | 28,1 % | 92,8 % |
| privacy-filter 1,4B | 1,57 /s | 1,8 h | 83,5 % | 41,7 % |
| **gpt-oss-120b (v2)** | **0,70 /s** | **4,0 h** | **97,1 %** | **82,7 %** |
| Gemma-4-31B heretic | 0,06 /s | 43 h | 98,1 % | 78,0 % |
| Gemma-4-31B-IT | 0,06 /s | 44 h | 97,9 % | 87,2 % |
| Qwen3.8-27B | 0,05 /s | 53 h | 93,6 % | 89,4 % |

**Un MoE très creux écrase des denses 4× plus petits.** L'en-tête GGUF de
gpt-oss-120b donne la raison : `expert_count = 128`, `expert_used_count = 4` —
en MXFP4, il n'active qu'une fraction de ses 120 B à chaque token. Les 27–31 B
*denses* de Gemma et Qwen coûtent **10× plus par verdict** pour un gain de
précision de quelques points.

### Le raisonnement caché est un coût pur, et il ne se désactive pas

Un juge de post-filtre tranche une décision binaire sur une chaîne de 20
caractères : chaque token de raisonnement est du temps machine sans contrepartie.
Or `/no_think`, `chat_template_kwargs: {enable_thinking: false}` et
`reasoning_effort` sont tous **ignorés** par Qwen3.8-27B dans cette build de LM
Studio, et le préfixage d'un tour assistant `<think></think>` est rejeté en
HTTP 400.

La conséquence est un **piège de budget** : à `max_tokens: 600`, Qwen3.8-27B
consomme les 600 tokens en raisonnement et renvoie un canal de contenu **vide** —
zéro verdict, 25 s perdues par appel. Il faut lui accorder 4 096 tokens pour
qu'il conclue (166–260 tokens de raisonnement effectifs). Un budget dimensionné
pour une réponse directe ne mesure pas la qualité d'un modèle « thinking » : il
ne mesure que sa troncature. Chaque modèle reçoit donc ici un budget calibré sur
son comportement observé, et le taux de troncature est reporté.

gpt-oss-120b, à `reasoning_effort: low`, se contente de **56 tokens de
raisonnement en moyenne**. C'est la première raison de son avance opérationnelle.

### L'abliteration ne sert à rien ici

Le corpus contient des convictions religieuses, des opinions politiques et des
orientations sexuelles : on pouvait craindre qu'un modèle aligné refuse de les
traiter. La variante *abliterated* a donc été mesurée face au modèle standard sur
les **mêmes 94 findings**.

| | Rétention TP | Élimination FP | MCC | Échecs | Art. 9 (TP gardés · FP supprimés) |
|---|---|---|---|---|---|
| Gemma-4-31B-IT | 97,9 % | **87,2 %** | **+0,856** | 4,3 % | 4/4 · 4/4 |
| Gemma-4-31B heretic | 97,9 % | 78,7 % | +0,780 | 6,4 % | 4/4 · 4/4 |

**Aucun refus observé sur les catégories art. 9 avec le modèle standard** — les
deux variantes y sont parfaites. L'abliteration dégrade en revanche le jugement
global de 8,5 points d'élimination FP. Elle est inutile, et contre-productive,
pour cet usage.

---

## 6. Le prompt : le levier le plus rentable

Les 11 vrais positifs détruits par gpt-oss-120b en v1 ne sont pas aléatoires. Ils
se répartissent en trois familles, toutes corrigeables :

**(a) Le modèle invente une spécification de format qu'il ne connaît pas.** Sept
des onze pertes. Il rejette `VD 284917` et `GE 45012` (plaques vaudoise et
genevoise), `CHE-116.281.710` (IDE suisse), `80756123456789012345` (carte
d'assuré à 20 chiffres), `12AB34567` (passeport français), `FRK-9928-1174`
(permis de conduire), au motif « VIOLATION DE FORMAT ». Le prompt v1 lui
interdisait de recalculer un checksum, mais pas d'inventer une spec nationale.

**(b) Il confond « type plus précis » et « type concurrent ».** `SVP-Mitglied`
étiqueté `POLITICAL_VIEW` est rejeté parce que « le contexte indique une
appartenance à un parti politique » — ce qui *est* une opinion politique.
`created_at: 2026-03-12` cesse d'être une `DATE` parce que c'est une date de
création. Gemma-4-31B commet exactement la même erreur, sur le même item
(`EGID-1928374`) — c'est sa seule perte sur 47 vrais positifs.

**(c) Il applique le motif « pas une personne » à des types qui ne qualifient
personne.** Un code PIN rejeté parce qu'il « ne désigne pas une personne ».

Le prompt **v2** ajoute trois garde-fous ciblant exactement ces trois erreurs
(`PROMPTS["v2"]` dans `run_benchmark.py`). Effet, à modèle et golden set
identiques :

| Prompt | Rétention TP | Élimination FP | utility@λ=10 | MCC | TP détruits |
|---|---|---|---|---|---|
| v1 | 92,1 % | 88,5 % | +0,09 | +0,806 | 11 / 139 |
| **v2** | **97,1 %** | 82,7 % | **+0,54** | +0,807 | **4 / 139** |

**Sept des onze pertes récupérées, pour 932 caractères de prompt.** Le gain
(+0,45 d'utilité) dépasse l'écart entre gpt-oss-120b et le meilleur modèle du
benchmark. Les quatre pertes restantes relèvent toutes de la famille (b) — un
titre de séjour reste un `NATIONAL_ID`, un EGID reste un `UNIQUE_ID` — plus une
hallucination de format sur le passeport français.

### Générique ou un corps par type ?

_Campagne du 4 septembre 2026, protocole complet dans
[`PLAN-prompt-strategy.md`](PLAN-prompt-strategy.md)._

v2 conserve 24 faux positifs sur 139, dont 18 d'axe `competing_type` : le
générique n'a pas la place d'énumérer les entités concurrentes des 47 types,
un corps par type l'a. La question est de savoir si cette connaissance ciblée
paie assez pour justifier 119 corps à maintenir en production.

Cinq bras, un seul facteur qui varie — le prompt système :

| Bras | Prompt | Ce qu'il mesure |
|---|---|---|
| **A** | `v2`, inchangé | la référence, rejouée dans la session du jour |
| **A (rerun)** | `v2`, seconde exécution | le plancher de bruit : deux runs identiques à `temperature: 0` ne donnent pas les mêmes verdicts |
| **B** | `per_type` : 47 corps | la mesure principale |
| **C** | `per_category` : 8 corps | le compromis de maintenance |
| **B-repli** | `per_type` dont 16 types routés vers le corps neutre | ce que coûte un corps manquant, donc l'urgence d'écrire les 72 corps hors golden set |

Les corps sont insérés entre les deux moitiés de v2 : `check_no_leak.py` vérifie
avant chaque campagne que `prefix + suffix` redonne v2 caractère pour caractère,
et qu'aucune valeur du golden set n'apparaît dans les corps. Les corps ont été
écrits sans accès au golden set ; le jeu de confirmation `judge_heldout.jsonl`
(188 findings, 2 TP + 2 FP par type) a été écrit sans accès aux corps.

**Décision : A reste, le générique v2 ne change pas.** Deux motifs, chacun
suffisant selon le § 9 du plan.

**Motif 1, la contrainte dure.** `per_type` détruit un vrai positif que le
générique conserve, sur le jeu de développement (`CSS-4471902`, « Assurance
maladie CSS, police CSS-4471902 ») **et** sur le jeu de confirmation
(`KK-88-401337`, même situation en allemand). Ce n'est pas du bruit : les quatre
exécutions concernées rejettent avec une confiance de 0,97, et le motif est
toujours le même. Le corps de `HEALTH_PLAN_BENEFICIARY_NUMBER` nomme « numéro de
police ou de contrat d'assurance » parmi les types concurrents ; le juge applique
la consigne à la lettre. La consigne est fausse pour ce produit : le numéro de
police chez l'assureur *est* le numéro d'assuré maladie que le détecteur doit
signaler. Un corps, mal écrit, suffit à faire fuir une donnée de santé.

**Motif 2, le débit.** `per_type` met 9,3 min là où le générique met 6,5, soit
1,44 × le temps mural, au-delà du seuil de 1,2 × fixé avant la mesure. Sur
10 000 findings : 5,6 h contre 3,9 h.

**Ce que la mesure établit tout de même, et qui est solide.** Un corps par type
élimine beaucoup plus de faux positifs, de façon reproductible et statistiquement
nette :

| | Élimination FP (seuil 0,95) | FP éliminés en plus qu'A | McNemar | utility@λ=10 |
|---|---|---|---|---|
| A (générique) | 67 % | référence | — | +0,45 |
| B (47 corps) | 84 % | **+24** | 5 / 30, p < 0,001 | +0,70 |
| C (8 corps) | 80 % | +18 | 6 / 25, p = 0,001 | +0,65 |
| B-repli (31 corps sur 47) | 81 % | +19 | 7 / 26, p = 0,001 | +0,59 |

Le jeu de confirmation, écrit par des rédacteurs qui n'avaient pas vu les corps,
reproduit le gain : +10 faux positifs éliminés pour B (p = 0,052), +2 pour C. Le
second run de B donne les mêmes chiffres à une unité près. L'hypothèse H1 est
confirmée : le gain se concentre exactement là où il était attendu, l'axe
`competing_type` (+19 sur 83 items du jeu de développement, +10 sur 61 du jeu de
confirmation), et il n'y détruit aucun vrai positif.

**Et H2 est infirmée dans les grandes largeurs.** Le risque redouté était que des
corps par type ramènent les rejets abusifs de v1. C'est l'inverse : au seuil
brut, B retient 97,8 à 98,6 % des vrais positifs contre 95,7 à 96,4 % pour A. En
comparaison par paires, B détruit **1** vrai positif qu'A conservait mais en
**sauve 2** qu'A détruisait — bilan net de rappel positif. La règle du § 9 ne
regarde que le premier nombre, et c'est ce qui rejette B. Cette asymétrie est
délibérée (un vrai positif perdu coûte dix faux positifs), mais elle mérite
d'être rediscutée maintenant qu'on sait qu'elle peut écarter un prompt qui
conserve globalement plus de vraies PII que celui qu'elle protège.

**Le plancher de bruit est plus haut que prévu, et il vient du seuil.** Deux
exécutions identiques de v2 donnent 12 verdicts différents sur 278, mais **22
décisions de rejet différentes** au seuil 0,95 : dix cas de plus voient leur
confiance traverser 0,95 sans que le verdict change. Le point d'exploitation
retenu en août est donc posé exactement là où le modèle est le moins stable. B
est nettement plus reproductible que A sur ce point : 3 verdicts et 5 décisions
discordants entre ses deux exécutions. Un seuil à 0,90 ou 0,80 mérite d'être
réexaminé, indépendamment de la question des corps.

**Le débit ne suit pas la longueur du prompt, mais sa diversité.** C a un prompt
plus long que B (1 308 tokens contre 1 079) et tourne pourtant plus vite (0,56
contre 0,50 verdict/s). Avec 8 corps, le cache de préfixe de LM Studio sert la
plupart des requêtes ; avec 47, presque aucune. C'est l'explication de H3, et
elle change l'arbitrage : le coût d'un corps par type n'est pas ce qu'il ajoute
de texte, c'est le cache qu'il fait perdre.

**Un corps manquant coûte cher.** B-repli, avec 16 des 47 types routés vers le
corps neutre, perd la moitié du gain d'utilité de B (+0,14 contre +0,24) et
détruit un vrai positif de plus. Un déploiement partiel, avec seulement les corps
du golden set écrits et les 72 autres en repli, n'est donc pas une option
intermédiaire raisonnable.

**La suite, dans l'ordre de rentabilité.**

1. Corriger le corps de `HEALTH_PLAN_BENEFICIARY_NUMBER` (retirer le numéro de
   police de la liste des concurrents, et dire explicitement qu'il relève du
   type). C'est le seul défaut mesuré sur 47 corps. Attention : la correction se
   fait après avoir vu le jeu de test, donc la remesure exige un **troisième**
   jeu de findings, écrit à l'aveugle, sans quoi elle ne prouverait plus rien.
2. Si la contrainte dure tombe alors, C (8 corps) devient le candidat sérieux :
   il capture les trois quarts du gain de B, pour 8 textes à maintenir au lieu de
   119, et un débit à 1,27 × A sur le jeu de développement, 1,06 × sur le jeu de
   confirmation.
3. Indépendamment, les 19 faux positifs `competing_type` que B élimine et qu'A
   garde décrivent précisément ce qui manque au générique. Un « v3 » qui ajoute
   un garde-fou générique sur les types voisins — sans énumérer 47 vocabulaires —
   est la piste la moins coûteuse, et se mesure avec le même protocole.

Les tableaux complets sont dans le § 4 (« Test-retest », « Comparaison par
paires », « Contrainte dure », « Règle de décision ») et dans
[`results/scores_heldout.md`](results/scores_heldout.md) pour la confirmation.

**Limites de cette campagne**, à lire avant d'en tirer autre chose :

- le rédacteur des corps a pu lire l'analyse d'erreurs de v1 et v2 du § 6 de ce
  rapport, comme le plan l'autorise. Les corps de `NATIONAL_ID` et `UNIQUE_ID`
  répondent donc directement à deux pertes connues de v2 : sur ces deux types, le
  gain de B est en partie un ajustement, pas une découverte ;
- le jeu de confirmation a été écrit par des rédacteurs qui, eux, avaient lu le
  golden set. Il confirme que B généralise à de nouveaux items, pas qu'il
  généralise à une autre doctrine d'annotation ;
- 6 findings par type sur le jeu de développement, 4 sur celui de confirmation :
  les tableaux par type localisent, ils ne concluent pas ;
- le cas qui décide de tout, un numéro de police d'assurance, relève d'une
  frontière d'annotation discutable. Un autre annotateur aurait pu l'étiqueter
  faux positif, et la décision se serait inversée. C'est une raison de plus de
  documenter la doctrine du golden set plutôt que de la laisser implicite.

---

## 7. Le seuil de confiance : le levier le moins cher

gpt-oss-120b émet une confiance **discriminante**, ce qui donne un curseur
précision/rappel sans retoucher ni le modèle ni le prompt :

| Seuil exigé pour honorer un rejet | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| aucun | 97,1 % | 82,7 % | +0,54 |
| **≥ 0,95** | **98,6 %** | 71,2 % | **+0,57** |
| ≥ 0,99 | 99,3 % | 38,1 % | +0,31 |

Gemma-4-31B, à l'inverse, renvoie `confidence: 1.0` sur **90 verdicts sur 90** :
aucun levier de calibration n'existe. C'est un argument opérationnel de plus en
faveur de gpt-oss malgré son infériorité brute en précision.

---

## 8. Cascade : proposeur gratuit + juge cher

Le juge n'est appelé que sur les findings qu'un modèle bon marché propose de
rejeter — un `KEEP` étant déjà le défaut fail-open, il n'a pas besoin d'être
confirmé — et l'entité n'est supprimée que **si les deux sont d'accord**.

Mesuré sur les 278 findings, juge = gpt-oss-120b + prompt v2 :

| Proposeur | Seuil du juge | Part LLM | Rétention TP | Élimination FP | utility |
|---|---|---|---|---|---|
| aucun | — | 100 % | 97,1 % | 82,7 % | +0,54 |
| aucun | ≥ 0,95 | 100 % | 98,6 % | 71,2 % | +0,57 |
| Ettin-68M | ≥ 0,95 | 51,8 % | 98,6 % | 51,8 % | +0,37 |
| **privacy-filter** | — | **29,1 %** | **100,0 %** | 36,7 % | +0,37 |
| **Ministral-3B** | **≥ 0,95** | 82,4 % | **99,3 %** | **66,9 %** | **+0,60** |

- **`privacy-filter` → juge ne détruit aucun vrai positif** tout en supprimant
  plus d'un tiers des faux positifs, et divise par 3,4 le volume d'appels au LLM.
- **Ministral-3B → juge @ 0,95 est le meilleur compromis global** : 1 seul vrai
  positif perdu sur 139, deux tiers des faux positifs éliminés.

Cette forme — proposeur orienté rappel puis juge binaire par span candidat — est
celle que la littérature récente valide indépendamment (§ 10).

---

## 9. Les encodeurs spécialisés : rapides, mais plafonnés par leur vocabulaire

Aucun des deux ne peut être servi par LM Studio, qui ne sert que du génératif
GGUF/MLX. Les deux tournent via `transformers` sur MPS.

| Encodeur | Taille | Débit | Hors vocabulaire | Rétention TP | Élimination FP |
|---|---|---|---|---|---|
| `kalyan-ks/ettin-68m-nemotron-pii` | 0,14 Go | 31,95 /s | 17 % | 64,7 % | 68,3 % |
| `openai/privacy-filter` | 2,8 Go | 1,57 /s | **53 %** | 83,5 % | 41,7 % |

**Ettin-68M** a un atout structurel : son vocabulaire de 56 labels **est** celui
de NVIDIA Nemotron-PII, c'est-à-dire celui que stocke déjà
`pii_type_config.detector_label` pour le détecteur MINISTRAL (`religious_belief`,
`political_view`, `password`, `swift_bic`, `unique_id`…). C'est un second avis
exprimé dans l'espace de labels du pipeline, pour 68 M de paramètres et 6 min par
tranche de 10 000 findings. Sa carte annonce l'**anglais seul**, ce qui explique
en partie sa rétention de 64,7 % sur un golden set à 76 % francophone. Il n'est
convertible ni en GGUF ni en MLX : llama.cpp n'enregistre pas
`ModernBertForTokenClassification`, et `mlx-embeddings` n'a de branche que pour
les variantes MaskedLM et SequenceClassification.

**`openai/privacy-filter`** (Apache-2.0, MoE bidirectionnel 1,4 B / ~50 M actifs,
nativement MLX) est le meilleur système non-frontière de RedactionBench. Mais ses
8 catégories (`secret`, `account_number`, `private_person`, `private_email`,
`private_phone`, `private_address`, `private_url`, `private_date`) ne couvrent que
47 % du golden set — et **aucune catégorie art. 9 / nLPD** : ni religion, ni
opinion politique, ni orientation sexuelle, ni origine ethnique, ni santé. Il ne
peut donc jamais être le juge unique d'ai-sentinel. En proposeur de cascade, en
revanche, son abstention massive est précisément ce qui lui vaut 100 % de
rétention.

---

## 10. Ce que dit la littérature vérifiée

Une recherche documentaire adversariale a accompagné ce benchmark : 26 assertions
extraites de sources 2025-2026, chacune soumise à trois vérificateurs
indépendants chargés de la **réfuter**. 14 ont survécu.

**Confirmé :**

- L'architecture cascade « proposeur orienté rappel → juge LLM binaire par span
  candidat » atteint 0,958 macro F1 contre 0,767 pour le *même* LLM en extracteur
  direct ([arXiv:2606.18372](https://arxiv.org/abs/2606.18372)). Le proposeur
  sur-génère (P 0,714 / R 0,970), le juge restaure la précision (P 0,926 /
  R 0,978). C'est exactement la forme mesurée au § 8.
- La taille du juge ne compte que sur les cas **ambigus** : sur un challenge set
  d'homonymies, un juge 31 B ne perd que 0,03 F1 là où des juges 125 M / 4 B
  perdent 0,19 à 0,25.
- Le plafond d'accord **humain** dépend massivement du type : 89,4 % sur les
  entités obligatoires (dont les clés API), mais **47,7 % sur les entités
  contextuelles**, α de Krippendorff global 0,540
  ([arXiv:2606.18782](https://arxiv.org/abs/2606.18782)). Une part de l'écart
  résiduel des modèles est donc irréductible.
- Reproductibilité ≠ validité : Qwen3-8B `/no_think` a la meilleure fidélité
  test-retest des 21 juges évalués (0,992) **et** le pire biais de position
  (0,192, ~96× celui de Gemini 2.5 Pro)
  ([arXiv:2606.19544](https://arxiv.org/abs/2606.19544)). Un juge local
  déterministe peut être parfaitement reproductible et systématiquement faux.

**Réfuté — et c'est ce qui justifie ce benchmark :** toutes les assertions
classant gpt-oss-120b, Qwen3 et DeepSeek entre eux comme juges binaires ont été
rejetées (votes 0-3 et 1-2). **Il n'existe aucune base publiée fiable pour ranger
ces modèles ; seule une mesure locale tranche.**

**Une mise en garde qui inverse partiellement l'intuition :** dans la
littérature, le gain de la cascade porte sur le **rappel**, pas sur la précision,
et les LLM génériques y sont déjà précision-forts / rappel-faibles (GPT-4o-mini
P 95,21 / R 67,05). Le présent benchmark mesure l'usage inverse — supprimer des
FP — et y trouve un gain réel, mais le transfert n'est pas automatique.

> ⚠️ Plusieurs agents de recherche ont lu les fichiers de ce benchmark en cours
> d'exécution et les ont cités comme sources. Ces citations sont circulaires et
> ne valent pas validation externe ; elles ont été écartées ci-dessus.

---

## 11. Modèles non exploitables — et comment les débloquer

Trois des dix modèles candidats refusent de se charger, tous avec le même message opaque
(`llama-server exited before becoming healthy`). Les en-têtes GGUF et
l'estimateur de LM Studio donnent la cause exacte de chacun.

**`deepseek-v4-flash-0731@q2_k_xl` (96,8 Go) — plafond mémoire GPU, pas
incompatibilité.** LM Studio estime le besoin à **90,18 Gio** et annonce
lui-même : *« This model will fail to load based on your resource guardrails
settings »* (SIGABRT à l'exécution). Sur macOS, `iogpu.wired_limit_mb = 0`
signifie « défaut système », soit ~75 % de la RAM — environ 96 Go ici, insuffisant
une fois le cache KV ajouté. Déblocage :

```bash
sudo sysctl iogpu.wired_limit_mb=118784   # 116 Go alloués au GPU
```

(à relancer après chaque redémarrage, et à relâcher aussi dans les *guardrails*
de LM Studio). **Ce modèle reste donc non mesuré** — c'est la principale lacune
de ce benchmark.

**`deepseek-v4-flash-0731@q8_0` (10,9 Go) — ce n'est pas un modèle de chat.**
Architecture `dflash`, **`block_count = 3`**, 81 tenseurs : c'est le **module de
draft pour décodage spéculatif** qui accompagne les poids `deepseek4` de 96,8 Go.
Utilisation correcte :

```bash
lms load deepseek-v4-flash-0731@q2_k_xl --speculative-draft-simple --speculative-draft-model deepseek-v4-flash-0731@q8_0
```

(107,7 Go cumulés — exige le relèvement ci-dessus.)

**`minimax-h3` (21,4 Go) — fichier inexploitable.** 532 tenseurs mais **zéro
paire de métadonnées** (`kv = 0`) : ni architecture, ni tokenizer, ni
hyperparamètres. Aucun runtime llama.cpp ne peut le charger. À re-télécharger.

---

## 12. Recommandation pour ai-sentinel

**Court terme — réintroduire le juge, en cascade et seuillé.**

1. Implémenter un `LlmJudgePostFilter` conforme à `PIIPostFilterProtocol`, placé
   **après** `FormatPostfilterValidator` (qui reste le premier rempart, gratuit
   et sûr par construction).
2. Juge : **gpt-oss-120b** via LM Studio, `reasoning_effort: low`,
   `response_format: json_schema` strict, `temperature: 0`, prompt **v2** — la
   même plomberie HTTP que `MinistralDetector` (`http2=False`,
   `trust_env=False`).
3. **Ne pas honorer un rejet en dessous de `confidence ≥ 0.95`.** Ce seuil doit
   être une colonne de configuration, pas une constante : c'est le curseur
   précision/rappel que l'exploitant voudra bouger.
4. Ajouter une passe **Ministral-3B en mode juge** comme proposeur : le modèle
   est déjà chargé, la passe coûte 38 min pour 10 000 findings, et elle fait
   passer l'utilité de +0,57 à +0,60 avec 99,3 % de rétention.
5. Conserver le fail-open absolu déjà en place : `UNSURE`, timeout et réponse
   illisible conservent l'entité.

**Moyen terme.**

- **Débloquer `iogpu.wired_limit_mb`** et mesurer DeepSeek-V4-Flash Q2_K_XL, seul
  modèle téléchargé encore non évalué — avec son module de draft en décodage
  spéculatif, qui pourrait le rendre compétitif en débit.
- Tester **Gemma-4-31B en MLX 4-bit** plutôt qu'en GGUF Q6_K. La littérature
  mesure 0,09 dialogue/s et ~18 Go sur exactement ce matériel, contre 0,06 /s et
  25 Go ici — le meilleur juge du benchmark redeviendrait peut-être abordable.
- Réintroduire des **corps de prompt par type** (le design v7 de ce dépôt) : les
  4 pertes résiduelles de v2 sont toutes des confusions type-précis /
  type-concurrent sur des types identifiables (`NATIONAL_ID`, `UNIQUE_ID`,
  `PASSPORT_NUMBER`).
- La littérature indique que le point d'exploitation gagnant exige un **LoRA
  in-domain**, le zero-shot s'effondrant. Un fine-tuning de Gemma-4-31B ou
  d'Ettin-68M sur les verdicts d'ai-sentinel est la suite naturelle — et le
  golden set produit ici en est l'amorce.

---

## 13. Limites

- **DeepSeek-V4-Flash n'a pas été mesuré** (§ 11). Le classement est incomplet
  sur le modèle le plus lourd du parc.
- **Tailles d'échantillon inégales.** Les modèles trop lents sont mesurés sur le
  sous-ensemble équilibré de 94 findings (1 TP + 1 FP par type, les 47 types) et
  non sur les 278. Le classement principal est donc calculé sur le sous-ensemble
  **commun à tous les modèles** ; le second expose la couverture réelle de chacun.
- **Un seul prompt entre modèles.** Seul gpt-oss-120b a été mesuré sur deux
  variantes. Les autres bénéficieraient probablement aussi de v2 : leur score
  reflète le prompt v1, pas leur potentiel.
- **Golden set rédigé à la main.** Les cas sont réalistes et ancrés sur la
  taxonomie de production, mais ce ne sont pas des findings issus d'un vrai scan.
  Un corpus de production reste supérieur pour calibrer un seuil.
- **Une seule exécution par (modèle, finding).** À `temperature: 0` la variance
  est faible mais non nulle ; aucun intervalle de confiance n'est calculé.
- **Le plafond humain est proche.** Avec 47,7 % d'accord inter-annotateur sur les
  entités contextuelles dans la littérature, une partie des « erreurs » mesurées
  sur les axes `competing_type` relève de l'ambiguïté intrinsèque, pas du modèle.

---

## 14. Reproduire

```bash
PY=../../pii-detector-service/.venv/bin/python

$PY build_golden_set.py                                          # golden/judge_golden.jsonl
$PY run_benchmark.py --model openai/gpt-oss-120b --prompt v2     # jeu complet (278)
$PY run_benchmark.py --model google/gemma-4-31b --per-type 1     # sous-ensemble (94)
$PY encoder_judge.py --model ettin
$PY encoder_judge.py --model privacy-filter
$PY score.py                                                     # results/scores.md + injection ici
```

`run_campaign.sh` enchaîne la campagne complète dans l'ordre de débit mesuré.
Méthodologie détaillée : [`README.md`](README.md).

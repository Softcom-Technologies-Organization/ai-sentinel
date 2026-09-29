### Classement principal — sous-ensemble commun (94 findings jugés par tous les modèles)

`utility@λ=10` = (FP supprimés − 10 × TP détruits) / nb FP. Un score négatif signifie que le juge fait plus de mal que de bien.

| # | Modèle | Taille | Rétention TP | Élimination FP | utility@λ=10 | Youden J | MCC | Latence méd. | Échecs |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Gemma-4-31B-IT | 26.4 Go | 97.9% | 87.2% | +0.66 | +0.851 | +0.856 | 61.29 s | 4.3% |
| 2 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type | 63.4 Go | 97.9% | 87.2% | +0.66 | +0.851 | +0.856 | 7.89 s | 0.0% |
| 3 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type (rerun) | 63.4 Go | 97.9% | 85.1% | +0.64 | +0.830 | +0.837 | 8.02 s | 0.0% |
| 4 | Gemma-4-31B heretic (abliterated) | 25.2 Go | 97.9% | 78.7% | +0.57 | +0.766 | +0.780 | 57.00 s | 6.4% |
| 5 | GPT-OSS-120B (MoE, reasoning low) — prompt per_category | 63.4 Go | 97.9% | 78.7% | +0.57 | +0.766 | +0.780 | 6.55 s | 0.0% |
| 6 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type_fallback | 63.4 Go | 95.7% | 85.1% | +0.43 | +0.809 | +0.813 | 8.06 s | 0.0% |
| 7 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 (rerun) | 63.4 Go | 95.7% | 80.9% | +0.38 | +0.766 | +0.775 | 5.24 s | 0.0% |
| 8 | Qwen3.8-27B (thinking) | 30.0 Go | 93.6% | 89.4% | +0.26 | +0.830 | +0.831 | 57.72 s | 1.1% |
| 9 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 | 63.4 Go | 93.6% | 80.9% | +0.17 | +0.745 | +0.751 | 5.11 s | 0.0% |
| 10 | GPT-OSS-120B (MoE, reasoning low) | 63.4 Go | 91.5% | 89.4% | +0.04 | +0.809 | +0.809 | 6.35 s | 0.0% |
| 11 | OpenAI Privacy-Filter 1.4B (encoder MoE) | 2.8 Go | 80.9% | 40.4% | -1.51 | +0.213 | +0.233 | 0.68 s | 0.0% |
| 12 | Ettin-68M-Nemotron-PII (encoder) | 0.1 Go | 70.2% | 70.2% | -2.28 | +0.404 | +0.404 | 0.03 s | 0.0% |
| 13 | Ministral-3B-PII (incumbent) | 2.1 Go | 34.0% | 91.5% | -5.68 | +0.255 | +0.312 | 1.16 s | 9.6% |

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
| 11 | OpenAI Privacy-Filter 1.4B (encoder MoE) | 278/278 | 83.5% | 41.7% | -1.24 | +0.252 | +0.277 | 0.63 s |
| 12 | Ettin-68M-Nemotron-PII (encoder) | 278/278 | 64.7% | 68.3% | -2.84 | +0.331 | +0.331 | 0.03 s |
| 13 | Ministral-3B-PII (incumbent) | 278/278 | 28.1% | 92.8% | -6.27 | +0.209 | +0.274 | 1.19 s |

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

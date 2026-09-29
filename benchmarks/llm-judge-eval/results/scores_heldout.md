### Classement principal — sous-ensemble commun (188 findings jugés par tous les modèles)

`utility@λ=10` = (FP supprimés − 10 × TP détruits) / nb FP. Un score négatif signifie que le juge fait plus de mal que de bien.

| # | Modèle | Taille | Rétention TP | Élimination FP | utility@λ=10 | Youden J | MCC | Latence méd. | Échecs |
|---|---|---|---|---|---|---|---|---|---|
| 1 | GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 63.4 Go | 98.9% | 77.7% | +0.67 | +0.766 | +0.784 | 6.57 s | 0.0% |
| 2 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 63.4 Go | 97.9% | 86.2% | +0.65 | +0.840 | +0.846 | 8.64 s | 0.0% |
| 3 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 63.4 Go | 95.7% | 81.9% | +0.39 | +0.777 | +0.784 | 6.13 s | 0.0% |

### Classement sur le périmètre réellement couvert par chaque modèle

Les modèles trop lents pour les 278 findings sont mesurés sur le sous-ensemble équilibré de 94 (1 TP + 1 FP par type, les 47 types). La colonne « Couverture » donne la taille d'échantillon effective : un score établi sur 94 items n'a pas le même poids qu'un score sur 278.

| # | Modèle | Couverture | Rétention TP | Élimination FP | utility@λ=10 | Youden J | MCC | Latence méd. |
|---|---|---|---|---|---|---|---|---|
| 1 | GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 188/188 | 98.9% | 77.7% | +0.67 | +0.766 | +0.784 | 6.57 s |
| 2 | GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 188/188 | 97.9% | 86.2% | +0.65 | +0.840 | +0.846 | 8.64 s |
| 3 | GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 188/188 | 95.7% | 81.9% | +0.39 | +0.777 | +0.784 | 6.13 s |

### Lecture opérationnelle

| Modèle | Couverture | TP détruits | FP supprimés | Précision du rejet | UNSURE | Troncatures | p95 | Tokens raisonnement |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 188/188 | 1/94 | 73/94 | 98.6% | 1.1% | 0.0% | 10.7 s | 59 |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 188/188 | 2/94 | 81/94 | 97.6% | 0.0% | 0.0% | 13.1 s | 53 |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 188/188 | 4/94 | 77/94 | 95.1% | 1.6% | 0.0% | 10.4 s | 67 |

### Coût opérationnel projeté

Débit mesuré de bout en bout (chargement exclu), au parallélisme testé. Un scan d'espace Confluence produit couramment plusieurs milliers de findings : c'est ce volume qui décide de la faisabilité, pas la latence unitaire.

| Modèle | Empreinte | Débit mesuré | 1 000 findings | 10 000 findings |
|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 63.4 Go | 0.56 /s | 30 min | 5.0 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 63.4 Go | 0.45 /s | 37 min | 6.1 h |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 63.4 Go | 0.59 /s | 28 min | 4.7 h |

### Par catégorie d'entité

| Modèle | CONTACT | DIGITAL | EMPLOYMENT | FINANCIAL | GOV_ID | IDENTITY | MEDICAL | TEMPORAL |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 100.0% / 70.0% | 100.0% / 80.0% | 100.0% / 75.0% | 100.0% / 64.3% | 100.0% / 71.4% | 100.0% / 90.0% | 83.3% / 100.0% | 100.0% / 50.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 100.0% / 60.0% | 100.0% / 95.0% | 100.0% / 87.5% | 100.0% / 71.4% | 92.9% / 85.7% | 100.0% / 95.0% | 83.3% / 100.0% | 100.0% / 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 100.0% / 70.0% | 100.0% / 85.0% | 100.0% / 75.0% | 92.9% / 78.6% | 78.6% / 85.7% | 100.0% / 85.0% | 100.0% / 83.3% | 100.0% / 100.0% |

_Format : rétention TP / élimination FP._

### Par axe de difficulté

| Modèle | canonical_no_clue | canonical_with_clue | competing_type | deterministic_control | label_not_value | masked_value | placeholder | variable_reference |
|---|---|---|---|---|---|---|---|---|
| GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout) | 100.0% | 98.3% | 78.7% | 100.0% / 20.0% | 100.0% | 66.7% | 50.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout) | 96.4% | 98.3% | 91.8% | 100.0% / 20.0% | 100.0% | 66.7% | 50.0% | 100.0% |
| GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout) | 96.4% | 96.6% | 83.6% | 85.7% / 60.0% | 100.0% | 66.7% | 33.3% | 100.0% |

### Seuil de confiance exigé pour honorer un rejet

Le juge ne supprime l'entité que si sa `confidence` atteint le seuil. C'est le seul levier disponible une fois le modèle figé.

**GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout)**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 98.9% | 77.7% | +0.67 |
| ≥ 0.50 | 98.9% | 77.7% | +0.67 |
| ≥ 0.70 | 98.9% | 77.7% | +0.67 |
| ≥ 0.80 | 98.9% | 77.7% | +0.67 |
| ≥ 0.90 | 98.9% | 77.7% | +0.67 |
| ≥ 0.95 | 98.9% | 71.3% | +0.61 |
| ≥ 0.99 | 100.0% | 47.9% | +0.48 |
| ≥ 1.00 | 100.0% | 0.0% | +0.00 |

**GPT-OSS-120B (MoE, reasoning low) — prompt per_type (heldout)**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 97.9% | 86.2% | +0.65 |
| ≥ 0.50 | 97.9% | 86.2% | +0.65 |
| ≥ 0.70 | 97.9% | 86.2% | +0.65 |
| ≥ 0.80 | 97.9% | 86.2% | +0.65 |
| ≥ 0.90 | 97.9% | 86.2% | +0.65 |
| ≥ 0.95 | 98.9% | 79.8% | +0.69 |
| ≥ 0.99 | 98.9% | 54.3% | +0.44 |
| ≥ 1.00 | 100.0% | 0.0% | +0.00 |

**GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout)**

| Seuil | Rétention TP | Élimination FP | utility@λ=10 |
|---|---|---|---|
| ≥ 0.00 | 95.7% | 81.9% | +0.39 |
| ≥ 0.50 | 95.7% | 81.9% | +0.39 |
| ≥ 0.70 | 95.7% | 81.9% | +0.39 |
| ≥ 0.80 | 95.7% | 81.9% | +0.39 |
| ≥ 0.90 | 95.7% | 81.9% | +0.39 |
| ≥ 0.95 | 97.9% | 69.1% | +0.48 |
| ≥ 0.99 | 98.9% | 39.4% | +0.29 |
| ≥ 1.00 | 100.0% | 0.0% | +0.00 |

### Couverture du vocabulaire (juges encodeurs)

Un tagger ne peut pas se prononcer sur un type qu'il n'a jamais appris à émettre. La part hors vocabulaire borne sa valeur, quelle que soit sa qualité sur le reste.

| Encodeur | Findings jugeables | Hors vocabulaire | Rétention TP (dans le vocabulaire) | Élimination FP (dans le vocabulaire) |
|---|---|---|---|---|

### Pertes de rappel (TP détruits) du meilleur modèle

**GPT-OSS-120B (MoE, reasoning low) — prompt per_category (heldout)** — 1 vrai(s) positif(s) perdu(s) :

| Type | Valeur | Axe | Motif invoqué |
|---|---|---|---|
| HEALTH_PLAN_BENEFICIARY_NUMBER | `KK-88-401337` | canonical_with_clue | Le contexte indique qu'il s'agit d'un numéro de police d'assurance, pas du numéro d'assuré maladie (type concu |

### Test-retest — plancher de bruit (GPT-OSS-120B (MoE, reasoning low))

Deux exécutions du **même** prompt à `temperature: 0`. LM Studio n'étant pas déterministe au bit près, l'écart mesuré ici borne par le bas tout écart entre bras : en dessous, on ne conclut pas.

| Prompt | Findings comparés | Verdicts discordants | Décisions de rejet discordantes (seuil 0.95) |
|---|---|---|---|
| _aucune répétition disponible_ | | | |

### Comparaison par paires — chaque bras contre le générique

Référence : **GPT-OSS-120B (MoE, reasoning low) — prompt v2 (heldout)**. Seuil de confiance 0.95 (le point d'exploitation retenu). Chaque finding est jugé par les deux bras, la comparaison est item par item.

| Bras | n | Rétention TP | Élimination FP | utility@λ=10 | ΔU | TP détruits / sauvés | FP éliminés en plus | McNemar (réf. juste / bras juste) | p | Temps mural |
|---|---|---|---|---|---|---|---|---|---|---|
| per_category (heldout) | 188 | 98.9% | 71.3% | +0.61 | +0.13 | −1 / +2 | +2 | 9 / 12 | 0.664 | × 1.06 |
| per_type (heldout) | 188 | 98.9% | 79.8% | +0.69 | +0.21 | −1 / +2 | +10 | 8 / 19 | 0.052 | × 1.30 |

_Référence elle-même : 95.7% de rétention TP, 81.9% d'élimination FP au seuil 0. « TP détruits / sauvés » se lit par rapport à elle : un bras peut détruire un vrai positif qu'elle conservait tout en en sauvant plusieurs qu'elle détruisait — la contrainte dure du § 9 ne regarde que le premier nombre._

### Contrainte dure — vrais positifs détruits par un bras et conservés par le générique

| Bras | Type | Valeur | Axe | Motif invoqué |
|---|---|---|---|---|
| per_category | HEALTH_PLAN_BENEFICIARY_NUMBER | `KK-88-401337` | canonical_with_clue | Le contexte indique qu'il s'agit d'un numéro de police d'assurance, pas du numéro d'assuré maladie (type concu |
| per_type | HEALTH_PLAN_BENEFICIARY_NUMBER | `KK-88-401337` | canonical_with_clue | Motif (b) : le contexte indique explicitement qu'il s'agit d'une "Policennummer" (numéro de police/contrat), q |

**Un seul cas suffit à rejeter le bras concerné** (§ 9, règle 1) : un vrai positif perdu coûte dix faux positifs éliminés.

### Où chaque bras gagne et perd, par axe de difficulté

| Axe de difficulté | n | per_category (heldout) | per_type (heldout) |
|---|---|---|---|
| canonical_no_clue | 28 | +0 FP / +0 TP | +0 FP / +0 TP |
| canonical_with_clue | 59 | +0 FP / +0 TP | +0 FP / +0 TP |
| competing_type | 61 | +3 FP / +0 TP | +10 FP / +0 TP |
| deterministic_control | 12 | -2 FP / +1 TP | -2 FP / +1 TP |
| label_not_value | 15 | +0 FP / +0 TP | +0 FP / +0 TP |
| masked_value | 3 | +0 FP / +0 TP | +1 FP / +0 TP |
| placeholder | 6 | +0 FP / +0 TP | +0 FP / +0 TP |
| variable_reference | 4 | +1 FP / +0 TP | +1 FP / +0 TP |

_Format : faux positifs éliminés en plus (net) / vrais positifs conservés en plus (net), par rapport au générique. Un nombre négatif en TP est une régression de rappel._

### Où chaque bras gagne et perd, par catégorie

| Catégorie | n | per_category (heldout) | per_type (heldout) |
|---|---|---|---|
| CONTACT | 20 | +0 FP / +0 TP | +0 FP / +0 TP |
| DIGITAL | 40 | +2 FP / +0 TP | +6 FP / +0 TP |
| EMPLOYMENT | 16 | +0 FP / +0 TP | +1 FP / +0 TP |
| FINANCIAL | 28 | -4 FP / +1 TP | -2 FP / +1 TP |
| GOV_ID | 28 | +1 FP / +1 TP | +2 FP / +1 TP |
| IDENTITY | 40 | +1 FP / +0 TP | +1 FP / +0 TP |
| MEDICAL | 12 | +2 FP / -1 TP | +2 FP / -1 TP |
| TEMPORAL | 4 | +0 FP / +0 TP | +0 FP / +0 TP |

_Format : faux positifs éliminés en plus (net) / vrais positifs conservés en plus (net), par rapport au générique. Un nombre négatif en TP est une régression de rappel._

### Règle de décision (§ 9 du plan)

| Bras | TP détruit en plus ? | ΔU ≥ +0,07 ? | Temps mural ≤ 1,2 × A ? | Au-dessus du plancher de bruit ? | Issue sur ce jeu |
|---|---|---|---|---|---|
| per_category (heldout) | oui | oui (+0.13) | oui (× 1.06) | non (3 corrections nettes, plancher 0) | **rejeté** (contrainte dure) |
| per_type (heldout) | oui | oui (+0.21) | non (× 1.30) | oui (11 corrections nettes, plancher 0) | **rejeté** (contrainte dure) |

La règle complète exige en plus ΔU ≥ 0 sur `judge_heldout.jsonl` : cette colonne se lit dans `scores_heldout.md`, produit par le même script sur l'autre jeu.

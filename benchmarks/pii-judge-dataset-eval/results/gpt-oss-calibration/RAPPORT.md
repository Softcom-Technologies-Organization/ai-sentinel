# GPT-OSS-120B : calibration comme juge PII

Rapport généré le 2026-09-20T17:37:13.786113+00:00.

## Résultats sur les documents réservés

Les stratégies et seuils sont choisis exclusivement sur le lot de calibrage, puis figés avant le lot de test.

| Réglage | Seuil FP | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| general_0.8 | 0.8 | 80.88 % | 85.55 % | 83.15 % | 79.19 % | 173 | -0.696 |
| recall_priority | 0.999 | 51.18 % | 99.92 % | 67.69 % | 1.89 % | 1 | 0.010 |
| max_f1 | 0.975 | 86.40 % | 93.40 % | 89.76 % | 84.87 % | 79 | 0.169 |

La classe positive est une vraie entité conservée. `UNSURE`, `UNPARSEABLE`, `ERROR` et un rejet sous le seuil restent conservés.

| Réglage | TP | FP | TN | FN | IC 95 % précision | IC 95 % rappel | IC 95 % F1 |
|---|---:|---:|---:|---:|---|---|---|
| general_0.8 | 1024 | 242 | 921 | 173 | 78.68 % – 83.33 % | 83.15 % – 87.69 % | 81.33 % – 84.88 % |
| recall_priority | 1196 | 1141 | 22 | 1 | 49.03 % – 53.57 % | 99.74 % – 100.00 % | 65.79 % – 69.73 % |
| max_f1 | 1118 | 176 | 987 | 79 | 84.52 % – 88.25 % | 91.85 % – 94.94 % | 88.51 % – 91.02 % |

## Comparaison avec OpenJev

| Juge | Objectif | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---|---:|---:|---:|---:|---:|---:|
| GPT-OSS-120B | recall_priority | 51.18 % | 99.92 % | 67.69 % | 1.89 % | 1 | 0.010 |
| OpenJev | recall_priority | 59.59 % | 99.67 % | 74.59 % | 30.44 % | 4 | 0.270 |
| GPT-OSS-120B | max_f1 | 86.40 % | 93.40 % | 89.76 % | 84.87 % | 79 | 0.169 |
| OpenJev | max_f1 | 79.45 % | 84.29 % | 81.80 % | 77.56 % | 188 | -0.841 |

## Calibration des stratégies GPT-OSS

| Stratégie | Réglage | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| general | rappel prioritaire | 0.999 | 48.67 % | 100.00 % | 65.47 % | 0.16 % | 0 | 0.002 |
| general | F1 maximal | 0.95 | 76.65 % | 90.38 % | 82.95 % | 73.94 % | 58 | -0.171 |
| per_type | rappel prioritaire | 0.999 | 49.06 % | 99.83 % | 65.79 % | 1.88 % | 1 | 0.003 |
| per_type | F1 maximal | 0.925 | 84.38 % | 93.20 % | 88.57 % | 83.67 % | 41 | 0.193 |
| per_type_financial_only | rappel prioritaire | 0.999 | 48.94 % | 100.00 % | 65.72 % | 1.26 % | 0 | 0.013 |
| per_type_financial_only | F1 maximal | 0.9 | 82.31 % | 88.72 % | 85.40 % | 81.95 % | 68 | -0.248 |
| concise | rappel prioritaire | 1 | 48.63 % | 100.00 % | 65.44 % | 0.00 % | 0 | 0.000 |
| concise | F1 maximal | 0.95 | 85.97 % | 80.27 % | 83.02 % | 87.60 % | 119 | -0.992 |
| checklist | rappel prioritaire | 1 | 48.63 % | 100.00 % | 65.44 % | 0.00 % | 0 | 0.000 |
| checklist | F1 maximal | 0.975 | 87.69 % | 94.53 % | 90.98 % | 87.44 % | 33 | 0.356 |
| few_shot | rappel prioritaire | 0.975 | 48.94 % | 100.00 % | 65.72 % | 1.26 % | 0 | 0.013 |
| few_shot | F1 maximal | 0.9 | 88.72 % | 88.72 % | 88.72 % | 89.32 % | 68 | -0.174 |

## Détail du réglage orienté rappel

Stratégie `per_type_financial_only`, seuil `0.999`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 54.26 % | 100.00 % | 70.35 % | 1.67 % | 0 |
| API_KEY | 134 | 47.76 % | 100.00 % | 64.65 % | 0.00 % | 0 |
| BANK_ROUTING_NUMBER | 114 | 52.63 % | 100.00 % | 68.97 % | 0.00 % | 0 |
| CREDIT_CARD | 136 | 52.63 % | 100.00 % | 68.97 % | 4.55 % | 0 |
| CREDIT_DEBIT_CARD | 136 | 51.85 % | 100.00 % | 68.29 % | 1.52 % | 0 |
| CVV | 130 | 48.46 % | 100.00 % | 65.28 % | 0.00 % | 0 |
| DRIVER_LICENSE_NUMBER | 136 | 46.32 % | 100.00 % | 63.32 % | 0.00 % | 0 |
| IBAN | 121 | 55.00 % | 100.00 % | 70.97 % | 1.82 % | 0 |
| IBAN_CODE | 121 | 55.00 % | 100.00 % | 70.97 % | 1.82 % | 0 |
| IPV4 | 130 | 52.38 % | 100.00 % | 68.75 % | 6.25 % | 0 |
| IPV6 | 131 | 57.25 % | 100.00 % | 72.82 % | 0.00 % | 0 |
| IP_ADDRESS | 131 | 52.34 % | 100.00 % | 68.72 % | 4.69 % | 0 |
| NATIONAL_ID | 136 | 48.15 % | 100.00 % | 65.00 % | 1.41 % | 0 |
| PASSWORD | 143 | 46.85 % | 100.00 % | 63.81 % | 0.00 % | 0 |
| PIN | 142 | 52.21 % | 100.00 % | 68.60 % | 8.45 % | 0 |
| SOCIALNUM | 126 | 47.62 % | 100.00 % | 64.52 % | 0.00 % | 0 |
| SOCIAL_SECURITY_NUMBER | 126 | 47.62 % | 100.00 % | 64.52 % | 0.00 % | 0 |
| SWIFT_BIC | 137 | 54.07 % | 98.65 % | 69.86 % | 1.59 % | 1 |

## Temps de calcul

| Stratégie | Prédictions | Latence médiane | Latence p95 | Temps cumulé |
|---|---:|---:|---:|---:|
| general | 3600 | 6.66 s | 10.97 s | 428.5 min |
| per_type | 1240 | 6.99 s | 13.74 s | 163.5 min |
| per_type_financial_only | 3600 | 6.99 s | 12.35 s | 454.1 min |
| concise | 1240 | 6.55 s | 12.20 s | 150.5 min |
| checklist | 3600 | 9.47 s | 14.17 s | 587.3 min |
| few_shot | 1240 | 6.88 s | 12.09 s | 154.0 min |

## Protocole et limites

- Modèle : `openai/gpt-oss-120b`, `reasoning_effort: low`, contexte `8192`.
- Dataset : `b08d9c5da559610d6d4da7a05e051a8cd73e2ef6288742e4e8498b5122f3f5df` ; 1240 candidats de calibrage et 2360 de test, avec exactement les partitions OpenJev.
- Même grille de seuils et même fonction d'utilité que la campagne OpenJev.
- GPT-OSS génère un verdict JSON et une confiance déclarée. Cette confiance n'est pas directement comparable au score conditionnel entre options d'OpenJev ; chaque juge est donc calibré séparément.
- Les textes des corpus sont synthétiques et les faux positifs construits. Les résultats ne mesurent pas la fréquence réelle des erreurs en production.
- Une seule exécution par candidat ; `temperature: 0` ne garantit pas une parfaite reproductibilité du serveur LM Studio.

## Reproduire

Depuis la racine du dépôt :

```bash
caffeinate -i pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py
```

Les résultats sont repris automatiquement. `--report-only` régénère le rapport sans charger GPT-OSS. `--keep-loaded` évite le rechargement lorsque le bon modèle est déjà servi par LM Studio.

# GPT-OSS-120B (MoE, reasoning low) : calibration comme juge PII

Rapport généré le 2026-11-02T11:55:47.646215+00:00.

## Résultats sur les documents réservés

Les stratégies et seuils sont choisis exclusivement sur le lot de calibrage, puis figés avant le lot de test.

| Réglage | Seuil FP | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| general_0.8 | 0.8 | 80.68 % | 84.80 % | 82.69 % | 79.11 % | 182 | -0.774 |
| recall_priority | 0.999 | 51.46 % | 99.92 % | 67.94 % | 3.01 % | 1 | 0.021 |
| max_f1 | 0.9 | 86.90 % | 91.98 % | 89.37 % | 85.73 % | 96 | 0.032 |

La classe positive est une vraie entité conservée. `UNSURE`, `UNPARSEABLE`, `ERROR` et un rejet sous le seuil restent conservés.

| Réglage | TP | FP | TN | FN | IC 95 % précision | IC 95 % rappel | IC 95 % F1 |
|---|---:|---:|---:|---:|---|---|---|
| general_0.8 | 1015 | 243 | 920 | 182 | 78.54 % – 83.12 % | 82.34 % – 87.03 % | 80.75 % – 84.51 % |
| recall_priority | 1196 | 1128 | 35 | 1 | 49.37 % – 53.84 % | 99.74 % – 100.00 % | 66.10 % – 69.96 % |
| max_f1 | 1101 | 166 | 997 | 96 | 84.90 % – 88.91 % | 90.22 % – 93.69 % | 87.88 % – 90.72 % |

## Comparaison avec OpenJev

Les métriques OpenJev sont absentes. Exécuter d'abord `calibrate_openjev.py`.

## Calibration des stratégies

| Stratégie | Réglage | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| general | rappel prioritaire | 0.999 | 48.75 % | 100.00 % | 65.54 % | 0.47 % | 0 | 0.005 |
| general | F1 maximal | 0.975 | 71.66 % | 96.85 % | 82.37 % | 63.74 % | 19 | 0.339 |
| per_type | rappel prioritaire | 0.999 | 49.14 % | 100.00 % | 65.90 % | 2.04 % | 0 | 0.020 |
| per_type | F1 maximal | 0.9 | 85.12 % | 92.04 % | 88.45 % | 84.77 % | 48 | 0.094 |
| per_type_financial_only | rappel prioritaire | 0.999 | 49.30 % | 99.83 % | 66.01 % | 2.83 % | 1 | 0.013 |
| per_type_financial_only | F1 maximal | 0.9 | 82.57 % | 89.55 % | 85.92 % | 82.10 % | 63 | -0.168 |
| concise | rappel prioritaire | 1 | 48.63 % | 100.00 % | 65.44 % | 0.00 % | 0 | 0.000 |
| concise | F1 maximal | 0.95 | 84.15 % | 79.27 % | 81.64 % | 85.87 % | 125 | -1.104 |
| checklist | rappel prioritaire | 1 | 48.63 % | 100.00 % | 65.44 % | 0.00 % | 0 | 0.000 |
| checklist | F1 maximal | 0.975 | 81.09 % | 93.86 % | 87.01 % | 79.28 % | 37 | 0.212 |
| few_shot | rappel prioritaire | 0.975 | 48.94 % | 100.00 % | 65.72 % | 1.26 % | 0 | 0.013 |
| few_shot | F1 maximal | 0.9 | 87.30 % | 88.89 % | 88.09 % | 87.76 % | 67 | -0.174 |

## Résultats par type : general_0.8

Stratégie `general`, seuil `0.8`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 80.00 % | 74.29 % | 77.04 % | 78.33 % | 18 |
| API_KEY | 134 | 58.10 % | 95.31 % | 72.19 % | 37.14 % | 3 |
| BANK_ROUTING_NUMBER | 114 | 86.15 % | 93.33 % | 89.60 % | 83.33 % | 4 |
| CREDIT_CARD | 136 | 100.00 % | 72.86 % | 84.30 % | 100.00 % | 19 |
| CREDIT_DEBIT_CARD | 136 | 98.08 % | 72.86 % | 83.61 % | 98.48 % | 19 |
| CVV | 130 | 85.71 % | 95.24 % | 90.23 % | 85.07 % | 3 |
| DRIVER_LICENSE_NUMBER | 136 | 70.77 % | 73.02 % | 71.88 % | 73.97 % | 17 |
| IBAN | 121 | 86.11 % | 93.94 % | 89.86 % | 81.82 % | 4 |
| IBAN_CODE | 121 | 86.11 % | 93.94 % | 89.86 % | 81.82 % | 4 |
| IPV4 | 130 | 84.42 % | 98.48 % | 90.91 % | 81.25 % | 1 |
| IPV6 | 131 | 85.06 % | 98.67 % | 91.36 % | 76.79 % | 1 |
| IP_ADDRESS | 131 | 77.91 % | 100.00 % | 87.58 % | 70.31 % | 0 |
| NATIONAL_ID | 136 | 74.32 % | 84.62 % | 79.14 % | 73.24 % | 10 |
| PASSWORD | 143 | 72.53 % | 98.51 % | 83.54 % | 67.11 % | 1 |
| PIN | 142 | 81.08 % | 84.51 % | 82.76 % | 80.28 % | 11 |
| SOCIALNUM | 126 | 78.38 % | 48.33 % | 59.79 % | 87.88 % | 31 |
| SOCIAL_SECURITY_NUMBER | 126 | 82.61 % | 63.33 % | 71.70 % | 87.88 % | 22 |
| SWIFT_BIC | 137 | 86.96 % | 81.08 % | 83.92 % | 85.71 % | 14 |

## Résultats par type : recall_priority

Stratégie `per_type`, seuil `0.999`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 53.85 % | 100.00 % | 70.00 % | 0.00 % | 0 |
| API_KEY | 134 | 47.76 % | 100.00 % | 64.65 % | 0.00 % | 0 |
| BANK_ROUTING_NUMBER | 114 | 52.63 % | 100.00 % | 68.97 % | 0.00 % | 0 |
| CREDIT_CARD | 136 | 51.47 % | 100.00 % | 67.96 % | 0.00 % | 0 |
| CREDIT_DEBIT_CARD | 136 | 52.24 % | 100.00 % | 68.63 % | 3.03 % | 0 |
| CVV | 130 | 48.84 % | 100.00 % | 65.62 % | 1.49 % | 0 |
| DRIVER_LICENSE_NUMBER | 136 | 46.32 % | 100.00 % | 63.32 % | 0.00 % | 0 |
| IBAN | 121 | 55.00 % | 100.00 % | 70.97 % | 1.82 % | 0 |
| IBAN_CODE | 121 | 55.00 % | 100.00 % | 70.97 % | 1.82 % | 0 |
| IPV4 | 130 | 55.93 % | 100.00 % | 71.74 % | 18.75 % | 0 |
| IPV6 | 131 | 57.69 % | 100.00 % | 73.17 % | 1.79 % | 0 |
| IP_ADDRESS | 131 | 51.94 % | 100.00 % | 68.37 % | 3.12 % | 0 |
| NATIONAL_ID | 136 | 47.79 % | 100.00 % | 64.68 % | 0.00 % | 0 |
| PASSWORD | 143 | 49.26 % | 100.00 % | 66.01 % | 9.21 % | 0 |
| PIN | 142 | 52.21 % | 100.00 % | 68.60 % | 8.45 % | 0 |
| SOCIALNUM | 126 | 47.62 % | 100.00 % | 64.52 % | 0.00 % | 0 |
| SOCIAL_SECURITY_NUMBER | 126 | 48.00 % | 100.00 % | 64.86 % | 1.52 % | 0 |
| SWIFT_BIC | 137 | 54.07 % | 98.65 % | 69.86 % | 1.59 % | 1 |

## Résultats par type : max_f1

Stratégie `per_type`, seuil `0.9`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 91.23 % | 74.29 % | 81.89 % | 91.67 % | 18 |
| API_KEY | 134 | 64.89 % | 95.31 % | 77.22 % | 52.86 % | 3 |
| BANK_ROUTING_NUMBER | 114 | 80.56 % | 96.67 % | 87.88 % | 74.07 % | 2 |
| CREDIT_CARD | 136 | 98.46 % | 91.43 % | 94.81 % | 98.48 % | 6 |
| CREDIT_DEBIT_CARD | 136 | 100.00 % | 91.43 % | 95.52 % | 100.00 % | 6 |
| CVV | 130 | 90.62 % | 92.06 % | 91.34 % | 91.04 % | 5 |
| DRIVER_LICENSE_NUMBER | 136 | 90.91 % | 95.24 % | 93.02 % | 91.78 % | 3 |
| IBAN | 121 | 89.39 % | 89.39 % | 89.39 % | 87.27 % | 7 |
| IBAN_CODE | 121 | 89.86 % | 93.94 % | 91.85 % | 87.27 % | 4 |
| IPV4 | 130 | 89.19 % | 100.00 % | 94.29 % | 87.50 % | 0 |
| IPV6 | 131 | 84.09 % | 98.67 % | 90.80 % | 75.00 % | 1 |
| IP_ADDRESS | 131 | 87.01 % | 100.00 % | 93.06 % | 84.38 % | 0 |
| NATIONAL_ID | 136 | 93.33 % | 86.15 % | 89.60 % | 94.37 % | 9 |
| PASSWORD | 143 | 80.00 % | 95.52 % | 87.07 % | 78.95 % | 3 |
| PIN | 142 | 85.07 % | 80.28 % | 82.61 % | 85.92 % | 14 |
| SOCIALNUM | 126 | 87.88 % | 96.67 % | 92.06 % | 87.88 % | 2 |
| SOCIAL_SECURITY_NUMBER | 126 | 86.57 % | 96.67 % | 91.34 % | 86.36 % | 2 |
| SWIFT_BIC | 137 | 88.73 % | 85.14 % | 86.90 % | 87.30 % | 11 |

## Temps de calcul

| Stratégie | Prédictions | Latence médiane | Latence p95 | Temps cumulé |
|---|---:|---:|---:|---:|
| general | 3600 | 7.81 s | 12.59 s | 497.2 min |
| per_type | 3600 | 8.30 s | 15.35 s | 548.4 min |
| per_type_financial_only | 1240 | 7.97 s | 13.50 s | 177.1 min |
| concise | 1240 | 7.59 s | 13.75 s | 173.0 min |
| checklist | 1240 | 11.56 s | 17.14 s | 247.9 min |
| few_shot | 1240 | 7.76 s | 12.99 s | 170.6 min |

## Protocole et limites

- Modèle : `openai/gpt-oss-120b`, paramètres `{'reasoning_effort': 'low'}`, contexte `8192`.
- Dataset : `b08d9c5da559610d6d4da7a05e051a8cd73e2ef6288742e4e8498b5122f3f5df` ; 1240 candidats de calibrage et 2360 de test, avec exactement les partitions OpenJev.
- Même grille de seuils et même fonction d'utilité que la campagne OpenJev.
- Le modèle génère un verdict JSON et une confiance déclarée. Cette confiance n'est pas directement comparable au score conditionnel entre options d'OpenJev ; chaque juge est donc calibré séparément.
- Les textes des corpus sont synthétiques et les faux positifs construits. Les résultats ne mesurent pas la fréquence réelle des erreurs en production.
- Une seule exécution par candidat ; `temperature: 0` ne garantit pas une parfaite reproductibilité du serveur LM Studio.

## Reproduire

Depuis la racine du dépôt :

```bash
caffeinate -i pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py --judge gpt-oss-120b --output /Users/Thibaut.Vuillaume/Workspace/ai-sentinel/benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/gpt
```

Les résultats sont repris automatiquement. `--report-only` régénère le rapport sans charger le modèle. `--keep-loaded` utilise le modèle déjà servi.

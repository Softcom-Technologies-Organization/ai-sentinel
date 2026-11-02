# Qwen3.8-Flash-Next IQ3_S (Strata) : calibration comme juge PII

Rapport généré le 2026-11-02T04:21:30.625815+00:00.

## Résultats sur les documents réservés

Les stratégies et seuils sont choisis exclusivement sur le lot de calibrage, puis figés avant le lot de test.

| Réglage | Seuil FP | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| general_0.8 | 0.8 | 98.53 % | 89.64 % | 93.88 % | 98.62 % | 124 | -0.080 |
| recall_priority | 0.975 | 77.84 % | 99.75 % | 87.44 % | 70.77 % | 3 | 0.682 |
| max_f1 | 0.95 | 99.82 % | 91.56 % | 95.51 % | 99.83 % | 101 | 0.130 |

La classe positive est une vraie entité conservée. `UNSURE`, `UNPARSEABLE`, `ERROR` et un rejet sous le seuil restent conservés.

| Réglage | TP | FP | TN | FN | IC 95 % précision | IC 95 % rappel | IC 95 % F1 |
|---|---:|---:|---:|---:|---|---|---|
| general_0.8 | 1073 | 16 | 1147 | 124 | 97.67 % – 99.21 % | 87.74 % – 91.69 % | 92.71 % – 94.99 % |
| recall_priority | 1194 | 340 | 823 | 3 | 75.40 % – 80.08 % | 99.32 % – 100.00 % | 85.85 % – 88.83 % |
| max_f1 | 1096 | 2 | 1161 | 101 | 99.54 % – 100.00 % | 89.85 % – 93.34 % | 94.58 % – 96.48 % |

## Comparaison avec OpenJev

Les métriques OpenJev sont absentes. Exécuter d'abord `calibrate_openjev.py`.

## Calibration des stratégies

| Stratégie | Réglage | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| general | rappel prioritaire | 0.975 | 68.41 % | 99.83 % | 81.19 % | 56.36 % | 1 | 0.548 |
| general | F1 maximal | 0.95 | 98.16 % | 88.39 % | 93.02 % | 98.43 % | 70 | -0.115 |
| per_type | rappel prioritaire | 0.975 | 75.47 % | 100.00 % | 86.02 % | 69.23 % | 0 | 0.692 |
| per_type | F1 maximal | 0.95 | 100.00 % | 90.88 % | 95.22 % | 100.00 % | 55 | 0.137 |
| per_type_financial_only | rappel prioritaire | 0.975 | 72.65 % | 100.00 % | 84.16 % | 64.36 % | 0 | 0.644 |
| per_type_financial_only | F1 maximal | 0.95 | 99.63 % | 89.55 % | 94.32 % | 99.69 % | 63 | 0.008 |
| concise | rappel prioritaire | 0.975 | 66.41 % | 100.00 % | 79.81 % | 52.12 % | 0 | 0.521 |
| concise | F1 maximal | 0.95 | 98.81 % | 82.59 % | 89.97 % | 99.06 % | 105 | -0.658 |
| checklist | rappel prioritaire | 0.975 | 78.21 % | 100.00 % | 87.77 % | 73.63 % | 0 | 0.736 |
| checklist | F1 maximal | 0.95 | 97.45 % | 88.89 % | 92.97 % | 97.80 % | 67 | -0.074 |
| few_shot | rappel prioritaire | 0.99 | 48.83 % | 100.00 % | 65.61 % | 0.78 % | 0 | 0.008 |
| few_shot | F1 maximal | 0.95 | 97.84 % | 90.22 % | 93.87 % | 98.12 % | 59 | 0.055 |

## Résultats par type : general_0.8

Stratégie `general`, seuil `0.8`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 98.11 % | 74.29 % | 84.55 % | 98.33 % | 18 |
| API_KEY | 134 | 100.00 % | 89.06 % | 94.21 % | 100.00 % | 7 |
| BANK_ROUTING_NUMBER | 114 | 96.36 % | 88.33 % | 92.17 % | 96.30 % | 7 |
| CREDIT_CARD | 136 | 96.36 % | 75.71 % | 84.80 % | 96.97 % | 17 |
| CREDIT_DEBIT_CARD | 136 | 96.55 % | 80.00 % | 87.50 % | 96.97 % | 14 |
| CVV | 130 | 100.00 % | 84.13 % | 91.38 % | 100.00 % | 10 |
| DRIVER_LICENSE_NUMBER | 136 | 96.72 % | 93.65 % | 95.16 % | 97.26 % | 4 |
| IBAN | 121 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IBAN_CODE | 121 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IPV4 | 130 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IPV6 | 131 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IP_ADDRESS | 131 | 98.48 % | 97.01 % | 97.74 % | 98.44 % | 2 |
| NATIONAL_ID | 136 | 91.67 % | 84.62 % | 88.00 % | 92.96 % | 10 |
| PASSWORD | 143 | 100.00 % | 89.55 % | 94.49 % | 100.00 % | 7 |
| PIN | 142 | 98.28 % | 80.28 % | 88.37 % | 98.59 % | 14 |
| SOCIALNUM | 126 | 100.00 % | 91.67 % | 95.65 % | 100.00 % | 5 |
| SOCIAL_SECURITY_NUMBER | 126 | 100.00 % | 90.00 % | 94.74 % | 100.00 % | 6 |
| SWIFT_BIC | 137 | 100.00 % | 95.95 % | 97.93 % | 100.00 % | 3 |

## Résultats par type : recall_priority

Stratégie `checklist`, seuil `0.975`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 72.63 % | 98.57 % | 83.64 % | 56.67 % | 1 |
| API_KEY | 134 | 70.33 % | 100.00 % | 82.58 % | 61.43 % | 0 |
| BANK_ROUTING_NUMBER | 114 | 78.95 % | 100.00 % | 88.24 % | 70.37 % | 0 |
| CREDIT_CARD | 136 | 84.34 % | 100.00 % | 91.50 % | 80.30 % | 0 |
| CREDIT_DEBIT_CARD | 136 | 75.27 % | 100.00 % | 85.89 % | 65.15 % | 0 |
| CVV | 130 | 73.26 % | 100.00 % | 84.56 % | 65.67 % | 0 |
| DRIVER_LICENSE_NUMBER | 136 | 74.12 % | 100.00 % | 85.14 % | 69.86 % | 0 |
| IBAN | 121 | 79.27 % | 98.48 % | 87.84 % | 69.09 % | 1 |
| IBAN_CODE | 121 | 77.38 % | 98.48 % | 86.67 % | 65.45 % | 1 |
| IPV4 | 130 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IPV6 | 131 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IP_ADDRESS | 131 | 97.10 % | 100.00 % | 98.53 % | 96.88 % | 0 |
| NATIONAL_ID | 136 | 74.71 % | 100.00 % | 85.53 % | 69.01 % | 0 |
| PASSWORD | 143 | 72.83 % | 100.00 % | 84.28 % | 67.11 % | 0 |
| PIN | 142 | 70.30 % | 100.00 % | 82.56 % | 57.75 % | 0 |
| SOCIALNUM | 126 | 66.67 % | 100.00 % | 80.00 % | 54.55 % | 0 |
| SOCIAL_SECURITY_NUMBER | 126 | 68.18 % | 100.00 % | 81.08 % | 57.58 % | 0 |
| SWIFT_BIC | 137 | 81.32 % | 100.00 % | 89.70 % | 73.02 % | 0 |

## Résultats par type : max_f1

Stratégie `per_type`, seuil `0.95`.

| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 100.00 % | 71.43 % | 83.33 % | 100.00 % | 20 |
| API_KEY | 134 | 100.00 % | 93.75 % | 96.77 % | 100.00 % | 4 |
| BANK_ROUTING_NUMBER | 114 | 100.00 % | 93.33 % | 96.55 % | 100.00 % | 4 |
| CREDIT_CARD | 136 | 100.00 % | 82.86 % | 90.62 % | 100.00 % | 12 |
| CREDIT_DEBIT_CARD | 136 | 100.00 % | 84.29 % | 91.47 % | 100.00 % | 11 |
| CVV | 130 | 100.00 % | 84.13 % | 91.38 % | 100.00 % | 10 |
| DRIVER_LICENSE_NUMBER | 136 | 98.39 % | 96.83 % | 97.60 % | 98.63 % | 2 |
| IBAN | 121 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IBAN_CODE | 121 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IPV4 | 130 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IPV6 | 131 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |
| IP_ADDRESS | 131 | 98.48 % | 97.01 % | 97.74 % | 98.44 % | 2 |
| NATIONAL_ID | 136 | 100.00 % | 84.62 % | 91.67 % | 100.00 % | 10 |
| PASSWORD | 143 | 100.00 % | 91.04 % | 95.31 % | 100.00 % | 6 |
| PIN | 142 | 100.00 % | 80.28 % | 89.06 % | 100.00 % | 14 |
| SOCIALNUM | 126 | 100.00 % | 96.67 % | 98.31 % | 100.00 % | 2 |
| SOCIAL_SECURITY_NUMBER | 126 | 100.00 % | 93.33 % | 96.55 % | 100.00 % | 4 |
| SWIFT_BIC | 137 | 100.00 % | 100.00 % | 100.00 % | 100.00 % | 0 |

## Temps de calcul

| Stratégie | Prédictions | Latence médiane | Latence p95 | Temps cumulé |
|---|---:|---:|---:|---:|
| general | 3600 | 11.57 s | 13.22 s | 709.5 min |
| per_type | 3600 | 11.82 s | 23.75 s | 903.0 min |
| per_type_financial_only | 1240 | 12.41 s | 32.91 s | 335.6 min |
| concise | 1240 | 10.12 s | 11.31 s | 209.6 min |
| checklist | 3600 | 13.54 s | 35.37 s | 1074.5 min |
| few_shot | 1240 | 11.67 s | 23.48 s | 309.9 min |

## Protocole et limites

- Modèle : `qwen3.8-flash-next-iq3_s`, paramètres `{'chat_template_kwargs': {'enable_thinking': False}}`, contexte `32768`.
- Dataset : `b08d9c5da559610d6d4da7a05e051a8cd73e2ef6288742e4e8498b5122f3f5df` ; 1240 candidats de calibrage et 2360 de test, avec exactement les partitions OpenJev.
- Même grille de seuils et même fonction d'utilité que la campagne OpenJev.
- Le modèle génère un verdict JSON et une confiance déclarée. Cette confiance n'est pas directement comparable au score conditionnel entre options d'OpenJev ; chaque juge est donc calibré séparément.
- Les textes des corpus sont synthétiques et les faux positifs construits. Les résultats ne mesurent pas la fréquence réelle des erreurs en production.
- Une seule exécution par candidat ; `temperature: 0` ne garantit pas une parfaite reproductibilité du serveur LM Studio.

## Reproduire

Depuis la racine du dépôt :

```bash
caffeinate -i pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py --judge qwen3.8-flash-next --output /Users/Thibaut.Vuillaume/Workspace/ai-sentinel/benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/qwen --keep-loaded
```

Les résultats sont repris automatiquement. `--report-only` régénère le rapport sans charger le modèle. `--keep-loaded` utilise le modèle déjà servi.

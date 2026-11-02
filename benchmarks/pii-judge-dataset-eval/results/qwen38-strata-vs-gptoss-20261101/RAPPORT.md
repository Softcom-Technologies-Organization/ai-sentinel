# Qwen3.8-Flash-Next / Strata contre GPT-OSS-120B

Même benchmark existant : 3 600 candidats, 18 types ; six stratégies sur 1 240 cas de calibrage, puis 2 360 cas de test. Le meilleur F1 est choisi uniquement sur le calibrage.

Les scores ci-dessous concernent le jugement de candidats PII et leur conservation après filtrage.

## max_f1

| Modèle | Prompt | Seuil | P | R | F1 | Erreurs HTTP |
|---|---|---:|---:|---:|---:|---:|
| Qwen / Strata | per_type | 0.95 | 99.82% | 91.56% | 95.51% | 0 |
| GPT-OSS | per_type | 0.9 | 86.90% | 91.98% | 89.37% | 2 |

IC 95 % de l'écart F1 Qwen − GPT-OSS : [+4.72, +7.44] points. 1 000 rééchantillonnages appariés par groupe de documents, graine identique au benchmark.

| PII | n | P Qwen | R Qwen | F1 Qwen | P GPT | R GPT | F1 GPT | Δ F1 (points) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 100.00% | 71.43% | 83.33% | 91.23% | 74.29% | 81.89% | +1.44 |
| API_KEY | 134 | 100.00% | 93.75% | 96.77% | 64.89% | 95.31% | 77.22% | +19.56 |
| BANK_ROUTING_NUMBER | 114 | 100.00% | 93.33% | 96.55% | 80.56% | 96.67% | 87.88% | +8.67 |
| CREDIT_CARD | 136 | 100.00% | 82.86% | 90.62% | 98.46% | 91.43% | 94.81% | -4.19 |
| CREDIT_DEBIT_CARD | 136 | 100.00% | 84.29% | 91.47% | 100.00% | 91.43% | 95.52% | -4.05 |
| CVV | 130 | 100.00% | 84.13% | 91.38% | 90.62% | 92.06% | 91.34% | +0.04 |
| DRIVER_LICENSE_NUMBER | 136 | 98.39% | 96.83% | 97.60% | 90.91% | 95.24% | 93.02% | +4.58 |
| IBAN | 121 | 100.00% | 100.00% | 100.00% | 89.39% | 89.39% | 89.39% | +10.61 |
| IBAN_CODE | 121 | 100.00% | 100.00% | 100.00% | 89.86% | 93.94% | 91.85% | +8.15 |
| IPV4 | 130 | 100.00% | 100.00% | 100.00% | 89.19% | 100.00% | 94.29% | +5.71 |
| IPV6 | 131 | 100.00% | 100.00% | 100.00% | 84.09% | 98.67% | 90.80% | +9.20 |
| IP_ADDRESS | 131 | 98.48% | 97.01% | 97.74% | 87.01% | 100.00% | 93.06% | +4.69 |
| NATIONAL_ID | 136 | 100.00% | 84.62% | 91.67% | 93.33% | 86.15% | 89.60% | +2.07 |
| PASSWORD | 143 | 100.00% | 91.04% | 95.31% | 80.00% | 95.52% | 87.07% | +8.24 |
| PIN | 142 | 100.00% | 80.28% | 89.06% | 85.07% | 80.28% | 82.61% | +6.45 |
| SOCIALNUM | 126 | 100.00% | 96.67% | 98.31% | 87.88% | 96.67% | 92.06% | +6.24 |
| SOCIAL_SECURITY_NUMBER | 126 | 100.00% | 93.33% | 96.55% | 86.57% | 96.67% | 91.34% | +5.21 |
| SWIFT_BIC | 137 | 100.00% | 100.00% | 100.00% | 88.73% | 85.14% | 86.90% | +13.10 |
## general_0.8

| Modèle | Prompt | Seuil | P | R | F1 | Erreurs HTTP |
|---|---|---:|---:|---:|---:|---:|
| Qwen / Strata | general | 0.8 | 98.53% | 89.64% | 93.88% | 0 |
| GPT-OSS | general | 0.8 | 80.68% | 84.80% | 82.69% | 0 |

IC 95 % de l'écart F1 Qwen − GPT-OSS : [+9.41, +12.91] points. 1 000 rééchantillonnages appariés par groupe de documents, graine identique au benchmark.

| PII | n | P Qwen | R Qwen | F1 Qwen | P GPT | R GPT | F1 GPT | Δ F1 (points) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 98.11% | 74.29% | 84.55% | 80.00% | 74.29% | 77.04% | +7.52 |
| API_KEY | 134 | 100.00% | 89.06% | 94.21% | 58.10% | 95.31% | 72.19% | +22.03 |
| BANK_ROUTING_NUMBER | 114 | 96.36% | 88.33% | 92.17% | 86.15% | 93.33% | 89.60% | +2.57 |
| CREDIT_CARD | 136 | 96.36% | 75.71% | 84.80% | 100.00% | 72.86% | 84.30% | +0.50 |
| CREDIT_DEBIT_CARD | 136 | 96.55% | 80.00% | 87.50% | 98.08% | 72.86% | 83.61% | +3.89 |
| CVV | 130 | 100.00% | 84.13% | 91.38% | 85.71% | 95.24% | 90.23% | +1.15 |
| DRIVER_LICENSE_NUMBER | 136 | 96.72% | 93.65% | 95.16% | 70.77% | 73.02% | 71.88% | +23.29 |
| IBAN | 121 | 100.00% | 100.00% | 100.00% | 86.11% | 93.94% | 89.86% | +10.14 |
| IBAN_CODE | 121 | 100.00% | 100.00% | 100.00% | 86.11% | 93.94% | 89.86% | +10.14 |
| IPV4 | 130 | 100.00% | 100.00% | 100.00% | 84.42% | 98.48% | 90.91% | +9.09 |
| IPV6 | 131 | 100.00% | 100.00% | 100.00% | 85.06% | 98.67% | 91.36% | +8.64 |
| IP_ADDRESS | 131 | 98.48% | 97.01% | 97.74% | 77.91% | 100.00% | 87.58% | +10.16 |
| NATIONAL_ID | 136 | 91.67% | 84.62% | 88.00% | 74.32% | 84.62% | 79.14% | +8.86 |
| PASSWORD | 143 | 100.00% | 89.55% | 94.49% | 72.53% | 98.51% | 83.54% | +10.94 |
| PIN | 142 | 98.28% | 80.28% | 88.37% | 81.08% | 84.51% | 82.76% | +5.61 |
| SOCIALNUM | 126 | 100.00% | 91.67% | 95.65% | 78.38% | 48.33% | 59.79% | +35.86 |
| SOCIAL_SECURITY_NUMBER | 126 | 100.00% | 90.00% | 94.74% | 82.61% | 63.33% | 71.70% | +23.04 |
| SWIFT_BIC | 137 | 100.00% | 95.95% | 97.93% | 86.96% | 81.08% | 83.92% | +14.01 |
## recall_priority

| Modèle | Prompt | Seuil | P | R | F1 | Erreurs HTTP |
|---|---|---:|---:|---:|---:|---:|
| Qwen / Strata | checklist | 0.975 | 77.84% | 99.75% | 87.44% | 0 |
| GPT-OSS | per_type | 0.999 | 51.46% | 99.92% | 67.94% | 2 |

IC 95 % de l'écart F1 Qwen − GPT-OSS : [+17.99, +20.87] points. 1 000 rééchantillonnages appariés par groupe de documents, graine identique au benchmark.

| PII | n | P Qwen | R Qwen | F1 Qwen | P GPT | R GPT | F1 GPT | Δ F1 (points) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 72.63% | 98.57% | 83.64% | 53.85% | 100.00% | 70.00% | +13.64 |
| API_KEY | 134 | 70.33% | 100.00% | 82.58% | 47.76% | 100.00% | 64.65% | +17.93 |
| BANK_ROUTING_NUMBER | 114 | 78.95% | 100.00% | 88.24% | 52.63% | 100.00% | 68.97% | +19.27 |
| CREDIT_CARD | 136 | 84.34% | 100.00% | 91.50% | 51.47% | 100.00% | 67.96% | +23.54 |
| CREDIT_DEBIT_CARD | 136 | 75.27% | 100.00% | 85.89% | 52.24% | 100.00% | 68.63% | +17.26 |
| CVV | 130 | 73.26% | 100.00% | 84.56% | 48.84% | 100.00% | 65.62% | +18.94 |
| DRIVER_LICENSE_NUMBER | 136 | 74.12% | 100.00% | 85.14% | 46.32% | 100.00% | 63.32% | +21.82 |
| IBAN | 121 | 79.27% | 98.48% | 87.84% | 55.00% | 100.00% | 70.97% | +16.87 |
| IBAN_CODE | 121 | 77.38% | 98.48% | 86.67% | 55.00% | 100.00% | 70.97% | +15.70 |
| IPV4 | 130 | 100.00% | 100.00% | 100.00% | 55.93% | 100.00% | 71.74% | +28.26 |
| IPV6 | 131 | 100.00% | 100.00% | 100.00% | 57.69% | 100.00% | 73.17% | +26.83 |
| IP_ADDRESS | 131 | 97.10% | 100.00% | 98.53% | 51.94% | 100.00% | 68.37% | +30.16 |
| NATIONAL_ID | 136 | 74.71% | 100.00% | 85.53% | 47.79% | 100.00% | 64.68% | +20.85 |
| PASSWORD | 143 | 72.83% | 100.00% | 84.28% | 49.26% | 100.00% | 66.01% | +18.27 |
| PIN | 142 | 70.30% | 100.00% | 82.56% | 52.21% | 100.00% | 68.60% | +13.96 |
| SOCIALNUM | 126 | 66.67% | 100.00% | 80.00% | 47.62% | 100.00% | 64.52% | +15.48 |
| SOCIAL_SECURITY_NUMBER | 126 | 68.18% | 100.00% | 81.08% | 48.00% | 100.00% | 64.86% | +16.22 |
| SWIFT_BIC | 137 | 81.32% | 100.00% | 89.70% | 54.07% | 98.65% | 69.86% | +19.84 |

## Limites

Corpus et faux positifs synthétiques. Les incertitudes par type ne sont pas testées séparément. Quantifications et moteurs différents : IQ3_S / Strata et MXFP4 / LM Studio. Qwen utilise un contexte de 32 768 tokens, GPT-OSS 8 192 ; même budget de sortie de 2 048 tokens, mêmes prompts et quatre requêtes simultanées. Les erreurs et abstentions suivent la règle de conservation du benchmark.

[Résultats JSON](comparison.json) · [Détail CSV](per-type.csv)

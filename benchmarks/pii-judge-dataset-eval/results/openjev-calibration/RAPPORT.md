# OpenJev : comparaison des prompts et calibrage

Rapport généré le 2026-09-20T07:41:02.110109+00:00.

## Résultats sur les documents réservés

Les réglages ci-dessous sont choisis exclusivement sur le lot de calibrage, puis figés avant de calculer les prédictions du lot de test.

| Réglage | Seuil FP | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Baseline actuelle, sans seuil | 0 | 84.75 % | 77.53 % | 80.98 % | 85.64 % | 269 | -1.457 |
| Baseline au seuil de production | 0.8 | 75.20 % | 87.89 % | 81.05 % | 70.16 % | 145 | -0.545 |
| recall_priority | 0 | 59.59 % | 99.67 % | 74.59 % | 30.44 % | 4 | 0.270 |
| max_f1 | 0.7 | 79.45 % | 84.29 % | 81.80 % | 77.56 % | 188 | -0.841 |

La classe positive est une vraie entité conservée. Un FP confirmé et rejeté par le juge compte comme un vrai négatif (TN). Le rappel est celui du filtre sur les candidats reçus, pas celui de la chaîne de détection complète.

| Réglage | TP | FP | TN | FN | IC 95 % précision | IC 95 % rappel | IC 95 % F1 |
|---|---:|---:|---:|---:|---|---|---|
| Baseline actuelle, sans seuil | 928 | 167 | 996 | 269 | 82.47 % – 86.98 % | 74.74 % – 79.93 % | 78.98 % – 82.70 % |
| Baseline au seuil de production | 1052 | 347 | 816 | 145 | 72.72 % – 77.66 % | 85.75 % – 89.90 % | 79.19 % – 82.82 % |
| recall_priority | 1193 | 809 | 354 | 4 | 57.23 % – 61.99 % | 99.24 % – 100.00 % | 72.72 % – 76.41 % |
| max_f1 | 1009 | 261 | 902 | 188 | 77.08 % – 81.78 % | 81.97 % – 86.53 % | 79.95 % – 83.55 % |

IC 95 % : bootstrap percentile de 1 000 rééchantillonnages par groupe de documents, avec les candidats et alias d'un groupe tirés ensemble.

## Protocole reproductible

- Modèle : `Qwen/Qwen3.5-4B`, révision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`.
- Environnement : `{"source": "Qwen/Qwen3.5-4B", "revision": "851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a", "device": "mps", "dtype": "float16", "torch_version": "2.13.0", "transformers_version": "5.14.1", "scorer_sha256": "f6c3fdf64d50a9b6667c1cbd57e1349b46197063b0be10908a3bde7deddbb87a", "runner_sha256": "f79fbba3b938c7f5d1038729d2de9e03949dde970e3b1308b3b0c765429e28a6"}`.
- Dataset : `/Users/Thibaut.Vuillaume/Workspace/ai-sentinel/benchmarks/pii-judge-dataset-eval/datasets/large.jsonl`, SHA-256 `b08d9c5da559610d6d4da7a05e051a8cd73e2ef6288742e4e8498b5122f3f5df`.
- Total : 3600 candidats ; calibrage : 1240 ; test : 2360.
- Séparation déterministe par document source et union des contextes exactement identiques ; 35 % des groupes pour le calibrage. Les lignes source redémarrant par fichier sans nom de fichier exporté, les mêmes dataset/row sont regroupés conservativement, même s'ils correspondent à plusieurs documents.
- Les tailles light/normal/large sont emboîtées et ne constituent pas des lots indépendants.
- `recall_priority` maximise l'utilité `(TN − 10×FN)/nombre de négatifs`, sous rappel empirique ≥ 99 % au calibrage. Cette contrainte n'est pas une garantie sur le test.
- `max_f1` maximise le F1 au calibrage et sert de comparaison, sans contrainte de rappel.
- Grille de seuils déclarée avant les essais : `(0.0, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.925, 0.95, 0.975, 0.99, 0.995, 0.999, 1.000001)`. Le dernier seuil désactive tout rejet.
- Rejet uniquement si FALSE_POSITIVE est le verdict majoritaire ET son score atteint le seuil. UNSURE est conservé. Les scores sont conditionnels aux options et non calibrés comme probabilités d'erreur ; seul le seuil de décision est ajusté.

## Prompts comparés sur le lot de calibrage

- `baseline` : entrée actuelle d'OpenJev, inchangée.
- `policy` : tronc court et garde-fous existants, adaptés au choix d'une option.
- `per_type` : même tronc, avec le corps existant du type jugé.
- `examples` : même version par type et six exemples préexistants, indépendants du dataset.
- Même modèle, mêmes trois options et même ordre. Aucun label gold, famille FP ou indice de difficulté n'est fourni au modèle. Tous les critères exacts sont archivés dans `criteria.json`, avec un hash du prompt rendu par prédiction.

| Variante | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| baseline | 0 | 81.95 % | 73.80 % | 77.66 % | 84.62 % | 158 | -1.634 |
| baseline | 0.8 | 73.44 % | 85.74 % | 79.11 % | 70.64 % | 86 | -0.644 |
| baseline / rappel prioritaire | 0.995 | 48.91 % | 100.00 % | 65.69 % | 1.10 % | 0 | 0.011 |
| policy | 0 | 63.31 % | 95.02 % | 75.99 % | 47.88 % | 30 | 0.008 |
| policy | 0.8 | 51.33 % | 99.17 % | 67.65 % | 10.99 % | 5 | 0.031 |
| policy / rappel prioritaire | 0.7 | 53.73 % | 99.17 % | 69.70 % | 19.15 % | 5 | 0.113 |
| per_type | 0 | 59.19 % | 99.83 % | 74.32 % | 34.85 % | 1 | 0.333 |
| per_type | 0.8 | 50.89 % | 100.00 % | 67.45 % | 8.63 % | 0 | 0.086 |
| per_type / rappel prioritaire | 0 | 59.19 % | 99.83 % | 74.32 % | 34.85 % | 1 | 0.333 |
| examples | 0 | 56.94 % | 100.00 % | 72.56 % | 28.41 % | 0 | 0.284 |
| examples | 0.8 | 49.63 % | 100.00 % | 66.34 % | 3.92 % | 0 | 0.039 |
| examples / rappel prioritaire | 0 | 56.94 % | 100.00 % | 72.56 % | 28.41 % | 0 | 0.284 |

## Temps de calcul

| Variante | Prédictions mesurées | Tokens médians | Latence médiane | Latence p95 | Temps cumulé |
|---|---:|---:|---:|---:|---:|
| baseline | 3600 | 379 | 0.364 s | 0.488 s | 22.0 min |
| policy | 1240 | 940 | 0.702 s | 0.811 s | 14.8 min |
| per_type | 3600 | 1582 | 1.181 s | 1.361 s | 71.1 min |
| examples | 1240 | 2300 | 1.981 s | 2.813 s | 45.1 min |

Mesures sérielles incluant tokenisation, transfert et retour des scores ; chargement exclu. Les variantes non sélectionnées ne passent que sur le calibrage, donc ces latences restent descriptives et ne constituent pas une comparaison appariée.

## Détail du réglage recall_priority

Variante `per_type`, seuil `0.0`.

### Type

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 55.12 % | 100.00 % | 71.07 % | 5.00 % | 0 |
| API_KEY | 134 | 47.76 % | 100.00 % | 64.65 % | 0.00 % | 0 |
| BANK_ROUTING_NUMBER | 114 | 64.52 % | 100.00 % | 78.43 % | 38.89 % | 0 |
| CREDIT_CARD | 136 | 56.10 % | 98.57 % | 71.50 % | 18.18 % | 1 |
| CREDIT_DEBIT_CARD | 136 | 57.02 % | 98.57 % | 72.25 % | 21.21 % | 1 |
| CVV | 130 | 60.58 % | 100.00 % | 75.45 % | 38.81 % | 0 |
| DRIVER_LICENSE_NUMBER | 136 | 47.73 % | 100.00 % | 64.62 % | 5.48 % | 0 |
| IBAN | 121 | 89.04 % | 98.48 % | 93.53 % | 85.45 % | 1 |
| IBAN_CODE | 121 | 86.84 % | 100.00 % | 92.96 % | 81.82 % | 0 |
| IPV4 | 130 | 68.04 % | 100.00 % | 80.98 % | 51.56 % | 0 |
| IPV6 | 131 | 71.43 % | 100.00 % | 83.33 % | 46.43 % | 0 |
| IP_ADDRESS | 131 | 68.37 % | 100.00 % | 81.21 % | 51.56 % | 0 |
| NATIONAL_ID | 136 | 49.24 % | 100.00 % | 65.99 % | 5.63 % | 0 |
| PASSWORD | 143 | 47.18 % | 100.00 % | 64.11 % | 1.32 % | 0 |
| PIN | 142 | 55.56 % | 98.59 % | 71.07 % | 21.13 % | 1 |
| SOCIALNUM | 126 | 51.72 % | 100.00 % | 68.18 % | 15.15 % | 0 |
| SOCIAL_SECURITY_NUMBER | 126 | 57.14 % | 100.00 % | 72.73 % | 31.82 % | 0 |
| SWIFT_BIC | 137 | 75.51 % | 100.00 % | 86.05 % | 61.90 % | 0 |

### Langue déclarée par la source

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| English | 1656 | 61.91 % | 99.65 % | 76.37 % | 34.09 % | 3 |
| French | 704 | 54.43 % | 99.71 % | 70.42 % | 22.47 % | 1 |

### Corpus

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ai4privacy | 781 | 63.00 % | 100.00 % | 77.30 % | 18.90 % | 0 |
| gretelai | 1579 | 57.68 % | 99.46 % | 73.01 % | 34.97 % | 4 |

### Famille de faux positifs

| Famille | Nombre | Rejetés | Taux de rejet |
|---|---:|---:|---:|
| competing_type | 361 | 116 | 32.13 % |
| format_violation | 210 | 98 | 46.67 % |
| placeholder | 122 | 32 | 26.23 % |
| type_mention | 470 | 108 | 22.98 % |

### Difficulté des vrais positifs

| Difficulté | Nombre | Conservés | Rétention |
|---|---:|---:|---:|
| misleading_clue | 76 | 75 | 98.68 % |
| no_clue | 477 | 474 | 99.37 % |
| with_clue | 644 | 644 | 100.00 % |

## Détail du réglage max_f1

Variante `baseline`, seuil `0.7`.

### Type

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ACCOUNT_NUMBER | 130 | 87.67 % | 91.43 % | 89.51 % | 85.00 % | 6 |
| API_KEY | 134 | 77.46 % | 85.94 % | 81.48 % | 77.14 % | 9 |
| BANK_ROUTING_NUMBER | 114 | 96.67 % | 96.67 % | 96.67 % | 96.30 % | 2 |
| CREDIT_CARD | 136 | 79.10 % | 75.71 % | 77.37 % | 78.79 % | 17 |
| CREDIT_DEBIT_CARD | 136 | 64.29 % | 77.14 % | 70.13 % | 54.55 % | 16 |
| CVV | 130 | 75.93 % | 65.08 % | 70.09 % | 80.60 % | 22 |
| DRIVER_LICENSE_NUMBER | 136 | 91.23 % | 82.54 % | 86.67 % | 93.15 % | 11 |
| IBAN | 121 | 95.24 % | 90.91 % | 93.02 % | 94.55 % | 6 |
| IBAN_CODE | 121 | 96.92 % | 95.45 % | 96.18 % | 96.36 % | 3 |
| IPV4 | 130 | 93.55 % | 87.88 % | 90.62 % | 93.75 % | 8 |
| IPV6 | 131 | 97.40 % | 100.00 % | 98.68 % | 96.43 % | 0 |
| IP_ADDRESS | 131 | 96.97 % | 95.52 % | 96.24 % | 96.88 % | 3 |
| NATIONAL_ID | 136 | 68.67 % | 87.69 % | 77.03 % | 63.38 % | 8 |
| PASSWORD | 143 | 56.64 % | 95.52 % | 71.11 % | 35.53 % | 3 |
| PIN | 142 | 62.50 % | 84.51 % | 71.86 % | 49.30 % | 11 |
| SOCIALNUM | 126 | 52.17 % | 80.00 % | 63.16 % | 33.33 % | 12 |
| SOCIAL_SECURITY_NUMBER | 126 | 85.71 % | 30.00 % | 44.44 % | 95.45 % | 42 |
| SWIFT_BIC | 137 | 98.48 % | 87.84 % | 92.86 % | 98.41 % | 9 |

### Langue déclarée par la source

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| English | 1656 | 83.03 % | 83.80 % | 83.41 % | 81.58 % | 139 |
| French | 704 | 71.78 % | 85.55 % | 78.06 % | 68.77 % | 49 |

### Corpus

| Groupe | n | Précision | Rappel | F1 | FP éliminés | TP perdus |
|---|---:|---:|---:|---:|---:|---:|
| ai4privacy | 781 | 77.14 % | 83.44 % | 80.17 % | 65.85 % | 75 |
| gretelai | 1579 | 80.90 % | 84.81 % | 82.81 % | 82.16 % | 113 |

### Famille de faux positifs

| Famille | Nombre | Rejetés | Taux de rejet |
|---|---:|---:|---:|
| competing_type | 361 | 275 | 76.18 % |
| format_violation | 210 | 183 | 87.14 % |
| placeholder | 122 | 107 | 87.70 % |
| type_mention | 470 | 337 | 71.70 % |

### Difficulté des vrais positifs

| Difficulté | Nombre | Conservés | Rétention |
|---|---:|---:|---:|
| misleading_clue | 76 | 56 | 73.68 % |
| no_clue | 477 | 378 | 79.25 % |
| with_clue | 644 | 575 | 89.29 % |

## Limites de l'interprétation

- Gretel et AI4Privacy contiennent des textes synthétiques. Les négatifs sont construits dans ces textes ; ce ne sont pas des erreurs effectivement émises par les détecteurs en production. Ce benchmark mesure ces cas définis, pas leur fréquence réelle.
- Le mélange positif/négatif est artificiellement équilibré. La précision en production dépendra de la proportion réelle de faux positifs avant filtrage.
- Les annotations d'origine peuvent contredire le contexte ou les règles de format. Les cas `misleading_clue` restent dans le score principal et sont affichés séparément. Aucun label n'a été corrigé pour améliorer les résultats.
- Les alias partagent des candidats. Les effectifs ne sont donc pas autant de cas indépendants ; les groupes restent ensemble pour la séparation et le bootstrap.
- Les noms de langue viennent des fichiers source ; ils ne garantissent pas la langue effective de chaque texte.
- Le lot de test est réservé au calibrage de cette campagne, mais il ne prouve pas l'absence d'exposition antérieure des auteurs des prompts ou du modèle aux corpus.
- Une seule exécution par candidat et variante ; aucun intervalle ne couvre les changements de matériel, de corpus ou les erreurs systématiques du gold.

## Reproduire

Depuis la racine du dépôt :

```bash
HF_HOME=/private/tmp/openjev-hf-cache HF_HUB_OFFLINE=1 pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_openjev.py --device mps
```

Les sorties se reprennent automatiquement. Les empreintes du dataset, des critères, du code et du modèle interdisent une reprise avec des entrées différentes. `--report-only` régénère le rapport sans charger le modèle. Utiliser `--output` pour une campagne distincte.

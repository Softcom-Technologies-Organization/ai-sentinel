# Archive du benchmark et choix pour la RC DGNSI

Cette archive conserve les résultats Qwen/Strata et GPT-OSS, leur protocole,
les réponses brutes, le dataset synthétique et le code exact de l'évaluation.
Le [rapport comparatif](RAPPORT.md) reste inchangé.

La RC `release/v1.2.0-rc.2-strata-qwen` reprend le meilleur F1 mesuré :
`qwen3.8-flash-next-iq3_s`, stratégie `per_type`, seuil `0.95`.
Sur les 2 360 candidats de test : 1 096 vrais positifs, 2 faux positifs,
1 161 vrais négatifs et 101 faux négatifs ; précision 99,82 %, rappel 91,56 %,
F1 95,51 %. Le mode initial de l'application reste `PROPOSE` : ces résultats
ne justifient pas de rejeter automatiquement les détections d'un nouveau client.
La variante `checklist` favorisant le rappel reste archivée avec ses résultats.

Le contrat d'inférence est : contexte Strata `32768`, parallélisme `4`,
température `0`, sortie maximale `2048`, `enable_thinking=false`, réponse JSON
structurée avec `reason`, `confidence`, puis `verdict`. Les prompts par type
sont insérés avant `GARDE-FOUS SUR LES MOTIFS`, comme dans le benchmark.
L'application conserve les prompts exacts dans
[sa configuration](../../../../pii-detector-service/config/prompts/judge/per-type-v1/system.md)
et vérifie leurs empreintes dans
[un test de contrat](../../../../pii-detector-service/tests/unit/test_judge_benchmark_contract.py).

Le dataset contient 3 600 candidats de 18 types : 1 240 pour la calibration,
2 360 pour le test, sans document partagé entre les deux partitions.
SHA-256 : `b08d9c5da559610d6d4da7a05e051a8cd73e2ef6288742e4e8498b5122f3f5df`.
Le [contrôle indépendant](validation.json) conserve notamment les erreurs HTTP
et les intervalles de confiance. Ces mesures portent sur le jugement de candidats
synthétiques déjà extraits, pas sur le rappel d'un scan Confluence complet.

## Recalcul hors ligne

Depuis la racine du dépôt, avec l'environnement Python du détecteur installé :

```bash
pii-detector-service/.venv/bin/python -B benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/snapshot/benchmarks/pii-judge-dataset-eval/compare_calibrated_judges.py benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/qwen benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/gpt /tmp/ai-sentinel-benchmark-replay
```

Lors de la préparation de la RC, ce recalcul a reproduit à l'octet près
`comparison.json`, `per-type.csv` et `RAPPORT.md`. Les 34 fichiers du manifeste
`snapshot.sha256.json` ont également été vérifiés. Les caches Python et fichiers
de suivi des processus ne font pas partie de l'archive versionnée.

`run.sh` est le script historique de lancement : il contient des chemins propres
au poste et peut arrêter ou charger des modèles. Il est conservé pour la traçabilité,
pas comme commande de déploiement. Utiliser le
[guide DGNSI](../../../../docker/dgnsi/README.md) pour la RC.

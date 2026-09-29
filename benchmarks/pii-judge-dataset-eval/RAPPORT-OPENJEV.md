# OpenJev comme juge PII : calibration et validation

## Conclusion

La calibration et la validation sont terminées. Pour un post-filtre PII où perdre une vraie entité coûte beaucoup plus cher que laisser passer un faux positif, le meilleur réglage testé est **le prompt par type au seuil 0** :

- **Précision : 59,59 %** — IC 95 % : 57,23 % à 61,99 %.
- **Rappel : 99,67 %** — IC 95 % : 99,24 % à 100,00 %.
- **F1 : 74,59 %** — IC 95 % : 72,72 % à 76,41 %.
- **354 faux positifs éliminés sur 1 163**, soit 30,44 %.
- **4 vrais positifs perdus sur 1 197**, soit 0,33 %. Ces quatre labels correspondent à trois contextes distincts, car une même valeur est comptée sous deux alias de carte bancaire.
- **Utilité λ=10 : +0,270**. C'est le seul des deux réglages retenus dont le bénéfice reste positif quand une vraie PII perdue coûte dix faux positifs éliminés.

Si l'objectif est uniquement le meilleur F1, le prompt initial au seuil 0,7 obtient **P = 79,45 %, R = 84,29 %, F1 = 81,80 %**. Il supprime 77,56 % des faux positifs, mais aussi **188 vraies PII sur 1 197**. Son utilité λ=10 est négative (-0,841) : ce n'est pas le réglage que je recommande pour ai-sentinel.

**Conclusion pratique : oui, OpenJev gagne nettement à recevoir les règles du type d'entité pour l'objectif de sécurité du projet.** Les six exemples ajoutés au prompt n'apportent pas de gain sur la calibration. Aucun réglage de production n'a été modifié par cette campagne.

Le [rapport détaillé généré](results/openjev-calibration/RAPPORT.md) contient les matrices de confusion, les intervalles de confiance, les résultats par type et par famille de faux positif.

## Ce qui a été réellement testé

OpenJev/SemIf est utilisé avec **Qwen3.5-4B**, en local sur le GPU du Mac, en `float16`. Il lit les scores des trois options `TRUE_POSITIVE`, `FALSE_POSITIVE` et `UNSURE`, sans générer une réponse textuelle. Il utilise donc bien un LLM, même si le mécanisme de jugement n'est pas une conversation générative.

Le [script de calibration](calibrate_openjev.py) réutilise le modèle, le scorer et les prompts déjà présents. Aucun entraînement ni nouveau modèle n'a été ajouté.

- Dataset `large` : **3 600 candidats**, dont 1 800 positifs et 1 800 négatifs, sur 18 types.
- Calibration : **1 240 candidats**, dont 603 positifs et 637 négatifs.
- Test réservé : **2 360 candidats**, dont 1 197 positifs et 1 163 négatifs.
- Séparation par groupe de documents, en conservant ensemble les alias et les contextes strictement identiques. Aucun groupe partagé entre calibration et test.
- Quatre variantes de prompt et quinze seuils déclarés avant les essais : **60 réglages** comparés exclusivement sur la calibration.
- Deux choix figés avant le test : meilleur F1, et meilleure utilité sous rappel de calibration d'au moins 99 %. L'utilité compte une vraie PII perdue comme dix faux positifs éliminés.

Le rappel mesure ici la conservation des vrais candidats **déjà proposés au juge**, pas le rappel de toute la chaîne de détection. `UNSURE` reste conservé, conformément au comportement du benchmark existant : conservation ne signifie pas confirmation explicite par le juge.

## Résultats sur le test réservé

Les deux réglages ont été choisis sur la calibration puis figés avant toute prédiction du lot de test.

| Objectif | Variante | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Sécurité PII | Prompt par type | 0 | 59,59 % | 99,67 % | 74,59 % | 30,44 % | 4 | +0,270 |
| F1 maximal | Prompt initial | 0,7 | 79,45 % | 84,29 % | 81,80 % | 77,56 % | 188 | -0,841 |
| Seuil actuellement usuel | Prompt initial | 0,8 | 75,20 % | 87,89 % | 81,05 % | 70,16 % | 145 | -0,545 |

Le prompt par type conserve 184 vraies PII de plus que le réglage au meilleur F1, au prix de 548 faux positifs supplémentaires laissés passer. Le choix dépend donc du coût métier. Avec le rapport de coût λ=10 déjà retenu par le benchmark, le prompt par type est le bon choix.

Il est aussi plus lent : latence médiane de **1,181 s** par candidat, contre **0,364 s** pour le prompt initial, sur cette exécution locale MPS. Le chargement du modèle est exclu.

## Où le réglage recommandé fonctionne, et où il échoue

Le taux d'élimination des faux positifs varie fortement selon le type :

- IBAN : 85,45 % ; IBAN_CODE : 81,82 % ; SWIFT_BIC : 61,90 %.
- IP_ADDRESS : 51,56 % ; IPV4 : 51,56 % ; IPV6 : 46,43 %.
- API_KEY : 0,00 % ; PASSWORD : 1,32 % ; ACCOUNT_NUMBER : 5,00 % ; DRIVER_LICENSE_NUMBER : 5,48 % ; NATIONAL_ID : 5,63 %.

Le résultat global ne justifie donc pas d'activer OpenJev indistinctement sur tous les types. Sur ce dataset, il apporte surtout quelque chose aux types bancaires structurés et aux adresses IP. Une règle d'activation par type devra être validée sur des faux positifs réellement issus des détecteurs avant un usage en production.

Par famille de négatifs, le réglage recommandé rejette 46,67 % des violations de format, 32,13 % des valeurs d'un type concurrent, 26,23 % des placeholders et seulement 22,98 % des mentions du nom du type. Le français déclaré par la source est moins bien filtré que l'anglais : 22,47 % contre 34,09 % des faux positifs éliminés.

## Ce que la calibration a choisi

Pour chaque variante, le tableau montre son meilleur réglage respectant le rappel minimal de 99 % sur la calibration.

| Variante | Seuil de rejet | Précision | Rappel | F1 | Faux positifs éliminés | Vrais positifs perdus |
|---|---:|---:|---:|---:|---:|---:|
| Prompt initial | 0,995 | 48,91 % | 100,00 % | 65,69 % | 1,10 % | 0 / 603 |
| Consignes générales | 0,7 | 53,73 % | 99,17 % | 69,70 % | 19,15 % | 5 / 603 |
| Consignes + définition par type | 0 | 59,19 % | 99,83 % | 74,32 % | 34,85 % | 1 / 603 |
| Définition par type + six exemples | 0 | 56,94 % | 100,00 % | 72,56 % | 28,41 % | 0 / 603 |

Le choix orienté rappel est donc **`per_type`, seuil 0**. Cela signifie : rejeter lorsque `FALSE_POSITIVE` est l'option gagnante, sans exiger en plus un score de 0,8. Cela ne signifie pas rejeter tous les candidats. Ces scores sont relatifs aux trois options ; un score de 0,8 n'est pas une probabilité démontrée de 80 % d'avoir raison.

Le meilleur F1 de calibration est **79,74 %**, avec le **prompt initial au seuil 0,7**. Mais il perd **109 vrais positifs sur 603**, soit un rappel de **81,92 %**. Ce compromis est très différent de celui recherché pour un filtre qui ne doit pas masquer des données personnelles.

**Un prompt par type aide donc dans cette expérience à préserver le rappel tout en filtrant une partie des faux positifs. Il ne maximise pas le F1.** Ajouter les six exemples existants rend le filtre encore plus conservateur et n'améliore pas l'utilité choisie. Ce constat ne signifie pas que tous les jeux d'exemples seraient inutiles.

## Sensibilité au nom du type

Sur les mêmes valeurs et contextes de calibration, le verdict change parfois avec un simple alias du type. Comparaison des trois verdicts bruts, sans appliquer de seuil :

| Alias comparés | Paires communes | Verdicts différents, prompt initial | Verdicts différents, prompt par type |
|---|---:|---:|---:|
| `CREDIT_CARD` / `CREDIT_DEBIT_CARD` | 64 | 12 | 7 |
| `IBAN` / `IBAN_CODE` | 79 | 5 | 6 |
| `SOCIALNUM` / `SOCIAL_SECURITY_NUMBER` | 74 | 33 | 14 |

Les définitions explicites réduisent fortement l'écart entre les deux libellés de numéro de sécurité sociale, sans rendre le modèle invariant aux alias. Pour ces deux types, les corps de prompt existants ne diffèrent que par leur nom en première ligne. Une normalisation des alias serait une piste à tester séparément, pas une amélioration démontrée par cette campagne.

## Fiabilité du ground truth et limites

Les négatifs explicites permettent réellement de mesurer les faux positifs du juge. Mais les textes proviennent de **corpus synthétiques Gretel et AI4Privacy** ; les négatifs ont été construits dans ces textes. Ils représentent des familles de difficultés, pas un échantillon d'erreurs réellement produites par les détecteurs sur Confluence.

Des annotations positives sont contestables. Exemples observés sur la calibration, sans correction des étiquettes :

- `9ed3632f5a3a8e82` : `121`, étiqueté CVV, est pris dans `Credit Card Number (last 4 digits): 1211`.
- `9568aef4aab78c2c` : `128`, étiqueté CVV, apparaît dans `Supplies and Materials: $128,000`. Ce cas est classé `no_clue`, pas `misleading_clue`.
- `b5711e14fbec1966` : `153`, étiqueté CVV, est présenté comme un code d'inscription à un webinaire. Le contexte ne démontre pas un code de carte bancaire.

Un juge qui rejette ces cas peut être pénalisé par le benchmark malgré une interprétation raisonnable du texte. **Les métriques mesurent l'accord avec ces annotations, pas une vérité humaine incontestable.** Aucun label n'a été changé après observation des réponses.

Cela n'explique pas toutes les erreurs : l'unique positif perdu par `per_type` sur la calibration (`e8ac33e636236f32`) est un numéro présenté explicitement comme une carte bancaire dans une phrase précisant qu'il n'est pas concerné par un redémarrage système. Le juge le rejette malgré cette désignation. Sans justification générée, on ne peut pas établir la cause exacte de ce verdict.

Autres limites à conserver en tête :

- La proportion 50/50 entre positifs et négatifs est artificielle. La précision en production dépendra de la proportion réelle de faux candidats.
- Les 18 types mesurés ne couvrent pas toutes les PII ni tous les types actifs du projet. Plusieurs sont des alias.
- Les identifiants source ne contiennent pas le nom du fichier alors que les numéros de ligne y redémarrent. Le découpage regroupe conservativement les mêmes couples corpus/ligne. Les ressemblances entre modèles de documents ne sont pas toutes éliminées.
- Les langues sont celles déclarées par les sources ; elles ne garantissent pas la langue effective de chaque extrait.
- Il s'agit d'une sélection parmi quatre formulations et une grille de seuils, pas d'une recherche exhaustive ni d'une optimisation des seuils pour chaque type.
- Une seule inférence par candidat et variante. Les intervalles statistiques ne couvrent pas les erreurs systématiques du gold ni un changement de corpus.

## Vérifications et reproduction

Les [tests du calibrateur](test_calibrate_openjev.py) passent, ainsi que les contrôles existants de construction du dataset. Les 60 réglages de calibration ont été vérifiés avec le calculateur existant du benchmark : les comptes et l'utilité concordent exactement. Les empreintes des 4 960 entrées de calibration et des 4 720 entrées de test ont aussi été vérifiées. Aucun groupe de documents n'est partagé entre calibration et test, et aucune réponse ne porte un statut d'erreur.

Depuis la racine du dépôt :

```bash
HF_HOME=/private/tmp/openjev-hf-cache HF_HUB_OFFLINE=1 pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_openjev.py --device mps
```

Le script reprend une campagne interrompue sans recalculer les réponses sauvegardées, et refuse une reprise dont les empreintes d'entrée, de code ou de modèle ont changé. Le cache temporaire du modèle doit encore exister ; sinon utiliser un `HF_HOME` contenant la même révision. `--output` permet de choisir un autre répertoire pour une nouvelle campagne.

Un essai de traitement par lots a également été exécuté avec le [script dédié](check_openjev_batch.py). Sur douze candidats de calibration, les lots de quatre ou huit étaient plus lents que le traitement sériel ; cette optimisation n'a donc pas été retenue. Les verdicts étaient identiques, mais certains scores numériques différaient légèrement.

Aucun réglage de production n'a été modifié.

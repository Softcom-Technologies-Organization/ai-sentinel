# Ministral-3B-PII : Q4_K_M contre Q8_0 — résultats

Campagne du 25 août 2026, MacBook Pro M5 Max 128 Go, LM Studio.
Corpus : 37 pages Confluence réelles (508 741 caractères, 179 465 tokens,
123 chunks), 1 287 PII de référence confirmées.

## Recommandation

Garde le Q8_0. Il est meilleur sur les deux axes de qualité à la fois : rappel
35,5 % contre 31,1 %, précision 76,6 % contre 74,8 %, F1 0,485 contre 0,440. Il
ne paie donc pas son rappel en faux positifs, contrairement à ce qu'on pouvait
craindre. Le Q4_K_M rend 24 % de débit de génération en échange de 4,5 points de
F1.

Sur ce corpus, Q8 trouve 69 PII que Q4 manque ; Q4 en trouve 13 que Q8 manque.
L'écart vient presque entièrement des adresses MAC et des IBAN.

Passe au Q4_K_M seulement si le temps de scan devient contraignant : sur ce
corpus il fait le travail en 11,7 min contre 16,3 min, soit 40 % de temps en
moins.

## Débit

Modèle chargé seul (`lms unload --all` avant chaque chargement), deux passes par
quantisation dans un ordre inversé pour neutraliser la dérive thermique.

| Mesure | Q4_K_M | Q8_0 | Rapport |
|---|---|---|---|
| Génération, séquentiel (passe production / passe sans plafond) | 97,5 / 99,4 tok/s | 77,2 / 81,4 tok/s | **1,24×** |
| Débit total à concurrence 4, prompt inclus | 510,9 tok/s | 431,7 tok/s | **1,18×** |
| Corpus complet, séquentiel | 10,4 / 11,7 min | 14,8 / 16,3 min | Q8 met 40 % de temps en plus |

Les deux passes par quantisation donnent la mesure du bruit : 2 % sur Q4, 5 % sur
Q8. L'écart de 24 % est très au-dessus.

Le temps mural n'est pas proportionnel au débit, parce que Q8 génère aussi plus
de texte : 79 313 tokens de complétion contre 69 567 sur la même passe, c'est-à-
dire plus d'entités extraites.

## Rappel

Mesuré sur 1 287 valeurs distinctes que trois détecteurs indépendants (GLiNER,
Regex, Presidio) ont trouvées et qu'un juge LLM a confirmées comme PII réelles.
Aucune ne vient de Ministral : la référence est neutre vis-à-vis des deux
quantisations.

| Passe | Q4_K_M | Q8_0 |
|---|---|---|
| Configuration de production (2048 tokens de génération) | 5,9 % | 5,7 % |
| Plafond de génération levé (5500 tokens) | 31,1 % | **35,5 %** |

Par type, sans plafond :

| Type de référence | PII connues | Q4_K_M | Q8_0 |
|---|---|---|---|
| Numéro AVS | 1 006 | 27,8 % | 28,0 % |
| Adresse MAC | 196 | 33,7 % | **57,1 %** |
| IBAN | 41 | 70,7 % | **90,2 %** |
| Adresse IP | 20 | 30,0 % | 30,0 % |
| Numéro de téléphone | 9 | 100 % | 100 % |
| Numéro de carte | 8 | 100 % | 100 % |
| Mot de passe | 4 | 75 % | 75 % |
| CVV | 2 | 0 % | 0 % |

L'écart entre les deux quantisations ne se lit donc pas comme une dégradation
uniforme : sur les types denses et répétitifs (numéros AVS en colonnes de
tableau) les deux modèles plafonnent au même niveau, alors que sur les adresses
MAC et les IBAN le Q4 perd nettement.

## Précision et F1

La référence ne couvre que les onze types que les détecteurs amont cherchaient,
alors que Ministral extrait bien plus : 2 805 extractions distinctes pour Q4 et
3 046 pour Q8 sur la passe sans plafond. Les 3 456 extractions hors référence ont
été soumises au juge validé (`openai/gpt-oss-120b`, prompt `v2`, 4 h 45 de
machine) pour que la précision porte sur des étiquettes et non sur un silence.

Deux lectures sont données, parce que le juge sur-rejette : la lecture
**stricte** honore tout verdict `FALSE_POSITIVE`, la lecture **prudente** ne le
retient qu'au-dessus de `confidence >= 0.95`, seuil que la campagne
`llm-judge-eval` avait retenu pour cette raison. La vérité est entre les deux ;
l'écart entre quantisations, lui, est le même dans les deux lectures.

| Passe | Précision stricte | Précision prudente | Rappel | F1 strict | F1 prudent |
|---|---|---|---|---|---|
| Q4_K_M production | 61,8 % | 71,9 % | 5,9 % | 0,108 | 0,109 |
| Q8_0 production | 61,8 % | 72,0 % | 5,7 % | 0,105 | 0,106 |
| Q4_K_M sans plafond | 65,7 % | 74,8 % | 31,1 % | 0,422 | 0,440 |
| **Q8_0 sans plafond** | **66,9 %** | **76,6 %** | **35,5 %** | **0,464** | **0,485** |

Sur la passe sans plafond, Q8 produit plus d'extractions que Q4 (3 046 contre
2 805) **et** une proportion plus élevée de vraies PII parmi elles : le volume
supplémentaire n'est pas du bruit.

## Deux constats indépendants de la quantisation

**Le plafond de génération de production détruit les pages denses.** `MAX_TOKENS`
vaut 2048 dans `ministral_detector.py`. Quand le tableau JSON dépasse ce budget,
il n'est jamais refermé ; `_parse_entity_array` ne trouve pas de `]` équilibré et
rend une liste vide, donc **tout le chunk est perdu**, pas seulement sa fin. Sur
ce corpus, 11 chunks sur 123 sont coupés en Q4 et 10 en Q8, et deux pages
contenant 992 des 1 287 PII de référence sortent zéro entité dans les deux
variantes. Le rappel passe de 5,9 % à 31,1 % (Q4) et de 5,7 % à 35,5 % (Q8) rien
qu'en levant ce plafond. C'est le premier levier à corriger, avant le choix de
quantisation : soit une sortie en flux avec récupération partielle, soit des
chunks plus petits, soit un budget de génération proportionné à la densité.

**Le modèle n'utilise presque pas le vocabulaire configuré.** Il émet 186
étiquettes distinctes en Q4 et 179 en Q8, dont 9 % seulement correspondent à un
`detector_label` du seed `data.sql` : les plus fréquentes sont `NAME`,
`ORGANIZATION`, `DATE`, `IDENTIFIER`, `NSS`, `CONTEXTUAL`, `DEVICE_ID`. Le
passthrough du `_LabelResolver` les remonte en `UPPER_SNAKE`, et le filtre gRPC
conserve les types sans configuration : ces étiquettes traversent donc le
pipeline sans seuil ni sensibilité. L'accord d'étiquette avec la référence n'est
que de 11,7 % (Q4) et 19,9 % (Q8) sur les PII correctement retrouvées — un numéro
AVS ressort en `PHONE`, en `NSS` ou en `IDENTIFIER` selon le contexte. Les deux
quantisations se comportent pareil sur ce point ; il ne discrimine pas, mais il
pèse sur l'exploitation des résultats.

## Limites

- **Le rappel absolu est un plafond, pas une vérité.** La référence n'est pas
  exhaustive : elle ne contient que les PII que GLiNER, Regex et Presidio ont
  trouvées. Ce que les trois ont manqué n'est compté ni comme trouvé ni comme
  manqué. L'écart entre Q4 et Q8 reste valide, puisque les deux affrontent la
  même référence.
- **La référence est déséquilibrée** : 78 % de numéros AVS, concentrés dans deux
  pages de tableaux. La moyenne globale est donc dominée par un seul type ; le
  tableau par type est plus informatif que le chiffre unique.
- **L'appariement se fait par valeur normalisée dans une page**, pas par offset,
  et accepte l'inclusion au-delà de six caractères. Même règle pour les deux
  quantisations.
- **La précision repose sur un juge unique.** `gpt-oss-120b` a arbitré les 3 456
  extractions hors référence ; sa campagne de validation le crédite de 99,3 % de
  rétention des vrais positifs et 66,9 % d'élimination des faux, pas de la
  perfection. D'où les deux lectures. Les deux quantisations passent devant le
  même juge, dans la même passe, donc l'écart entre elles ne dépend pas de lui.
- **Une seule mesure de qualité par variante.** Le débit a été mesuré deux fois
  par variante, la qualité une seule (la température est à 0, mais l'inférence
  GGUF n'est pas bit-à-bit reproductible entre exécutions).

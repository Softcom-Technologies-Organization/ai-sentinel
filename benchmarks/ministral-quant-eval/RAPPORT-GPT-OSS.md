# gpt-oss-120b en détecteur principal — vélocité et stratégies de prompt

Campagne du 26 août 2026, même machine, même corpus et même référence que
[RAPPORT.md](RAPPORT.md) : 37 pages Confluence, 123 chunks, 1 287 PII confirmées.
Seul le modèle et le prompt changent, tout le reste est tenu constant.

> **Campagne arrêtée avant la fin du jugement**, à la demande de l'utilisateur.
> Le rappel, l'accord d'étiquette et les débits sont définitifs. La précision
> retenue est la précision **restreinte** — calculée sur les seules valeurs que
> la référence indépendante avait déjà tranchées, donc complète et non biaisée,
> mais limitée aux onze types de cette référence. La précision complète aurait
> demandé 1 098 jugements de plus (890 des 1 988 ont été faits) ; elle n'est pas
> rapportée, un jugement partiel donnerait un chiffre faux. Voir
> « Circularité du juge ».

## Verdict

**gpt-oss-120b n'est pas un no-go en vélocité, et il écrase Ministral en
qualité dès qu'on lui écrit un prompt adapté.** Avec la stratégie `swiss_vocab`
il retrouve 88,0 % des PII contre 35,5 % pour Ministral Q8_0, étiquette
correctement 99,9 % de ce qu'il trouve contre 19,9 %, et met 22,9 min contre
16,3 min sur le corpus.

Le prix : 63 Go de mémoire résidente contre 2,15 Go, et 40 % de temps de scan en
plus. À mettre en regard de ce que le rappel actuel laisse passer.

## Vélocité

| Modèle et prompt | Décodage | Tokens générés | Temps corpus |
|---|---|---|---|
| Ministral Q4_K_M | 99,4 tok/s | 69 567 | 11,7 min |
| Ministral Q8_0 | 81,4 tok/s | 79 313 | 16,3 min |
| gpt-oss `prod` | 53,5 tok/s | 44 758 | 14,0 min |
| gpt-oss `swiss` | 46,4 tok/s | 44 549 | 16,0 min |
| gpt-oss `vocab` | 51,2 tok/s | 67 164 | 21,9 min |
| gpt-oss `swiss_vocab` | 51,7 tok/s | 70 667 | 22,9 min |

Un modèle de 63 Go tient le rythme d'un 3B parce qu'il génère moins de tokens
pour un résultat meilleur : il est deux fois plus lent par token produit, et
c'est le nombre de tokens produits qui fait le temps de scan, pas la taille du
modèle. Le temps ne dérive qu'avec la longueur du prompt système et le nombre
d'entités trouvées — `swiss_vocab` paie ses 88 % de rappel en tokens de sortie.

## Stratégies de prompt

Les quatre variantes sont dans `prompts.py`. `prod` est le prompt de production
de `ministral_detector.py`, écrit pour un modèle spécialisé.

| Stratégie | Rappel | Précision restreinte | Accord d'étiquette | Extractions |
|---|---|---|---|---|
| `prod` | 31,5 % | 97,4 % | 48,2 % | 1 382 |
| `vocab` | 36,1 % | 95,9 % | **99,1 %** | 2 090 |
| `swiss` | 81,4 % | 98,8 % | 3,2 % | 1 410 |
| **`swiss_vocab`** | **88,0 %** | **97,8 %** | **99,9 %** | 2 099 |
| _Ministral Q8_0_ | 35,5 % | 96,2 % | 19,9 % | 3 046 |

Les deux leviers sont orthogonaux et échouent de façon opposée :

- **`vocab` règle l'étiquetage.** Énumérer les 74 `detector_label` du seed dans
  le prompt fait passer l'accord de 48 % à 99 %. Sans cela, le modèle invente son
  vocabulaire, et une étiquette hors `pii_type_config` traverse le pipeline sans
  seuil ni niveau de sensibilité.
- **`swiss` règle le rappel.** Décrire les formats locaux (NAVS13 `756.`, IBAN
  CH, IDE `CHE-`, plaques cantonales, NPA, carte d'assuré) et exiger
  l'exhaustivité ligne à ligne sur les tableaux fait passer le rappel de 31 % à
  81 %. Mais le modèle reprend alors les noms du prompt comme étiquettes :
  `AVS_AHV_SOCIAL_SECURITY_NUMBER` (872 fois), `AVS`, `SWISS_IBAN`, `NPA`,
  `IDE_UID` — d'où 3,2 % d'accord.
- **Les deux ensemble ne s'annulent pas, ils s'additionnent** : 88,0 % de rappel
  et 99,9 % d'accord. C'est la variante à retenir.

Rappel par type — c'est là que se lit le vrai comportement des prompts :

| Type | PII connues | Ministral Q8_0 | gpt-oss `swiss` | gpt-oss `swiss_vocab` |
|---|---|---|---|---|
| Numéro AVS | 1 006 | 28,0 % | **100 %** | 93,0 % |
| Adresse MAC | 196 | 57,1 % | 0 % | **64,8 %** |
| IBAN | 41 | 90,2 % | **100 %** | 97,6 % |
| Adresse IP | 20 | 30,0 % | 0 % | **100 %** |
| Numéro de téléphone | 9 | **100 %** | 44,4 % | **100 %** |
| Numéro de carte | 6 | **100 %** | 0 % | 0 % |
| Mot de passe | 4 | 75 % | 0 % | 75 % |
| CVV | 2 | 0 % | 0 % | 0 % |

Deux enseignements que la moyenne cachait :

- **`swiss` seul est un détecteur myope** : il fait 100 % sur les deux formats
  que le prompt nomme et 0 % sur les adresses MAC, les IP, les cartes et les mots
  de passe. Énumérer des formats revient à dire au modèle de ne chercher qu'eux.
  Sa moyenne de 81 % ne tient qu'au poids des numéros AVS dans la référence.
- **`swiss_vocab` corrige cette myopie** (le vocabulaire remet les autres types
  dans le champ) mais garde un angle mort : **0 % sur les six numéros de carte
  bancaire**, que Ministral Q8 trouvait tous. Effectif faible, mais c'est une
  régression sur un type sensible, à vérifier avant tout basculement.

## Circularité du juge

gpt-oss-120b est aussi le juge qui arbitre les extractions hors référence. Quand
il devient le détecteur, il se note lui-même. Deux chiffres sont donc donnés :

- **précision restreinte** — calculée sur les seules valeurs que la référence
  indépendante a déjà tranchées (findings de GLiNER, Regex et Presidio arbitrés
  avant que gpt-oss ne soit détecteur). Non biaisée, mais elle ne couvre que les
  onze types de cette référence. C'est le chiffre du tableau ci-dessus ;
- **précision complète** — inclut le jugement des extractions hors référence.
  Optimiste par construction pour gpt-oss, et à lire comme telle. _En cours._

Pour Ministral, les deux chiffres existent déjà et l'écart donne l'ordre de
grandeur du biais : 96,2 % en restreint contre 66,9 % (strict) et 76,6 %
(prudent) en complet. La précision restreinte est nettement plus flatteuse pour
tout le monde : elle ne voit que les valeurs qu'un détecteur déterministe avait
déjà repérées, c'est-à-dire les plus faciles.

## Applicabilité : un seul prompt, pas un par type

`swiss_vocab` est **un unique prompt système statique**, envoyé tel quel pour
tous les chunks, quel que soit leur contenu. Il n'y a ni prompt par type de PII,
ni appel supplémentaire, ni aiguillage : le remplacer en production revient à
changer la constante `_SYSTEM_PROMPT` de `ministral_detector.py`. Le nombre
d'appels au modèle est exactement le même qu'aujourd'hui — un par chunk.

Ce prompt fait 2 400 caractères contre 480 pour l'actuel, ce qui coûte environ
400 tokens de prompt supplémentaires par chunk, soit 3 % du budget d'un chunk de
2 048 tokens. C'est ce qui explique l'essentiel des 6 min d'écart entre les
passes `prod` et `swiss_vocab`.

Le piège à ne pas reproduire est ailleurs : `swiss` **seul** énumère des formats
sans rappeler le reste du vocabulaire, et le modèle en conclut qu'il ne doit
chercher que ceux-là — 0 % sur les adresses MAC et les IP. Ce n'est pas un
argument pour un prompt par type, c'est l'inverse : c'est ce qui impose de garder
**un** prompt qui couvre tout le vocabulaire d'un coup.

## Ce que ça implique pour ai-sentinel

1. **Le prompt pèse plus lourd que le modèle.** Entre `prod` et `swiss_vocab`
   sur le même modèle, le rappel est multiplié par 2,8. Entre Ministral Q4 et Q8,
   il varie de 4 points. Le prompt de production n'a jamais été adapté au corpus
   suisse ni au vocabulaire configuré.
2. **Le même travail de prompt reste à faire sur Ministral.** Les deux leviers
   testés ici n'ont rien de spécifique à gpt-oss ; rien ne dit qu'ils rendront
   autant sur un 3B, mais rien ne dit le contraire non plus. À mesurer avant de
   trancher le choix du modèle.
3. **Le plafond de génération de 2048 tokens reste le premier défaut du
   détecteur** (voir RAPPORT.md) : il fait perdre des chunks entiers, quel que
   soit le modèle et quel que soit le prompt.

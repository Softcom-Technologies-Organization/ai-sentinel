# Benchmark des juges LLM sur corpus PII public

Quel juge LLM décide le mieux si une valeur `XXX` est réellement une instance du
type `YYY`, et faut-il lui parler avec un prompt générique ou un prompt par type
de PII ?

Le benchmark voisin [`llm-judge-eval`](../llm-judge-eval/) répond à la même
question sur un jeu rédigé à la main. Celui-ci la repose sur des documents
publics annotés — Gretel et AI4Privacy — et sur le vocabulaire **réellement
activé** dans `pii-reporting-api/src/main/resources/data.sql`. Il s'appuie sur
[`pii-dataset-eval`](../pii-dataset-eval/) pour la façon de figer, normaliser et
échantillonner ces jeux, mais il mesure autre chose : là où `pii-dataset-eval`
note les *détecteurs* en précision/rappel, celui-ci note les *juges* qui
post-filtrent leurs résultats.

## Ce qui est mesuré

La sémantique de production dicte les métriques. `verdictHonoring(threshold)`
n'honore un rejet qu'au-dessus du seuil de confiance du job, et le finding est
conservé dans tous les autres cas :

| Verdict du juge | Effet |
|---|---|
| `FALSE_POSITIVE` au-dessus du seuil | le finding est **supprimé** |
| `TRUE_POSITIVE`, `UNSURE`, `UNPARSEABLE`, `ERROR`, ou sous le seuil | le finding est **conservé** (fail-open) |

D'où deux nombres asymétriques :

- **Rétention TP** — part des vrais findings encore remontés. Chaque point perdu
  est une PII réelle qui cesse d'être signalée.
- **Élimination FP** — part des findings parasites supprimés. Sans elle, le juge
  est un no-op très coûteux.

`utility@λ=10` chiffre l'asymétrie : `(FP supprimés − 10 × TP détruits) / nb FP`.
Négatif, le juge fait plus de mal que pas de juge du tout.

Le rapport décline ces nombres **par type de PII**, par famille de faux positif,
par difficulté et par langue — c'est ce qui permet de choisir un juge global,
ou un juge par type.

## Le jeu de données

### D'où viennent les vrais positifs

Des spans annotés de deux corpus publics figés à une révision :

| Corpus | Révision | Fichiers retenus | Rôle |
|---|---|---|---|
| `gretelai/synthetic_pii_finance_multilingual` | `7b844d1…` | `English_test`, `France_test`, `English_train`, `France_train` | documents financiers longs et structurés (messages SWIFT, relevés, contrats) |
| `ai4privacy/pii-masking-300k` | `c8c7789…` | `validation/1english_openpii_8k`, `validation/french_openpii_8k` | documents courts, conversationnels et formulaires |

44 031 documents, 71 % anglais et 29 % français. Le corpus de production étant un
Confluence francophone, le français est dans le jeu dès le départ, et le rapport
sépare les deux langues. L'allemand est disponible dans les deux dépôts : l'ajouter
consiste à lister son fichier dans `config/datasets.toml`, rien d'autre.

### D'où viennent les faux positifs

Un corpus annoté ne contient, par construction, que des vrais positifs. Les faux
positifs sont donc **construits** — mais jamais inventés : chaque valeur et ses
300 caractères de contexte sont pris mot pour mot dans un document du corpus.
Quatre familles, calquées sur ce que le juge a réellement rejeté sur le space
Confluence `PCYBER` le 18 septembre 2026 (414 rejets sur 897 findings, par
`deepseek-v4-flash-0731@q2_k_xl` au seuil 0,8) :

| Famille | Part visée | Ce que c'est |
|---|---:|---|
| `type_mention` | 40 % | le span est la **locution qui nomme le type**, pas une valeur : « paiement par >>>carte de crédit<<< », « Payment method: >>>Debit card<<< », « >>>IBAN<<< : GB42IITW… ». C'est le mode de défaillance dominant observé en production. |
| `competing_type` | 30 % | une vraie valeur d'un type voisin annoncée sous celui-ci : un BBAN sous `IBAN`, un numéro de téléphone sous `SOCIAL_SECURITY_NUMBER`. Les paires sont déclarées une par une dans `config/pii_types.toml`, **et le contexte doit nommer le type voisin** dans les 60 caractères autour de la valeur. |
| `format_violation` | 20 % | une suite de caractères du document que la spécification du type ne peut pas accepter. Les formes sont choisies pour qu'une vraie instance ne puisse jamais correspondre — c'est ce qui rend l'étiquette « faux positif » vérifiable. |
| `placeholder` | 10 % | une valeur masquée ou un gabarit présent dans le texte : `[Insert Date]`, `****1234`, `${DB_PASSWORD}`, `XXXXX`. |

Une famille impossible à remplir pour un type cède ses places aux autres, et le
journal de construction le dit. `PASSWORD` et `API_KEY` n'ont pas de
`format_violation` : ces deux types n'ont pas de spécification publiée, donc
aucune suite de caractères n'est structurellement impossible pour eux.

**Pourquoi le contexte doit nommer le type voisin.** Le prompt de production
n'autorise un rejet au titre du motif (b) que si « le contexte désigne
explicitement une AUTRE entité », interdit de rejeter faute d'indice, et interdit
de calculer une clé de contrôle. Un BBAN nu annoncé comme numéro de carte est
donc réellement indécidable sur la valeur seule : un juge qui répond `UNSURE` a
raison, et le compter en échec reviendrait à le noter sur l'inverse de ses propres
consignes. Chaque candidat `competing_type` porte donc l'expression qui a qualifié
le rejet, dans son champ `difficulty` (`rival_named:<expression>`). Les expressions
qui nomment un label qu'aucun type jugé ne revendique — identifiant client,
matricule, téléphone, passeport, code postal, coordonnées — sont déclarées dans la
table `[rival_mentions]` de `config/pii_types.toml`.

### Les trois tailles

`n` compte les vrais positifs **et**, séparément, les faux positifs : une taille
de 20 donne 40 findings par type.

| Taille | n par type | Findings | Appels par juge et par stratégie |
|---|---:|---:|---:|
| `light` | 20 | 720 | 720 |
| `normal` | 50 | 1 800 | 1 800 |
| `large` | 100 | 3 600 | 3 600 |

Les trois sont **emboîtées** : `light ⊂ normal ⊂ large`, avec des `finding_id`
stables. Un juge passé sur `large` contient déjà la réponse pour `light`, et
`self_test.py` vérifie l'emboîtement.

### Les types couverts, et ceux qui ne le sont pas

18 `pii_type` activés sont mesurés, tous avec plus de 600 vrais positifs
disponibles — largement au-delà des 100 de la taille `large` :

| Catégorie | Types |
|---|---|
| Financier | `CREDIT_DEBIT_CARD`, `CREDIT_CARD`, `CVV`, `PIN`, `ACCOUNT_NUMBER`, `BANK_ROUTING_NUMBER`, `IBAN`, `IBAN_CODE`, `SWIFT_BIC` |
| Identité officielle | `SOCIAL_SECURITY_NUMBER`, `SOCIALNUM`, `NATIONAL_ID`, `DRIVER_LICENSE_NUMBER` |
| Numérique | `IP_ADDRESS`, `IPV4`, `IPV6`, `PASSWORD`, `API_KEY` |

`CREDIT_CARD`/`CREDIT_DEBIT_CARD`, `IBAN`/`IBAN_CODE` et
`SOCIALNUM`/`SOCIAL_SECURITY_NUMBER` désignent le même concept sous deux
`pii_type` activés et deux libellés distincts. Production envoie la chaîne au juge
telle quelle : ils sont mesurés séparément, et un écart entre les deux est une
information, pas une redondance. La graine de tirage est dérivée de la signature
`gold`/`competing`/`mentions` et non du nom du type, si bien que les deux bras
jugent **exactement les mêmes spans** : un écart est donc imputable aux seules
chaînes `pii_type` et `type_label`, pas au hasard du tirage. `--type` permet d'en
écarter un quand le budget est serré.

**Types activés que les deux corpus n'annotent pas, et qui ne sont donc pas
mesurés** : `TAX_ID`, `LICENSE_PLATE`, `VEHICLE_IDENTIFIER`,
`CERTIFICATE_LICENSE_NUMBER`, `MAC_ADDRESS`, `HTTP_COOKIE`, `DEVICE_IDENTIFIER`,
`SOFTWARE_LICENSE`, `CLIENT_SECRET`, `GITHUB_TOKEN`, `ACCESS_TOKEN`, `CRYPTO`,
`NRP`, `AVS_NUMBER`. C'est la limite la plus nette du benchmark : `TAX_ID` et
`VEHICLE_IDENTIFIER` sont justement deux des types les plus bruyants sur PCYBER.
L'ablation `per_type_financial_only` existe pour approcher la question par un
autre angle — combien coûte un corps de prompt *manquant*.

## Les juges

| Clé | Modèle | Taille | Moteur |
|---|---|---:|---|
| `ministral` | `ministral-3b-pii-preview@q8_0` | 3,7 Go | LM Studio — c'est la quantification que `data.sql` sème comme `lm_studio_model` |
| `gpt-oss-120b` | `openai/gpt-oss-120b` | 63,4 Go | LM Studio, `reasoning_effort: low` |
| `deepseek-v4-flash` | `deepseek-v4-flash-0731@q2_k_xl` | 96,8 Go | LM Studio — le juge qui a produit les verdicts PCYBER |
| `openjev` | OpenJev/SemIf + `Qwen/Qwen3.5-4B` | 8,7 Go | `transformers`, hors LM Studio |

Charger les 96,8 Go de DeepSeek demande d'abord `sudo sysctl
iogpu.wired_limit_mb=118784` : le plafond GPU par défaut de macOS est proche de
96 Go et le chargement échoue sans cela.

Dans `run_judges.py`, OpenJev fait noter directement les trois options de verdict
et tourne sous `--strategy none`. SemIf utilise son propre prompt système et un
critère de décision qui peut recevoir des consignes supplémentaires. La campagne
de calibrage ci-dessous exploite ce critère pour comparer les formulations.

### Calibrage dédié à OpenJev

`calibrate_openjev.py` réutilise Qwen3.5-4B et le scoring direct existants. Il
compare quatre variantes : entrée actuelle, consignes générales adaptées au
scoring, ajout du corps par type, puis ajout des six exemples existants.

Les documents sont séparés avant les essais : 35 % des groupes pour le calibrage,
le reste pour le test. Les alias et contextes identiques restent dans le même
lot. Deux réglages sont figés sur le calibrage : meilleur F1, et meilleure
utilité à rappel empirique d'au moins 99 %. Le seuil n'est jamais choisi sur le
test. La calibration ajuste une règle de rejet, pas des probabilités fiables.

Depuis la racine du dépôt, avec le cache de la campagne OpenJev précédente :

```bash
pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/test_calibrate_openjev.py
```

```bash
HF_HOME=/private/tmp/openjev-hf-cache HF_HUB_OFFLINE=1 pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_openjev.py --device mps
```

Le script reprend les prédictions déjà écrites et vérifie les empreintes de la
campagne. `--output` crée une campagne séparée ; `--report-only` régénère le
rapport sans charger le modèle. Le cache temporaire doit encore exister ; sinon
configurer `HF_HOME` vers un cache contenant la révision du modèle.

Résultats : [synthèse OpenJev](RAPPORT-OPENJEV.md),
[rapport détaillé](results/openjev-calibration/RAPPORT.md),
`results/openjev-calibration/metrics.json`, prédictions brutes, critères exacts,
répartition des lots et seuils sélectionnés. Les scores restent relatifs aux
annotations fournies : les corpus sont synthétiques et les FP construits, sans
correction des labels après observation des réponses.

### Calibrage comparable de GPT-OSS-120B

`calibrate_gpt_oss.py` applique à GPT-OSS-120B les mêmes partitions de documents,
la même grille de seuils, les mêmes métriques et les deux mêmes objectifs de
sélection qu'à OpenJev. Il calibre séparément les six stratégies déclarées dans
`config/prompts.toml`, car la confiance générée par GPT-OSS n'est pas comparable
au score conditionnel entre options d'OpenJev.

Le modèle est chargé une seule fois par LM Studio. Chaque réponse est écrite
immédiatement et une relance reprend la campagne. Une fois la sélection figée sur
les 1 240 candidats de calibration, seuls le prompt générique et les stratégies
retenues passent sur les 2 360 candidats de test. Si les métriques OpenJev sont
présentes, le rapport final compare automatiquement les deux juges.

Depuis la racine du dépôt :

```bash
pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/test_calibrate_gpt_oss.py
```

```bash
caffeinate -i pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py
```

Le script décharge les modèles LM Studio déjà présents puis charge
`openai/gpt-oss-120b`. Utiliser `--keep-loaded` uniquement si cette révision est
déjà servie. `--report-only` régénère le rapport sans charger le modèle.

Résultats : `results/gpt-oss-calibration/RAPPORT.md`, `metrics.json`,
`comparison.json`, prédictions brutes, protocole et sélection figée.

## Les prompts

Tous les prompts sont des fichiers Markdown sous `prompts/`. Modifier un prompt
ne demande jamais de toucher au Python.

```
prompts/
  general/production-v2.md     le prompt de production, à l'octet près
  per-type/<PII_TYPE>.md       un corps par type
  per-type/_default.md         le corps neutre de repli
  strategies/*.md              troncs alternatifs complets
```

`prompts/general/production-v2.md` est **identique caractère pour caractère** à la
constante `SYSTEM_PROMPT` de
`pii-detector-service/pii_detector/infrastructure/judge/llm_finding_judge.py`, et
`self_test.py` échoue si les deux divergent. Le bras générique est donc
démontrablement la ligne de base de production, pas une paraphrase.

### Générique, par type, ou un mélange

`config/prompts.toml` déclare les stratégies. Une stratégie répond à une seule
question : pour un finding de type `T`, quel prompt système le juge reçoit-il ?

| Stratégie | Ce qu'elle fait |
|---|---|
| `general` | le prompt de production pour tous les types |
| `per_type` | le même tronc, plus le corps écrit pour le type jugé |
| `per_type_financial_only` | corps spécifique pour les neuf types financiers, corps neutre pour tous les autres — l'ablation qui mesure ce que coûte un corps *absent* |

Le corps est inséré dans le tronc juste avant le paragraphe nommé par
`insert_before` ; garder ce point d'ancrage dans la configuration plutôt que comme
un marqueur dans le fichier est ce qui permet au tronc de rester identique à la
production.

Pour qu'un type reçoive un prompt propre : déposer
`prompts/per-type/<PII_TYPE>.md`. Pour qu'il retombe sur le prompt général :
supprimer le fichier, ou retirer le type de la liste `types` d'une stratégie
restreinte. Pour une nouvelle stratégie : ajouter une section à
`config/prompts.toml`.

```toml
[strategies.concise]
label  = "Tronc court"
system = "strategies/concise.md"

[strategies.concise_per_type]
label         = "Tronc court + corps par type"
system        = "strategies/concise.md"
bodies        = "per-type"
insert_before = "SORTIE :"
```

Les troncs alternatifs contiennent tous un paragraphe commençant par `SORTIE :`,
qui leur sert de point d'ancrage : chacun peut donc être combiné avec les corps
par type en quatre lignes de configuration.

## Exécution

Tout tourne dans le venv du service, qui a déjà `torch`, `transformers` et
`semif_phase1` pour OpenJev :

```bash
PY=../../pii-detector-service/.venv/bin/python
```

```bash
$PY self_test.py                                        # hors ligne, avant toute campagne
$PY build_dataset.py                                    # les trois tailles
$PY run_judges.py --judge ministral --size light
$PY run_judges.py --judge ministral --size light --strategy per_type
$PY run_judges.py --all --size light
$PY score.py --threshold 0.8                            # results/report.md
```

Options utiles :

| Option | Effet |
|---|---|
| `--type IBAN` (répétable) | restreint à un `pii_type` |
| `--limit 40` | échantillonne à pas régulier, pour un essai à blanc |
| `--tag rerun` | deuxième exécution du même bras : donne le plancher de bruit |
| `--keep-loaded` | juge avec ce que LM Studio sert déjà, sans recharger |
| `--no-resume` | repart de zéro au lieu de reprendre le fichier existant |

Chaque jugement est écrit au fil de l'eau : un arrêt conserve tout ce qui a été
mesuré, et la reprise repart exactement où elle s'était arrêtée. `run_judges.py`
décharge explicitement tous les modèles avant chaque chargement — un MoE de 96 Go
ne cohabite avec rien sur une machine de 128 Go, et laisser LM Studio arbitrer en
JIT rend les temps de chargement irreproductibles.

`build_dataset.py --inventory` imprime l'histogramme des labels des deux corpus
sans rien écrire : c'est par là qu'on commence pour ajouter un type.

## Fichiers

| Fichier | Rôle |
|---|---|
| `config/datasets.toml` | coordonnées HF figées, tailles, fenêtre de contexte, familles de FP |
| `config/pii_types.toml` | **source de vérité** : vocabulaire jugé, libellés, sources de vrais positifs, paires de confusion, formes de surface, formes impossibles |
| `config/prompts.toml` | déclaration des stratégies |
| `config.py` | lecteurs stdlib des trois tables |
| `build_dataset.py` | téléchargement → vrais positifs et quatre familles de faux positifs → `datasets/{light,normal,large}.jsonl` + `manifest.json` |
| `prompts.py` | assemblage du prompt système, marquage du span |
| `run_judges.py` | registre des juges, pilote LM Studio et OpenJev |
| `score.py` | métriques de production, classement, meilleur juge par type, comparaison appariée des stratégies → `results/report.md` |
| `self_test.py` | contrôles hors ligne : synchronisation avec le prompt de production, cohérence des tables, invariants du jeu, absence de fuite du jeu de test dans les prompts |

## Ce que le benchmark ne dit pas

- **Les vrais positifs héritent du bruit des corpus.** Gretel compose le document
  puis y insère les valeurs, si bien qu'un IBAN atterrit parfois sur une ligne
  « Transaction ID ». Ces cas sont marqués `misleading_clue` : un juge qui les
  rejette a correctement lu le contexte, et le rapport isole la colonne pour
  qu'on puisse les mettre de côté.
- **Les faux positifs sont construits, pas observés.** Ils reproduisent les
  familles d'erreur du scan PCYBER ; ils ne les échantillonnent pas. Le lien reste
  une hypothèse de conception, pas une mesure.
- **Les corps par type connaissent une partie de la clé de correction.** Un corps
  qui écrit « une suite de chiffres seule n'est jamais un IBAN » énonce la
  spécification dont la famille `format_violation` est tirée, et un corps qui dit
  « le mot IBAN nomme le type » vise la famille `type_mention`. C'est en partie
  légitime — c'est exactement ce qu'un prompt par type doit contenir — mais le
  gain de la stratégie `per_type` sur ces deux familles est donc à lire avec
  réserve. Le tableau par famille du rapport est là pour ça : regarder d'abord le
  gain sur `competing_type` et sur la rétention des vrais positifs, qu'aucun corps
  ne peut anticiper.
- **Quatorze types activés ne sont pas mesurés** faute d'annotation publique, dont
  deux des plus bruyants en production.
- **La base de production ne stocke ni la valeur ni le motif du juge** : la
  campagne PCYBER a servi à choisir les familles de faux positifs et leurs parts,
  elle ne pouvait pas fournir les textes eux-mêmes.

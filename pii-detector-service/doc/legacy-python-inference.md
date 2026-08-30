# L'inférence PII en Python « à la main » — le guide de survie pour un entretien

> Diagramme associé : [`legacy-python-inference.mmd`](./legacy-python-inference.mmd)

## 0. Le contexte en trois phrases

`pii-detector-service` doit répondre à une question simple : *dans ce texte Confluence, où sont les
données personnelles et les secrets ?* Aujourd'hui, on envoie le texte à **LM Studio** en HTTP et LM
Studio se débrouille avec le modèle. Avant, on **chargeait le modèle nous-mêmes dans le process
Python** et on pilotait chaque étape à la main : téléchargement des poids, tokenisation, découpage,
forward pass, décodage, filtrage.

C'est cette version « à la main » que ce document explique. Elle est plus verbeuse, mais elle a un
énorme avantage en entretien : **on voit tous les rouages**.

Où retrouver le code :

| Concept | Fichier (supprimé au commit `0bc85df6`) |
|---|---|
| Chargement tokenizer + modèle | `pii_detector/infrastructure/model_management/model_manager.py` |
| Pipeline complet « transformers » | `pii_detector/infrastructure/detector/pii_detector.py` |
| Détecteur zero-shot | `pii_detector/infrastructure/detector/gliner_detector.py` |
| Découpage token-aware | `pii_detector/infrastructure/text_processing/semantic_chunker.py` |
| Décodage BIOES + fusion de fragments | `pii_detector/infrastructure/detector/openmed_detector.py` |
| Parallélisme mémoire-safe | `pii_detector/infrastructure/model_management/detector_worker_pool.py` |
| Juge LLM local (llama.cpp) | `infrastructure/validation/llm_validator.py` **au commit `010dadc7`** |

---

## 1. La chaîne complète, une ligne par étape

1. **Au boot** : on télécharge les poids depuis Hugging Face, on instancie un *tokenizer* et un
   *modèle*, on les met en mode inférence, on précharge le tout une fois pour toutes.
2. **À la requête** : on normalise le texte en Unicode NFC.
3. **On découpe** le texte en fenêtres de tokens qui rentrent dans la fenêtre de contexte du modèle,
   avec du chevauchement.
4. **On tokenise** chaque fenêtre : le texte devient une suite d'entiers, plus une table qui dit à
   quel caractère du texte d'origine correspond chaque entier.
5. **Forward pass** : le modèle transforme ces entiers en un score par token et par étiquette.
6. **On décode** : les scores par token redeviennent des *spans* de texte (« du caractère 412 au 431,
   c'est un IBAN, confiance 0.87 »).
7. **On recolle** : offsets remis à l'échelle du texte entier, fragments de sous-mots refusionnés,
   doublons du chevauchement éliminés.
8. **On filtre** : mapping du label du modèle vers notre type métier, puis seuils de confiance.
9. **On fusionne** avec les autres détecteurs (regex, Presidio) et on arbitre les conflits.
10. **Un LLM génératif** relit chaque candidat et dit *vrai positif* ou *faux positif*.

---

## 2. Les concepts, un par un

Format : ce que c'est → pourquoi ça existe → ce qu'on en faisait → la phrase à sortir en entretien.

### 2.1 Hugging Face Hub, `safetensors`

**Ce que c'est.** Un registre public de modèles. Un « modèle », concrètement, c'est un dossier :
`config.json` (l'architecture), `model.safetensors` (les poids, des centaines de millions de nombres),
`tokenizer.json` (le vocabulaire et les règles de découpe).

**Pourquoi.** Sans ça, chacun réimplémente son format. `safetensors` a remplacé les *pickles* PyTorch
parce qu'un pickle exécute du code arbitraire au chargement — c'est un vecteur d'attaque.

**Chez nous.** `hf_hub_download` fichier par fichier, ou `snapshot_download` pour tout le dépôt, avec
un cache local (`HF_HOME`). En Docker, ce cache est un volume : sinon on retélécharge 2,7 Go à chaque
redémarrage de conteneur.

> « Un repo HF, c'est une architecture + des poids + un tokenizer. Le tokenizer fait partie du modèle :
> on ne peut pas mélanger le tokenizer de l'un avec les poids de l'autre. »

### 2.2 `transformers` et les classes `Auto*`

**Ce que c'est.** La bibliothèque de Hugging Face. Son idée centrale : les classes `Auto`.
`AutoTokenizer.from_pretrained("iiiorg/piiranha-v1")` lit `config.json`, y voit
`"model_type": "deberta-v2"`, et instancie la bonne classe pour toi.

**Pourquoi.** Il existe des dizaines d'architectures (BERT, RoBERTa, DeBERTa, Mistral…). Sans les
`Auto*`, changer de modèle voudrait dire changer de code. Avec, ça veut dire changer une chaîne de
caractères dans un fichier TOML — c'est exactement ce que faisait notre `config/models/*.toml`.

> « `transformers` est une couche d'abstraction au-dessus de PyTorch. Les classes `Auto*` sont un
> factory pattern piloté par le `config.json` du modèle. »

### 2.3 Le tokenizer — le concept le plus mal compris

**Ce que c'est.** Un traducteur bidirectionnel entre du texte et des entiers. Un réseau de neurones ne
sait pas manipuler des caractères : il manipule des vecteurs. Le tokenizer est le pont.

**Pourquoi des « sous-mots » et pas des mots.** Un vocabulaire de mots entiers serait infini (noms
propres, IBAN, mots composés, fautes de frappe). Un vocabulaire de caractères serait trop long à
traiter. Le compromis, c'est le **sous-mot** : les algorithmes BPE / WordPiece / SentencePiece
apprennent sur un gros corpus les fragments les plus fréquents. Résultat :

```
"jean.dupont@vd.ch"  →  ["jean", ".", "du", "pont", "@", "vd", ".", "ch"]
"Tr0ub4dor!2024"     →  ["Tr", "0", "ub", "4", "dor", "!", "202", "4"]
```

**Les conséquences directes, qui sont le vrai sujet.**

1. **Un mot ≠ un token.** Un texte français accentué peut faire 600 tokens là où il fait 380 mots.
   C'est ce qui nous a valu une régression : on dimensionnait les chunks en mots, le modèle tronquait
   silencieusement, et il retournait zéro entité sur les textes longs.
2. **Les entités sont fragmentées.** Le modèle prédit sur des tokens, donc il peut nous rendre
   `"Tr0ub4dor!202"` puis `"4"` comme **deux** entités distinctes. D'où notre étape de fusion des
   fragments adjacents (`_merge_adjacent_fragments`).
3. **Les accents.** `é` peut s'écrire en un caractère (NFC) ou en deux (`e` + accent combinant, NFD).
   Les deux se ressemblent à l'écran mais se tokenisent différemment. D'où l'appel
   `unicodedata.normalize('NFC', text)` **avant** tokenisation.

**L'`offset_mapping`, l'arme secrète.** Appelé avec `return_offsets_mapping=True`, le tokenizer rend
en plus, pour chaque token, son intervalle de caractères dans le texte source :

```python
enc = tokenizer(text, return_offsets_mapping=True, add_special_tokens=False)
enc["offset_mapping"]  # [(0, 4), (4, 5), (5, 7), ...]
```

Sans ça, on saurait « il y a un email quelque part » mais on ne saurait pas **où** le surligner dans
la page Confluence. Tout le contrat métier d'ai-sentinel (spans, surlignage, rapport Excel) repose sur
ces offsets.

**Les tokens spéciaux.** `[CLS]`, `[SEP]`, `<pad>` : des marqueurs de début, de séparation et de
remplissage que le modèle attend. Ils ont un offset `(0, 0)` — il faut les filtrer, sinon on croit
qu'une entité commence au caractère 0.

> « Le tokenizer découpe en sous-mots, donc un mot n'est pas un token et une entité peut arriver en
> morceaux. Deux choses le rendent gérable : l'`offset_mapping`, qui garde le lien vers les
> caractères d'origine, et une étape de refusion des fragments en aval. »

### 2.4 La fenêtre de contexte et le chunking

**Ce que c'est.** Tout modèle a un `max_len` : le nombre maximum de tokens qu'il peut traiter d'un
coup. 256 pour Piiranha, 384 à 768 pour GLiNER. Au-delà, il **tronque** — et souvent en silence.

**Pourquoi cette limite.** Le mécanisme d'attention compare chaque token à tous les autres : le coût
est quadratique en nombre de tokens. Doubler la longueur quadruple le calcul et la mémoire.

**Comment on découpe.**

```python
encoding = tokenizer(
    text,
    return_offsets_mapping=True,
    truncation=True,
    max_length=256,
    return_overflowing_tokens=True,  # ne jette pas le surplus : rend N fenêtres
    stride=64,                       # chevauchement entre fenêtres consécutives
)
```

**Pourquoi le chevauchement (`stride` / `overlap`).** Sans lui, une entité posée pile sur une frontière
de fenêtre est coupée en deux et perdue dans les deux fenêtres. Avec un chevauchement supérieur à la
longueur maximale d'une entité, **toute entité est entière dans au moins une fenêtre**. Le prix à payer :
on détecte deux fois les entités de la zone commune, il faut dédupliquer après.

**Le rebase des offsets.** Le modèle voit une sous-chaîne, donc il rend des offsets **locaux** au chunk.
On fait `span.start += chunk.start` pour repasser en offsets **globaux**. C'est mécanique, et c'est la
source de bug numéro un de ce genre de pipeline.

> « La fenêtre de contexte est finie et la troncature est silencieuse. On découpe donc en fenêtres de
> tokens — pas de mots — avec un chevauchement supérieur à la taille d'une entité, puis on rebase les
> offsets et on déduplique la zone commune. »

### 2.5 Le forward pass : embeddings, attention, logits, softmax

**Ce qui se passe.**

1. **Embedding** : chaque token id devient un vecteur dense (par exemple 768 nombres). Deux tokens de
   sens proche ont des vecteurs proches.
2. **Couches d'attention** (l'« encodeur Transformer ») : à chaque couche, chaque token regarde tous
   les autres et met à jour son vecteur. C'est ça, la « compréhension du contexte » — après quelques
   couches, le vecteur de `12` dans « CH93 0076 2011 6238 5295 7 » n'a plus rien à voir avec le vecteur
   de `12` dans « 12 personnes ».
3. **Tête de classification** : une simple couche linéaire projette chaque vecteur de token sur
   `nombre_d_étiquettes` valeurs. Ces valeurs brutes sont les **logits**.
4. **Softmax** : transforme les logits en probabilités qui somment à 1. C'est ce nombre entre 0 et 1
   qu'on appelle le `score` et qu'on compare au seuil.

**Le mode inférence.** Trois réglages, trois raisons :

```python
model.eval()                       # désactive dropout & co : comportement déterministe
for p in model.parameters():
    p.requires_grad = False        # on n'entraîne pas : pas de graphe de gradients
with torch.no_grad():              # idem, au niveau de l'appel
    logits = model(input_ids)
```

Sans `no_grad`, PyTorch conserve tout l'historique de calcul pour pouvoir rétropropager : la mémoire
explose pour rien, puisqu'on n'entraîne pas.

> « L'inférence, c'est un forward pass : embeddings, couches d'attention, tête linéaire, softmax. En
> mode `eval` et sous `no_grad`, parce qu'on ne calcule aucun gradient. »

### 2.6 L'étiquetage BIO / BIOES et l'agrégation

**Le problème.** Le modèle classe des **tokens**, pas des entités. Comment savoir que trois tokens
consécutifs forment *une* entité et pas trois ?

**La solution.** On préfixe les étiquettes :

| Texte | `Jean` | `Dupont` | `habite` | `à` | `Lausanne` |
|---|---|---|---|---|---|
| Étiquette | `B-NAME` | `I-NAME` | `O` | `O` | `B-CITY` |

`B` = *Begin*, `I` = *Inside*, `O` = *Outside*. La variante **BIOES** ajoute `E` (*End*) et `S`
(*Single*) : plus verbeuse, mais elle contraint mieux les frontières.

**L'agrégation.** C'est l'étape qui relit cette séquence d'étiquettes et produit des spans. Dans
`transformers`, c'est le paramètre `aggregation_strategy` du pipeline :

- `"none"` : un résultat par token, à toi de te débrouiller.
- `"simple"` : regroupe `B` + les `I` qui suivent, moyenne les scores. C'est ce qu'on utilisait.
- `"first"` / `"max"` / `"average"` : variantes sur le choix de l'étiquette quand un mot est coupé en
  plusieurs sous-mots et que ceux-ci ne sont pas d'accord.

Certains modèles font mieux : un **décodeur Viterbi** intégré, qui choisit la séquence d'étiquettes
globalement la plus probable **en respectant la grammaire BIOES** (un `I-EMAIL` ne peut pas suivre un
`B-IBAN`). C'était le cas d'`OpenMed/privacy-filter-multilingual` — le décodage était fait dans le
modèle, on récupérait des spans propres.

> « Le modèle fait de la classification de tokens avec un schéma BIO. L'agrégation transforme cette
> séquence d'étiquettes en spans. Une agrégation gloutonne suffit souvent ; un décodage Viterbi
> contraint est plus propre parce qu'il interdit les séquences d'étiquettes illégales. »

### 2.7 NER classique vs NER zero-shot (GLiNER)

C'est notre différenciateur, et c'est une très bonne histoire d'entretien.

| | NER classique (Piiranha) | NER zero-shot (GLiNER) |
|---|---|---|
| Les types détectés | **figés à l'entraînement** | **passés en entrée à chaque appel** |
| Ajouter « numéro AVS » | il faut annoter et ré-entraîner | il faut ajouter une ligne en base |
| Appel | `pipeline(text)` | `model.predict_entities(text, labels, threshold)` |
| Où sont les étiquettes | dans `config.json` (`id2label`) | dans un paramètre : `["iban", "passport number", …]` |

**Comment ça marche.** GLiNER encode le texte **et** les étiquettes en langage naturel dans le même
espace vectoriel, puis calcule une similarité entre chaque span candidat et chaque étiquette. C'est
pour ça que le **libellé** de l'étiquette compte énormément : « password » et « secret credential »
n'ont pas les mêmes performances. On a un agent et une skill dédiés à cette optimisation de libellés
dans ce dépôt — parce que c'est un vrai levier de précision, à coût de développement nul.

**Chez nous.** Les libellés vivaient dans la colonne `detector_label` de `pii_type_config`, avec un
`threshold` par type. Changer une détection = changer une ligne SQL, pas un modèle.

> « GLiNER est du NER zero-shot : les types sont un paramètre d'entrée, pas une propriété du modèle.
> Concrètement, ajouter un type de PII devient une ligne en base au lieu d'une campagne
> d'annotation. En échange, la qualité dépend du libellé qu'on donne à l'étiquette. »

### 2.8 Seuils de confiance : le curseur précision / rappel

Chaque span sort avec un score. On applique deux étages :

1. un seuil **global** volontairement permissif (0.30) au niveau du détecteur ;
2. un seuil **par type**, lu en base, qui fait la coupe finale.

Pourquoi deux étages : un IBAN est vérifiable par checksum, on peut être laxiste. Un « mot de passe »
est une notion floue, on doit être sévère sinon le rapport se noie dans le bruit. Un seul seuil global
forcerait un compromis unique sur des situations qui n'ont rien à voir.

Le vocabulaire à maîtriser : monter le seuil augmente la **précision** (moins de faux positifs) et
baisse le **rappel** (on rate des vrais). Sur un outil de conformité, un faux négatif est une fuite de
données ; un faux positif est du temps perdu pour un humain. On préfère donc être permissif et filtrer
ensuite — c'est exactement la raison d'être du juge LLM.

### 2.9 Device, dtype, quantification, runtimes

**Device.** `cpu`, `cuda` (GPU NVIDIA), `mps` (GPU Apple). `model.to(device)` déplace les poids.
Notre contrainte de déploiement était le CPU : pas de GPU garanti chez le client.

**Dtype.** La précision des nombres. `float32` = 4 octets par poids, `float16` = 2. Sur GPU, `float16`
divise la mémoire par deux et va plus vite. Sur CPU, `float16` est souvent **plus lent** (pas
d'instructions natives) — d'où notre `float16 if device == 'cuda' else float32`.

**Quantification.** Aller plus loin : stocker les poids sur 8, 5 ou 4 bits. `Q4_K_M` veut dire « 4 bits,
variante K, taille moyenne ». Un modèle de 8 Go tient dans 2,5 Go. On perd un peu de qualité, on gagne
la possibilité de le faire tourner. C'est ce qui rend un LLM local viable sur une machine de dev.

**GGUF et `llama.cpp`.** GGUF est le format de fichier des modèles quantifiés ; `llama.cpp` est un
moteur d'inférence en C++ qui les exécute, sans PyTorch. `llama-cpp-python` en est le binding Python :

```python
from llama_cpp import Llama
llm = Llama(model_path="gemma-4-E4B-it-Q4_K_M.gguf", n_ctx=4096, n_gpu_layers=-1)
```

`n_ctx` = fenêtre de contexte allouée. `n_gpu_layers` = combien de couches déporter sur le GPU
(`-1` = toutes, `0` = tout en CPU) : ça permet d'utiliser un GPU trop petit pour le modèle entier.

**ONNX Runtime.** Une autre voie : on **exporte** le modèle PyTorch en un graphe de calcul figé
(`.onnx`), puis un runtime optimisé l'exécute. Plus rapide, pas de PyTorch en production. Le piège
qu'on a rencontré : une session ONNX Runtime **ne survit pas à un `fork()`** — le premier appel dans
le process enfant part en deadlock. Ça a directement dicté notre stratégie de parallélisme.

> « Trois niveaux : le dtype (32 vs 16 bits), la quantification (4-5 bits, format GGUF, moteur
> llama.cpp), et le runtime (PyTorch pour l'expérimentation, ONNX Runtime pour la production). On
> échange un peu de qualité contre la faisabilité sur le matériel disponible. »

### 2.10 Modèle discriminatif vs modèle génératif

C'est **le** point de bascule de notre architecture.

| | Encodeur / discriminatif | Décodeur / génératif |
|---|---|---|
| Exemples | BERT, DeBERTa, GLiNER | Mistral, Gemma, Qwen |
| Question posée | « quelle étiquette pour ce token ? » | « quel est le prochain token ? » |
| Sortie | une distribution de probabilités **par token** | du **texte**, généré un token à la fois |
| Coût | un seul forward pass | un forward pass **par token généré** |
| Offsets de caractères | exacts, par construction | **aucun** — il faut retrouver le span dans le texte |
| Nouveau type de PII | ré-entraînement (ou libellé, si zero-shot) | une phrase dans le prompt |

**Pourquoi la génération coûte cher.** « Autoregressif » veut dire que pour écrire 200 tokens, le
modèle fait 200 passes, chacune conditionnée par les précédentes. D'où les optimisations qu'on ne code
plus soi-même : *KV-cache*, *batching* continu, *paged attention*.

**Ce qu'on en faisait.** Un **juge**. Le modèle discriminatif ratisse large et rapporte des candidats ;
le LLM génératif relit chaque candidat **avec son contexte** et tranche. Un « 12345678 » dans un tableau
de références produit n'est pas un numéro de sécurité sociale — un modèle de tokens n'a pas ce
raisonnement, un LLM oui.

**Les réglages de génération.**

- `temperature=0.0` : déterministe, on prend toujours le token le plus probable. Pour un juge, on veut
  la reproductibilité, pas la créativité.
- `max_tokens=200` : borne dure sur la sortie. Sans elle, un modèle « thinking » peut partir en
  monologue et faire exploser la latence.
- `n_ctx` : le prompt (entité + contexte + consignes) doit y tenir. On envoyait par lots d'environ
  20 entités et on **coupait le lot en deux, récursivement**, si l'estimation dépassait la fenêtre.

**Le point faible qu'on a corrigé plus tard.** On demandait au modèle d'écrire
`0: TRUE_POSITIVE` / `1: FALSE_POSITIVE`, et on parsait avec une regex. Ça marche… jusqu'à ce que le
modèle enrobe sa réponse d'une phrase de politesse. La bonne réponse est la **sortie structurée
contrainte** : le moteur d'inférence n'autorise, à chaque token, que ceux qui respectent une grammaire
ou un JSON Schema. C'est ce que fait aujourd'hui le `response_format: json_schema` avec
`strict: true`. **Le parsing n'est plus une supposition, c'est une garantie.**

> « On a combiné les deux familles : un encodeur pour le rappel et les offsets exacts, un génératif
> pour la précision et le raisonnement contextuel. Et on a remplacé le parsing regex de la réponse du
> LLM par une génération contrainte par JSON Schema — ça supprime toute une classe de bugs. »

### 2.11 Concurrence et mémoire : là où ça se joue vraiment

En production, le problème n'est presque jamais le modèle. C'est la RAM et le débit.

**Chargement unique (singleton).** Un `AutoModel.from_pretrained` coûte 5 à 10 secondes et 2,7 Go.
Notre premier détecteur OpenMed le rechargeait à **chaque appel** parce que le helper de la
bibliothèque ne cachait rien : on a mis un benchmark de 480 appels de **1 heure à quelques minutes**
juste en gardant le pipeline en attribut d'instance. C'est l'exemple parfait d'« optimisation qui n'est
pas du micro-tuning ».

**Threads ou processus ?** Le forward pass CPU représentait ~99,7 % du temps. On a mesuré :

- Les **threads** (`ThreadPoolExecutor`) sont adaptés au traitement des chunks d'**une** requête :
  l'attente est dans du code natif PyTorch qui relâche le GIL.
- Pour paralléliser **plusieurs requêtes**, il faut des **processus**. L'intra-op de PyTorch scale mal
  (le calcul est *memory-bound*) alors que « N inférences indépendantes sur N cœurs » scale presque
  linéairement.

**Le `fork` + copy-on-write.** N processus × 1,8 Go de poids = OOM. La solution : le parent charge le
modèle **une fois**, puis `fork()`. Sous Linux, l'enfant partage les pages mémoire du parent en
copy-on-write : comme les poids sont en lecture seule, ils ne sont jamais recopiés. On a N workers pour
le prix d'une copie de modèle.

Deux règles impératives autour de ça, apprises à la dure :

1. **Le parent ne doit jamais exécuter de forward avant le fork.** Un `fork()` ne duplique que le thread
   appelant : les threads BLAS/OpenMP créés par le premier calcul n'existent pas chez l'enfant, et
   l'enfant deadlock. Chaque worker fait donc son propre *warmup* **après** le fork.
2. **Pas de `fork` avec ONNX Runtime.** Sa session ne survit pas au fork, même en la rechargeant. On
   bascule en `spawn` dans ce cas — chaque worker paie sa propre copie, tant pis.

**Les variables d'environnement qui comptent.** `TOKENIZERS_PARALLELISM=false` (les tokenizers Rust
forkent leurs propres threads, ce qui entre en collision avec le multiprocessing et affiche un
warning), `OMP_NUM_THREADS` / `torch.set_num_threads` (1 à 2 threads par worker quand on a N workers
≈ N cœurs, sinon les workers se battent pour les mêmes cœurs).

> « Le vrai travail d'ingénierie n'était pas le modèle mais son cycle de vie : chargement unique,
> processus plutôt que threads pour le débit, fork après préchargement pour garder une seule copie des
> poids en RAM, et un warmup post-fork parce que fork ne duplique pas les threads BLAS. »

### 2.12 Dégradation gracieuse (`fail-open`)

Si le juge LLM tombe en timeout, plante, ou si le modèle n'est pas là : on **garde** les entités
candidates. Sur un outil de conformité, la règle est que l'échec d'un filtre de précision ne doit jamais
provoquer une perte de détection. On accepte du bruit, jamais un silence.

> « Le juge est un filtre de précision optionnel. Sa politique d'erreur est *fail-open* : en cas de
> panne, on conserve les candidats. Un faux positif se traite, un faux négatif est une fuite. »

---

## 3. Pourquoi on a arrêté de faire l'inférence nous-mêmes

Ce n'est **pas** un aveu d'échec, et c'est important de le formuler comme ça en entretien.

**Ce que ça nous coûtait.**

- `torch` + `transformers` + `llama-cpp-python` dans l'image Docker : plusieurs Go, une compilation
  native, des conflits de versions (`transformers >= 5.0` exigé par un modèle, incompatible avec un
  autre).
- Tous les problèmes ci-dessus à maintenir nous-mêmes : fork-safety, threads BLAS, quantification,
  cache mémoire.
- Une roadmap d'optimisations d'inférence (KV-cache, batching continu) qui n'est pas notre métier.

**Ce que LM Studio apporte.**

- Le chargement, la quantification, le déchargement, le batching, le KV-cache, le choix du backend
  GPU (Metal, CUDA) : géré.
- Une **API HTTP compatible OpenAI**. Le service Python n'a plus qu'un client `httpx` : plus de poids,
  plus de PyTorch, plus de pool de processus à orchestrer pour le modèle.
- Un **contrat** : `response_format: json_schema` avec `strict: true`. La sortie est valide par
  construction.
- Changer de modèle devient une opération d'exploitation, plus un déploiement applicatif.

**Ce qui reste chez nous, et qu'aucun serveur d'inférence ne fera.** Le chunking token-aware, la gestion
des offsets, le mapping label → type métier, les seuils par type, la fusion multi-détecteurs, les
post-filtres de format (checksum IBAN, AVS, Luhn), la politique de fail-open. **Autrement dit : toute
la logique métier.** Le serveur d'inférence est un détail d'infrastructure — c'est exactement ce que
l'architecture hexagonale du service exprime : le détecteur est un *adapter out* derrière un port.

**Le prix payé.** Une dépendance à un service externe (donc de la latence réseau, une surface de panne,
un `fail-open` à câbler), et moins de contrôle fin sur l'inférence.

---

## 4. Cheat sheet — les questions probables

**« Explique-moi ce que fait un tokenizer. »**
Il traduit du texte en entiers que le modèle peut manipuler, par sous-mots. Trois conséquences
pratiques : un mot n'est pas un token, donc on dimensionne les chunks en tokens ; une entité peut
revenir fragmentée, donc on refusionne ; et on demande l'`offset_mapping` pour garder le lien vers les
caractères d'origine — sans ça, pas de surlignage.

**« Pourquoi découper le texte ? »**
La fenêtre de contexte est finie et la troncature est silencieuse. On découpe en fenêtres de tokens
avec un chevauchement plus grand que la taille d'une entité, pour qu'aucune entité ne soit coupée dans
toutes les fenêtres. Ensuite on rebase les offsets et on déduplique la zone commune.

**« C'est quoi un logit ? »**
La sortie brute de la dernière couche linéaire, avant normalisation. Le softmax le transforme en
probabilité entre 0 et 1 — c'est ce score qu'on compare au seuil.

**« Différence entre BERT et un LLM pour cette tâche ? »**
BERT est un encodeur discriminatif : il classe chaque token, un seul forward pass, offsets exacts, mais
des types figés. Un LLM est un décodeur génératif : il écrit du texte, un forward pass par token
généré, aucun offset, mais il raisonne sur le contexte. On a utilisé le premier pour le rappel et le
second comme juge de précision.

**« Comment tu ajoutes un nouveau type de PII ? »**
Avec un modèle classique : annoter un corpus et ré-entraîner. Avec GLiNER : ajouter une ligne en base
avec le libellé de l'étiquette et un seuil. C'est la raison principale de notre choix d'un modèle
zero-shot.

**« Ton plus gros problème de perf ? »**
Un modèle de 2,7 Go rechargé à chaque appel parce que le helper de la bibliothèque ne cachait rien :
5 à 10 s de démarrage à froid par détection. On a mis le pipeline en cache dans l'instance, et un
benchmark de 480 appels est passé d'une heure à quelques minutes. Ensuite, le passage de threads à
processus avec fork après préchargement, pour scaler le débit sans multiplier la RAM.

**« Pourquoi avoir abandonné l'inférence maison ? »**
Parce que ce n'était pas de la valeur métier. Notre valeur, c'est le chunking, les offsets, le mapping
vers les types métier, les seuils, la fusion multi-détecteurs et les post-filtres. Le chargement de
poids, la quantification et le batching sont un problème résolu par un serveur d'inférence. On a gagné
une image Docker allégée, la sortie structurée garantie par JSON Schema, et le changement de modèle
sans redéploiement.

---

## 5. Glossaire express

| Terme | En une phrase |
|---|---|
| **Token** | Un fragment de texte (souvent un sous-mot) du vocabulaire du modèle. |
| **Tokenizer** | Le traducteur texte ⇄ entiers. Fait partie du modèle. |
| **`offset_mapping`** | Pour chaque token, sa position en caractères dans le texte source. |
| **Embedding** | Le vecteur dense associé à un token. |
| **Attention** | Le mécanisme par lequel chaque token intègre le contexte des autres. |
| **Fenêtre de contexte / `max_len`** | Le nombre max de tokens traitables d'un coup. |
| **Chunking** | Découper un texte trop long en fenêtres qui rentrent, avec chevauchement. |
| **`stride` / `overlap`** | Le chevauchement entre deux fenêtres consécutives. |
| **Forward pass** | Une passe d'inférence, entrée → sortie, sans apprentissage. |
| **Logit** | Score brut avant softmax. |
| **Softmax** | Transforme des logits en probabilités sommant à 1. |
| **`no_grad` / `eval()`** | Mode inférence : pas de gradients, comportement déterministe. |
| **BIO / BIOES** | Le schéma d'étiquettes qui encode les frontières d'entités sur les tokens. |
| **Agrégation** | Le passage de la séquence d'étiquettes aux spans. |
| **Viterbi** | Décodage qui choisit la séquence d'étiquettes globalement optimale et légale. |
| **NER** | *Named Entity Recognition* : repérer et typer des entités dans un texte. |
| **Zero-shot** | Le modèle traite des étiquettes qu'il n'a pas vues à l'entraînement. |
| **Seuil / `threshold`** | Le curseur précision ↔ rappel appliqué au score. |
| **dtype** | Précision numérique des poids (float32, float16). |
| **Quantification** | Compresser les poids sur 4-8 bits pour tenir en mémoire. |
| **GGUF** | Format de fichier des modèles quantifiés pour `llama.cpp`. |
| **`llama.cpp`** | Moteur d'inférence C++ pour modèles GGUF, sans PyTorch. |
| **ONNX** | Format de graphe de calcul figé, exécuté par un runtime optimisé. |
| **`n_ctx`** | Taille de fenêtre de contexte allouée à un LLM génératif. |
| **`n_gpu_layers`** | Nombre de couches déportées sur le GPU. |
| **Autoregressif** | Génération token par token, chacun conditionné par les précédents. |
| **KV-cache** | Réutilisation des états d'attention passés pour accélérer la génération. |
| **`temperature`** | Aléa de la génération. 0 = déterministe. |
| **Sortie contrainte** | Générer sous contrainte d'une grammaire ou d'un JSON Schema. |
| **Copy-on-write** | Les pages mémoire partagées après `fork` ne sont copiées qu'à l'écriture. |
| **`fail-open`** | En cas de panne d'un filtre, laisser passer plutôt que bloquer. |

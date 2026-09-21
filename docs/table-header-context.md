# Contexte des tableaux pour la détection de PII

L’extraction transmet les intitulés de colonnes avec leurs valeurs. Par exemple :

```text
Service : SMTP | Port : 587 | Code PIN : 1234
```

Les détecteurs reçoivent ce texte enrichi lors du scan initial et de la reprise d’un scan. Aucun
réglage ni changement du contrat gRPC n’est nécessaire. Le modèle reste responsable de la
classification ; un en-tête n’est pas une règle qui exclut automatiquement une détection.

## Formats

| Source | Association des en-têtes |
| --- | --- |
| Tableaux HTML Confluence | En-têtes explicites `th`, `thead`, `scope` et `headers`/`id` ; cellules fusionnées et niveaux d’en-tête pris en compte quand la structure est exploitable. |
| Pièces jointes XLS, XLSX | Grille lue avec Apache POI ; les en-têtes déclarés des tableaux XLSX sont associés uniquement aux cellules de leur plage. |
| Pièces jointes ODS | Structure extraite par Tika ; associations explicites conservées lorsqu’elles sont présentes. |
| CSV | Virgule, point-virgule ou tabulation ; champs entre guillemets et multilignes lus avec Commons CSV. Le paramètre MIME `header=present` ou `header=absent` est respecté. |
| Documents Word et ODT joints | Association lorsque les en-têtes explicites subsistent dans le HTML produit par Tika. |
| HTML joint | Lecture directe avec Jsoup, en respectant les indications d’encodage du document. |
| Autres pièces jointes | Extraction textuelle existante. |

Sans déclaration explicite, la première ligne est comparée aux vingt lignes suivantes. Ses
cellules non vides doivent être textuelles et au moins une colonne correspondante doit contenir
des valeurs non vides d’une forme constante : nombres, dates numériques ou adresses électroniques.
La largeur doit être compatible et les fusions ne doivent pas rendre la structure ambiguë.
Il n’existe aucune liste de titres autorisés : langue, ponctuation, doublons et intitulés
métier sont conservés. Un titre vide n’est pas répété. La première ligne reste toujours analysée.
Les tableaux entièrement textuels sans déclaration explicite restent sans association supposée.

Les structures ambiguës, les tableaux imbriqués et les limites de contextualisation déclenchent
un traitement sans ajout de titres, qui conserve les limites des cellules. Le regroupement des
titres de feuilles sur plusieurs lignes n’est pas inféré. Les contrôles de qualité des pièces
jointes et les limites de taille restent applicables. La reconnaissance automatique est une
heuristique : elle peut manquer un en-tête, mais ne supprime jamais sa ligne.

## Positions et découpage

`ExtractedContent` contient le texte d’analyse, le texte utilisé pour le rapport et leurs
segments correspondants. Les libellés répétés sont ajoutés uniquement au texte d’analyse.
`DetectionOffsetRemapper` reconvertit les positions des détections et des rejets du post-filtre.
Une détection située sur un libellé synthétique ou traversant deux cellules est exclue ; les
en-têtes originaux restent analysés. Les limites sont contrôlées aussi lorsque les textes
d’analyse et du rapport sont identiques. Un séparateur ` | ` facilite la lecture et le découpage,
mais la protection repose sur les segments, pas sur l’interprétation du séparateur par le modèle.
Les statistiques finales sont recalculées, et les durées et erreurs des détecteurs sont conservées.

Le rapport conserve les valeurs issues du document, avec une présentation textuelle normalisée
des tableaux. Le masquage utilise les positions dans ce texte, sans les préfixes synthétiques.
Le contexte conservé pour le juge ultérieur reste celui du rapport : cette évolution enrichit
l’entrée des détecteurs, pas le prompt du juge.

Les deux chunkers privilégient les lignes complètes, puis les cellules séparées par ` | `.
Le chunker Ministral vérifie le budget avec le tokenizer ; le fallback utilise son estimation
en caractères. Le recouvrement s’adapte aux limites de lignes. Chaque fragment reste une
sous-chaîne exacte du texte d’analyse.

Une cellule dépassant à elle seule le budget est découpée : son en-tête ne peut pas être garanti
dans chacun de ses fragments. Le texte est conservé. Un budget incapable de contenir un seul
caractère avec le tokenizer provoque une erreur explicite.

## Limites de la mesure

Les tests vérifient les formats, les associations, les replis, le découpage et les positions.
Le test gRPC utilise un serveur déterministe qui fournit des positions en points de code Python,
puis vérifie leur conversion en positions Java et dans le texte du rapport. Il ne mesure pas
la précision du LLM.

L’extracteur Ministral actuel localise toujours une valeur retournée par le modèle avec
`chunk_text.find(value)`. Si une même valeur apparaît dans plusieurs cellules du fragment,
son occurrence peut rester ambiguë, par exemple `Port : 1234 | Code PIN : 1234`. La conversion
des positions ne corrige pas cette limite préexistante du contrat de sortie du modèle.

La diminution effective des faux positifs doit être mesurée sur le même corpus avant et après
enrichissement, en contrôlant aussi les vrais positifs perdus. Aucun taux de réduction n’est
déduit des seuls tests de fonctionnement.

## Vérification du 6 septembre 2026

- Suite Java complète : 1 723 tests réussis.
- Vérification finale après relecture : 150 tests réussis, dont le transport gRPC local,
  les cas limites HTML, les formats de pièces jointes, le scan et sa reprise.
- Python : 135 tests ciblés réussis, puis 14 tests d’intégration avec le tokenizer Ministral
  réel chargé depuis le cache local. Les appels au LLM sont simulés.
- Aucune dépendance ajoutée et aucun benchmark de précision du modèle exécuté.

Depuis `pii-reporting-api/` :

```bash
mvn -o test
mvn -o test -Plocal-build,unit-and-integration-tests -Dtest=TableContextDetectionIntegrationTest,ExtractedContentTest,HtmlContentParserTest,HtmlTableContextualizerTest,DetectionOffsetRemapperTest,StreamConfluenceScanUseCaseTest,StreamConfluenceResumeScanUseCaseTest,ScanEventFactoryTest,HexagonalArchitectureTest,TikaAttachmentTextExtractorAdapterTest,CompositeAttachmentTextExtractorAdapterTest,TabularAttachmentHeadersTest,CsvAttachmentTableParserTest
```

Depuis `pii-detector-service/` :

```bash
HF_HUB_OFFLINE=1 .venv/bin/pytest tests/unit/test_semantic_chunker.py tests/unit/test_ministral_detector.py tests/unit/test_ministral_concurrency.py tests/unit/test_concurrency_autotuner.py tests/integration/test_ministral_token_chunker_real.py -q --no-cov
```

## Comparaison avec le modèle réel — 14 septembre 2026

`TableHeaderPrecisionBenchmarkIT` reprend le test conteneurisé Java : PostgreSQL et le service
Python tournent dans Testcontainers, l’API Java dans le processus de test. LM Studio tourne sur
l’hôte et le conteneur Python le joint par `host.docker.internal:1234`. Le test exige que le modèle
`ministral-3b-pii-preview@q8_0` soit déjà chargé ; il échoue si le modèle ou le détecteur ne répond
pas. Il vérifie que la connexion JDBC pointe bien vers PostgreSQL et n’active pas le profil H2.
Lors de cette première mesure, seul Ministral était activé, avec les types `PIN` et `PASSWORD`.

Deux tableaux synthétiques annotés sont analysés trois fois chacun : le texte du rapport sert de
référence sans répétition des en-têtes et le texte d’analyse contient les en-têtes ajoutés aux
cellules. Les deux variantes gardent donc la même mise en forme. Le test compare les vrais
positifs, les faux positifs, les omissions et le score F1 ; il exige un gain de F1 sans perte de
vrais positifs. Premier passage : 0 vrai positif, 3 faux positifs, 15 omissions sans enrichissement ;
6 vrais positifs, 0 faux positif, 9 omissions avec enrichissement. Ce résultat montre un gain sur
ces exemples, pas un taux de précision général sur les documents Confluence. Le détail révèle que
le gain vient du tableau `PASSWORD` (0/6 vrais positifs sans contexte, 6/6 avec contexte) ; le
tableau `PIN` reste à 0/9 dans les deux variantes. La répétition du benchmark a donné les mêmes
résultats.

Depuis `pii-reporting-api/`, avec Docker et LM Studio démarrés :

```bash
mvn -o test -Plocal-build,unit-and-integration-tests -Dtest=TableHeaderPrecisionBenchmarkIT -Dtable.header.benchmark=true
```

## Correction des coordonnées CSV — 15 septembre 2026

Le benchmark active maintenant Presidio, Regex, Ministral et le post-filtre dans sa base
PostgreSQL isolée. Il conserve les types et seuils de `data.sql`, sauf `PIN`, explicitement
désactivé et vérifié par une assertion. Cela reproduit ce réglage local, sans prétendre copier
tous les réglages de production.

Le cache local `~/.cache/huggingface/hub/models--OpenMed--Ministral-3B-PII-Preview` est monté
en lecture seule dans le conteneur. Il doit contenir le tokenizer téléchargé. Le test échoue
si le détecteur utilise le découpage de secours en caractères à la place du tokenizer réel.

La comparaison des en-têtes garde les mêmes cellules, séparateurs et contrôles de positions
dans les deux variantes. Seule la répétition des titres change. Le tableau contenant des PIN
sert désormais de contrôle négatif. Les faux positifs, les omissions et le score F1 sont mesurés
sur les trois détecteurs.

Un second test charge le CSV signalé, conservé dans `src/test/resources/tables/`. Il compare
l’ancien texte aplati au texte corrigé, puis retire entièrement les en-têtes pour vérifier que
la protection fonctionne aussi sans eux. Il exige la reproduction des 26 fausses cartes
Presidio avant correction, leur absence après correction et la détection d’une carte de test
avec espaces contenue dans une seule cellule. La stack et le modèle sont réels ; aucune base H2
ni réponse simulée de détecteur n’intervient dans ce benchmark.

Résultats du 15 septembre 2026, avec le modèle `ministral-3b-pii-preview@q8_0` et son tokenizer réel :

| Contrôle | Avant | Après |
| --- | ---: | ---: |
| Fausses cartes sur les 302 lignes du CSV | 26 | 0 |
| Fausses cartes sur le CSV corrigé privé de ses en-têtes | — | 0 |
| Carte de test avec espaces dans une cellule | — | 1 détectée |
| Mots de passe trouvés, deux valeurs sur trois répétitions | 0 / 6 | 6 / 6 |
| Faux positifs du tableau de mots de passe | 0 | 0 |
| PIN retenus avec le type désactivé | 0 | 0 |

La comparaison des titres passe de F1 = 0 à F1 = 1 sur ce petit corpus synthétique ; elle ne
mesure pas la précision générale en production. Sur le CSV, le texte passe de 14 962 à 41 548
caractères et l’appel complet de 206 à 281 secondes. Le contrôle sans en-têtes prend 103 secondes.
Ces durées correspondent à cette exécution locale, avec une concurrence fixée par le seed à 1.

Vérification finale : **158 tests réussis, aucun échec, aucune erreur, aucun test ignoré**.
La sélection couvre l’extraction, les limites de cellules, les formats, le transport gRPC,
le scan et sa reprise, l’architecture et les deux tests utilisant la stack réelle.

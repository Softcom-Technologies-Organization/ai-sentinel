# Détecteur Trio PII

Trio PII regroupe Bards, CBX et Ettin dans les paramètres de détection. L'interrupteur général
commande l'ensemble ; chaque modèle conserve son propre interrupteur, ses types PII et leurs
seuils. Désactiver le groupe ne modifie pas les choix enregistrés pour les modèles ou les types.
Un Trio activé doit contenir au moins un modèle activé. Le Trio peut être le seul détecteur actif.

Les 104 types natifs sont proposés : 37 pour Bards, 12 pour CBX et 55 pour Ettin. Les catégories
larges de Bards restent distinctes des catégories précises des autres modèles. Par exemple,
`AUTH_SECRET` ne devient pas artificiellement `PASSWORD`. Les scores sont des estimations
du modèle, sans échelle commune entre les trois modèles : chaque type a donc son propre seuil
initial, mesuré dans `benchmarks/trio-calibration`. Cinq types de Bards qui ne donnent aucune
détection correcte dans cette mesure sont désactivés au départ : `CRIMINAL_OFFENCE_DATA`,
`FINANCIAL_AMOUNT`, `PERSON_ALIAS`, `DOCUMENT_REFERENCE` et `TRADE_UNION_MEMBERSHIP`. Pour les
deux derniers, la mesure ne repose que sur trois ou quatre détections.

## Décodage d'Ettin

Ettin étiquette des morceaux de mots. Regroupés tels quels, ces morceaux découpaient une même
valeur en fragments d'un à trois caractères portant des types différents. Le décodeur attribue
donc un seul type par mot entier : celui dont la probabilité moyenne sur tous les caractères du
mot est la plus élevée, un morceau sans type comptant pour zéro. Les mots collés par de la
ponctuation (adresse IP, URL, e-mail, clé) prennent un seul type lorsqu'il domine le bloc.
Un mot dont un seul morceau est étiqueté obtient ainsi un score faible et passe sous le seuil.

## Seuils initiaux

Les seuils sont choisis sur une moitié de trois sources, puis vérifiés sur l'autre moitié :
3 600 documents annotés (AI4Privacy et Gretel), 2 000 documents du jeu Nemotron, et les détections
obtenues sur 1,8 Mo de documentation et de rapports du dépôt, jugées une à une. Une détection
sur ce texte technique, juste ou fausse, pèse huit fois plus qu'une détection sur les corpus
annotés, parce qu'un espace Confluence contient surtout du texte sans donnée personnelle. Pour les types de gravité élevée, une valeur
manquée coûte deux fois plus qu'une fausse alerte. La méthode, les commandes et les résultats
sont dans `benchmarks/trio-calibration/README.md`.

## Exécution

Le service Python lance un processus persistant par modèle activé, avec `spawn`. Les trois modèles
reçoivent le même texte original en parallèle. Chaque modèle découpe les textes longs selon ses
propres fenêtres ; aucun texte n'est tronqué à la taille d'une seule fenêtre. L'envoi des entrées
est lui aussi soumis à un délai maximal. Les threads du parent servent uniquement aux échanges
entre processus : ils n'exécutent aucune inférence. En cas d'échec, le processus et ses échanges
sont fermés avant la requête suivante.

Les échanges sont protégés entre requêtes gRPC. Une erreur, un crash ou un dépassement du délai
de réponse est indiqué dans les statistiques du modèle ; ce cas n'est pas présenté comme une
absence de données personnelles. Un processus défaillant est arrêté, puis recréé à la requête
suivante. Les autres modèles peuvent fournir leurs résultats.

Les types et seuils sont appliqués avant la fusion. Les portions complémentaires qui se
chevauchent sont conservées et le masquage couvre leur union. Les résultats et statistiques
conservent les sources `BARDS`, `CBX` et `ETTIN`. Leurs durées peuvent se chevaucher : leur somme
n'est pas la durée de la requête.

Le pool historique de requêtes est contourné pour les appels Trio, afin de ne pas dupliquer les
trois modèles dans chaque worker. L'inférence utilise CUDA si disponible, sinon MPS sur Mac,
sinon le CPU. Les modèles restent locaux ; aucun texte n'est envoyé à Hugging Face.

## Configuration et déploiement

Les endpoints existants sont conservés. La configuration JSON ajoute `trioEnabled`,
`bardsEnabled`, `cbxEnabled` et `ettinEnabled`. Les quatre colonnes correspondantes sont
`trio_enabled`, `bards_enabled`, `cbx_enabled` et `ettin_enabled`.

Trio est désactivé par défaut ; ses trois enfants sont présélectionnés. Les lignes de types
utilisent les détecteurs natifs. Les insertions de démarrage ne réinitialisent pas les choix
déjà enregistrés. La migration [021-trio-detector.sql](../pii-reporting-api/init-scripts/021-trio-detector.sql)
est rejouable ; le démarrage habituel Hibernate et les données initiales couvrent également les
bases existantes. Il faut déployer l'API et le service Python avec le même contrat gRPC.

Au premier chargement, les snapshots Hugging Face figés sont téléchargés dans le cache standard.
Pour un déploiement sans réseau, préparer les trois snapshots complets dans `HF_HUB_CACHE`,
puis définir `HF_HUB_OFFLINE=1`. Conserver ce cache dans un volume persistant. Les références,
licences, paramètres de fenêtrage et contrôles d'intégrité des décodeurs sont décrits dans
[NOTICE.md](../pii-detector-service/pii_detector/infrastructure/detector/trio_models/NOTICE.md).

## Vérifications

Les tests couvrent notamment l'exécution de trois processus en concurrence, un worker qui ne
lit pas un document de 1 Mo, les délais de réponse, les offsets Unicode, les types désactivés,
les seuils, la configuration indisponible et le masquage des chevauchements.

La comparaison réelle avec le benchmark de référence a confirmé des spans identiques sur
104 documents, dont les 30 plus longs : 734 spans Bards, 27 CBX et 875 Ettin. Un texte synthétique
de 70 073 caractères vérifie aussi que chaque modèle détecte au-delà du début du document.
Cette comparaison vérifie l'intégration des modèles ; elle ne constitue pas une nouvelle mesure
du rappel ni une garantie de détection de toutes les données personnelles. Elle date d'avant le
nouveau décodage d'Ettin et les seuils par type : pour Ettin, les spans ne sont plus ceux du
benchmark d'origine, et les deux contrôles d'identité de cette section ne valent plus que pour Bards
et CBX à seuil zéro.

Une vérification supplémentaire de la chaîne gRPC, avec la configuration lue en PostgreSQL,
compare l'union des caractères détectés sur 120 documents du même corpus, dont les 30 plus longs.
Elle ne montre aucun caractère manquant ou supplémentaire par rapport au benchmark Trio figé.
Les résultats de la validation de l'application sont consignés dans
[trio-pii-validation.md](trio-pii-validation.md).

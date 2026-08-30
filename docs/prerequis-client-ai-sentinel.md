# AI Sentinel — Configuration matérielle et prérequis techniques client

Document destiné à accompagner une offre commerciale. Il décrit ce que le client doit
mettre à disposition pour installer et exploiter AI Sentinel, et ce que nous devons
savoir de son environnement avant de chiffrer la prestation.

Version du produit de référence : **v1.2.0-rc.1**
Date : 24 août 2026

---

## 1. Ce qui est déployé

AI Sentinel s'installe sur **une seule machine** (VM ou serveur physique) via Docker
Compose. Aucun composant n'est installé sur l'instance Confluence du client : le produit
consomme uniquement l'API REST de Confluence.

| Conteneur | Rôle | Ports (par défaut) |
|---|---|---|
| `pii-reporting-ui` | Interface web (Angular + Nginx) | 4200 |
| `pii-reporting-api` | API, exploration Confluence, persistance, rapports Excel, obfuscation | 8080, 8090 |
| `pii-detector` | Service de détection (Python, gRPC) : Presidio, expressions régulières, Ministral | 50051 |
| `postgres` | Base de données applicative | interne |
| `infisical` + `infisical-db` + `infisical-redis` | Gestionnaire de secrets embarqué | 8082 |
| `pgadmin` | Administration de la base (optionnel, à désactiver en production) | 5050 |

**Composant hors Docker Compose** : le modèle de langage `Ministral-3B-PII` est servi par
un moteur d'inférence externe exposant une API compatible OpenAI (LM Studio par défaut,
port 1234). Ce moteur n'est pas fourni dans la pile Docker et doit être installé
séparément, sur la même machine ou sur une machine dédiée (l'adresse et le port sont
configurables depuis l'interface).

---

## 2. Configuration matérielle

Le dimensionnement dépend d'un seul choix : active-t-on le détecteur Ministral ?

- **Sans Ministral** : détection par Presidio (modèles spaCy) et expressions régulières.
  Tout tourne sur CPU. C'est la configuration la moins coûteuse, avec un taux de détection
  plus faible sur les données non structurées.
- **Avec Ministral** : un modèle de langage de 3 milliards de paramètres analyse chaque
  page. La qualité de détection est nettement supérieure, mais il faut un GPU (ou un Mac
  Apple Silicon) et le débit de scan devient le facteur limitant.

### 2.1 Configuration minimale (sans Ministral)

| Ressource | Valeur |
|---|---|
| Processeur | 4 cœurs x86-64 |
| Mémoire vive | 16 Go |
| Disque | 100 Go SSD |
| GPU | aucun |
| Système | Linux (Ubuntu 22.04+ / RHEL 9+), Windows Server avec Docker Desktop, ou macOS |

Les limites inscrites dans `docker-compose.yml` réservent déjà 2 cœurs et 4 Go au seul
service de détection, avec un plafond à 4 cœurs et 8 Go. En dessous de 16 Go au total, la
pile démarre mais le service de détection est exposé à un arrêt pour dépassement mémoire.

### 2.2 Configuration recommandée (avec Ministral, un seul serveur)

| Ressource | Valeur |
|---|---|
| Processeur | 8 à 12 cœurs x86-64 |
| Mémoire vive | 32 Go |
| GPU | NVIDIA 16 Go de mémoire vidéo (L4, A10, RTX 4000 Ada ou supérieur), pilotes récents |
| Disque | 250 Go SSD NVMe |
| Système | Linux (Ubuntu 22.04+ / RHEL 9+) |

Alternative validée : un **Mac Apple Silicon avec 32 Go de mémoire unifiée au minimum**
(les mesures de référence du produit ont été faites sur un MacBook Pro M5 Max 128 Go).

### 2.3 Configuration pour gros volumes (> 20 000 pages)

| Ressource | Valeur |
|---|---|
| Processeur | 16 cœurs |
| Mémoire vive | 64 Go |
| GPU | 24 Go de mémoire vidéo ou plus |
| Disque | 500 Go SSD NVMe |

### 2.4 Répartition sur deux machines

Le moteur d'inférence peut être séparé du reste. C'est la solution à retenir quand le
client dispose déjà d'une machine GPU mutualisée :

- **Machine A** (applicative, sans GPU) : 8 cœurs, 16 Go, 150 Go de disque.
- **Machine B** (inférence, avec GPU) : 16 Go de mémoire vidéo, 16 Go de mémoire vive,
  50 Go de disque. Un flux HTTP interne de A vers B sur le port du moteur d'inférence
  (1234 par défaut) doit être ouvert.

### 2.5 Détail de l'occupation disque

| Poste | Taille |
|---|---|
| Images Docker (9 conteneurs, dont le détecteur Python avec PyTorch) | ~10 à 12 Go |
| Cache des modèles Hugging Face (spaCy fr/en/de, segmenteur Ministral) | ~2 Go |
| Poids du modèle Ministral-3B quantifié en q8_0 | ~3,6 Go |
| Base PostgreSQL | croît avec le nombre de résultats ; compter quelques centaines de Mo pour 100 000 résultats |
| Rapports Excel | ~50 Ko à 900 Ko par espace scanné |

### 2.6 Débit à attendre

Mesure de référence sur MacBook Pro M5 Max, modèle Ministral-3B quantifié, concurrence 4 :
environ **1 400 caractères analysés par seconde**, soit de l'ordre de **1 000 pages de
5 000 caractères par heure**.

Trois points à retenir pour le cadrage :

- Le débit plafonne à partir d'une concurrence de 4 requêtes simultanées sur cette
  machine. Augmenter la concurrence n'apporte rien ; il faut un GPU plus puissant.
- Sans Ministral, le scan est bien plus rapide et se retrouve limité par l'API Confluence
  et par l'extraction de texte des pièces jointes.
- Cette valeur doit être **re-mesurée sur la machine cible** avant tout engagement de
  durée de scan. Le produit embarque un test de charge automatique qui calibre la
  concurrence au démarrage.

---

## 3. Prérequis logiciels

| Élément | Version | Remarque |
|---|---|---|
| Docker Engine | 20.10 ou supérieur | Docker Desktop sous Windows et macOS |
| Docker Compose | 2.0 ou supérieur | inclus dans Docker Desktop |
| Moteur d'inférence | LM Studio (référence), ou tout serveur exposant `/v1/chat/completions` compatible OpenAI | uniquement si Ministral est activé |

Deux vérifications à faire avec le client :

- **Conditions de licence du moteur d'inférence** en usage professionnel. À valider avec
  l'éditeur retenu, ou à contourner en utilisant un serveur d'inférence open source
  (llama.cpp, vLLM).
- **Droits d'installation** sur la machine : Docker doit pouvoir tourner, et sous Linux
  l'utilisateur d'exploitation doit appartenir au groupe `docker`.

---

## 4. Prérequis Confluence

### 4.1 Informations à obtenir sur l'instance

| Information | Pourquoi elle est nécessaire |
|---|---|
| Type de déploiement : **Cloud** ou **Data Center** | Le mécanisme d'authentification diffère : Cloud = identifiant + jeton d'API en Basic, Data Center = jeton d'accès personnel en Bearer. Le produit ne connaît que ces deux modes. |
| Version exacte de Confluence (Data Center) | Le produit exige les **jetons d'accès personnels**. Une instance trop ancienne pour cette fonction est bloquante. |
| URL de base complète, chemin de contexte inclus | Exemple : `https://wiki.client.ch/confluence`. Toutes les URL d'API en découlent. |
| Nombre d'espaces à scanner, nombre de pages, nombre et poids des pièces jointes | Dimensionnement de la machine et estimation de la durée du premier scan. |
| Langues des contenus | Le produit charge des modèles fr, en, de, et couvre es et it par expressions régulières. |
| Applications Marketplace installées qui produisent des macros propriétaires | Une macro non standard peut contenir du texte non extrait, et surtout compliquer la réécriture de page lors de l'obfuscation. |
| Existence d'une instance de test ou d'un espace bac à sable | Indispensable pour valider l'obfuscation avant de toucher la production. |

### 4.2 Points techniques à confirmer avec l'équipe Confluence

- **L'API REST v1 (`/rest/api/...`) doit être joignable et non filtrée.** C'est celle
  qu'utilise le produit, pour Cloud comme pour Data Center. Aucun pare-feu applicatif ni
  aucune application de sécurité ne doit bloquer ces routes.
- **Point de vigilance sur Confluence Cloud** : Atlassian a engagé la dépréciation de
  l'API v1 au profit de la v2. À date, le produit fonctionne, mais la trajectoire doit
  être discutée si l'engagement porte sur plusieurs années.
- **Le langage de requête CQL et l'index de recherche doivent fonctionner.** Le scan
  incrémental repose sur une recherche `lastModified >= ...` ; un index de recherche
  dégradé fausse la détection des pages modifiées.
- **Limites de débit de l'API.** Sur Cloud, Atlassian applique des quotas par compte. Sur
  Data Center, il faut vérifier l'absence de limitation par un répartiteur de charge ou un
  pare-feu applicatif. Un scan génère un appel par page, plus un appel par liste de pièces
  jointes, plus un téléchargement par pièce jointe extractible.
- **Fenêtre de scan.** Un scan complet fait monter la charge sur Confluence. Prévoir un
  créneau hors heures de bureau pour le premier passage.
- **Notifications.** L'obfuscation modifie la page, ce qui crée une nouvelle version et
  peut notifier les personnes qui suivent la page. À arbitrer avec le client.

### 4.3 Compte technique

Un compte de service dédié, non nominatif, est nécessaire.

**Caractéristiques du compte**

- Non soumis à une authentification multifacteur interactive ni à une expiration de mot de
  passe non maîtrisée.
- Sur une instance fédérée (SAML / SSO), le compte doit pouvoir s'authentifier **par jeton
  sur l'API** sans passer par le fournisseur d'identité. C'est le cas normal des jetons
  d'accès personnels, à confirmer sur l'instance du client.
- Exclu des politiques de verrouillage automatique pour inactivité.

**Secret d'authentification**

| Déploiement | Secret à fournir |
|---|---|
| Cloud | adresse e-mail du compte + jeton d'API Atlassian |
| Data Center | jeton d'accès personnel (Personal Access Token) |

Le jeton d'accès personnel de Data Center porte une **date d'expiration** et hérite des
droits de son porteur : prévoir une procédure de renouvellement, et convenir de qui la
déclenche. Le secret est saisi dans l'interface d'AI Sentinel et stocké chiffré.

**Droits en lecture (obligatoires)**

- Droit « Voir » sur **tous** les espaces à scanner.
- Accès aux pièces jointes de ces espaces.

**Attention aux restrictions de page.** Une page peut être restreinte à un groupe
restreint et rester invisible pour un compte qui a pourtant le droit sur l'espace. Deux
options à trancher avec le client :

1. Donner au compte technique des droits d'administration Confluence, pour garantir une
   couverture complète du scan.
2. Accepter que les pages restreintes ne soient pas analysées, et le documenter comme
   angle mort du rapport de conformité.

**Droits en écriture (uniquement si l'obfuscation automatique est retenue)**

- Droit « Ajouter / modifier des pages » sur les espaces concernés.

Sans ces droits, la fonction d'obfuscation reste disponible en simulation mais chaque
tentative d'écriture échoue.

### 4.4 Limites connues à annoncer au client

Ces points ne sont pas des défauts à corriger mais des propriétés du produit à faire
valider avant signature.

- **Les pièces jointes ne sont pas obfuscables.** Elles sont analysées (PDF, Word, Excel,
  PowerPoint, OpenDocument, texte, CSV, HTML) et les résultats sont rapportés, mais la
  correction reste manuelle. Le produit le signale explicitement dans le plan
  d'obfuscation.
- **L'historique des versions de Confluence conserve la donnée d'origine.** L'obfuscation
  écrit une nouvelle version de la page ; la version précédente, qui contient encore la
  donnée personnelle, reste consultable dans Confluence. Une purge de l'historique des
  versions par un administrateur Confluence est nécessaire pour que la suppression soit
  effective. Même remarque pour la corbeille et pour l'index de recherche.
- **L'obfuscation est irréversible** côté AI Sentinel : la valeur d'origine est remplacée
  par un jeton et le résultat passe dans un état terminal.
- **Les propriétaires d'espace ne sont récupérables que sur Confluence Cloud.** Sur Data
  Center, l'API ne permet pas de lister les administrateurs d'espace par le même appel
  (limitation Atlassian, ticket CONFSERVER-78176) : la colonne des contacts du rapport
  reste vide et les responsables doivent être renseignés autrement.
- **Les types de données personnalisés ne sont pas garantis.** Le modèle de langage peut
  remonter des catégories qu'un opérateur promeut ensuite en type suivi. La qualité de ces
  catégories dépend de leur formulation et doit être validée sur un échantillon
  représentatif.

---

## 5. Réseau, certificats, accès

### 5.1 Flux à ouvrir

| Origine | Destination | Port | Usage |
|---|---|---|---|
| Serveur AI Sentinel | Instance Confluence | 443 | Lecture des espaces, pages, pièces jointes ; écriture si obfuscation |
| Poste des utilisateurs | Serveur AI Sentinel | 4200 | Interface web |
| Serveur applicatif | Serveur d'inférence | 1234 | Uniquement en déploiement sur deux machines |
| Serveur AI Sentinel | `ghcr.io`, `huggingface.co` et son réseau de diffusion | 443 | Premier démarrage uniquement (images et modèles) |

### 5.2 Proxy d'entreprise

Si les flux sortants passent par un proxy, fournir : nom d'hôte, port, et le cas échéant
identifiant et mot de passe. Le produit expose des paramètres dédiés
(`CONFLUENCE_ENABLE_PROXY`, `CONFLUENCE_PROXY_HOST`, `CONFLUENCE_PROXY_PORT`,
`CONFLUENCE_PROXY_USERNAME`, `CONFLUENCE_PROXY_PASSWORD`).

Le trafic vers le moteur d'inférence local ne doit **jamais** passer par le proxy ; le
produit force ce comportement.

### 5.3 Certificat d'autorité interne

Nécessaire dans deux cas : le certificat de Confluence est signé par une autorité de
certification interne, ou le proxy pratique l'inspection du trafic chiffré.

Le client doit alors fournir le certificat racine **dans deux formats**, parce que les deux
services ne valident pas les certificats de la même manière :

| Format attendu | Consommateur | Emplacement |
|---|---|---|
| Fichier PEM (`corporate-ca.pem`) | service de détection Python | `./certs/corporate-ca.pem` |
| Magasin de confiance **complet** au format PKCS12, mot de passe `changeit` (racines publiques **plus** l'autorité interne) | API Java | `./certs/cacerts` |

Le magasin PKCS12 remplace celui de la machine virtuelle Java : il doit contenir les
racines publiques en plus du certificat interne, sinon tous les autres appels HTTPS
échouent. Le fichier `docker-compose-dgnsi.yml` livré avec le produit sert de modèle pour
ce montage.

Symptôme d'un certificat manquant côté Java : `SSLHandshakeException` avec
`PKIX path building failed`, alors que le réseau fonctionne.

### 5.4 Accès VPN

Si l'instance Confluence n'est pas joignable depuis le réseau où tourne le serveur, prévoir
un accès VPN permanent de site à site, ou héberger AI Sentinel dans le réseau qui atteint
déjà Confluence. Un VPN nominatif sur poste de travail n'est pas exploitable pour un
service qui tourne en continu.

### 5.5 Installation hors ligne

Si le serveur n'a aucun accès sortant vers Internet, l'installation hors ligne est
possible : nous livrons les images Docker sous forme d'archive et le cache de modèles
pré-rempli. À signaler avant l'installation, car cela change la procédure et le volume à
transférer (environ 20 Go).

---

## 6. Sécurité de la plateforme

**Point à traiter explicitement dans l'offre : l'application n'embarque aucune
authentification.** L'interface web et l'API sont ouvertes à quiconque atteint le serveur.
La protection est donc entièrement de la responsabilité du réseau. Trois solutions
acceptables :

1. Serveur accessible uniquement depuis un réseau d'administration restreint.
2. Reverse proxy en frontal assurant l'authentification et le chiffrement TLS.
3. Accès local uniquement, par tunnel SSH pour les opérateurs.

Une évolution vers une authentification intégrée (SSO d'entreprise) peut être chiffrée
séparément si le client l'exige.

**Ports à ne jamais exposer hors du serveur** : 5050 (administration de la base, compte par
défaut `admin` / `admin`), 8082 (gestionnaire de secrets), 5432 (base de données), 50051
(service de détection), 8090 (endpoints techniques).

**Traitement des données**

- Les données personnelles détectées **ne quittent jamais le déploiement**. Détection,
  modèle de langage et stockage sont tous locaux. Aucun service en ligne n'est appelé.
- Les valeurs détectées sont stockées **chiffrées** (AES-GCM) et chaque déchiffrement est
  inscrit dans une table d'audit.
- Les rapports Excel contiennent le type de donnée, la page, le score et un **contexte
  masqué** — pas la valeur détectée. Ces fichiers restent néanmoins sensibles : ils
  cartographient où se trouvent les données personnelles. Le répertoire de sortie doit être
  protégé au même niveau que la base.
- La rétention des journaux d'audit est de 730 jours par défaut, avec une purge planifiée.

**Sauvegardes, à cadrer avec l'exploitant du client**

- **La clé de chiffrement des données détectées doit être sauvegardée hors de la machine.**
  Sans elle, tous les résultats stockés sont définitivement illisibles et les liens avec
  les décisions de traitement sont rompus.
- Sauvegarde de la base PostgreSQL selon la politique du client.
- Sauvegarde du répertoire des rapports Excel.

---

## 7. Prérequis organisationnels

Ces points ne sont pas techniques mais bloquent le démarrage plus souvent que le matériel.

- **Base légale et information.** Le scan lit l'intégralité du wiki, données personnelles
  incluses. Le délégué à la protection des données du client doit avoir validé le
  traitement. Selon le pays et le contexte, l'information ou la consultation des
  représentants du personnel peut être requise.
- **Autorisation formelle de scanner** les espaces retenus, délivrée par leur propriétaire.
- **Désignation d'un opérateur** côté client : la personne qui lance les scans, qualifie
  les faux positifs et déclenche l'obfuscation. Compter une charge réelle de tri : sur un
  wiki d'entreprise, un premier scan remonte typiquement plusieurs milliers de résultats.
- **Désignation des responsables par espace**, pour orienter le tri (obligatoire sur Data
  Center, où le produit ne peut pas les déduire).
- **Validation du principe de l'obfuscation** par le métier avant toute écriture en
  production, sur la base d'un essai en environnement de test.
- **Point de contact technique Confluence** identifié, capable de créer le compte de
  service, de fournir le certificat et de purger l'historique des versions.

---

## 8. Fiche de collecte à envoyer au client

À faire remplir avant le chiffrage définitif.

**Instance Confluence**

| Question | Réponse |
|---|---|
| Cloud ou Data Center ? | |
| Version de Confluence (si Data Center) | |
| URL de base complète | |
| Nombre d'espaces à scanner | |
| Nombre total de pages concernées | |
| Nombre et volume des pièces jointes | |
| Langues des contenus | |
| Applications Marketplace avec macros propriétaires | |
| Instance ou espace de test disponible ? | |
| Limitation de débit sur l'API ? | |

**Accès**

| Question | Réponse |
|---|---|
| Compte de service dédié possible ? | |
| Jetons d'accès personnels disponibles (Data Center) ? | |
| Droits en lecture sur tous les espaces visés ? | |
| Droits en écriture souhaités (obfuscation) ? | |
| Pages restreintes : couverture complète ou angles morts acceptés ? | |
| Authentification fédérée (SAML / SSO) sur l'instance ? | |

**Réseau et sécurité**

| Question | Réponse |
|---|---|
| Le serveur peut-il joindre Confluence en direct ? | |
| Proxy sortant (hôte, port, authentification) | |
| Certificat signé par une autorité interne ? | |
| Inspection du trafic chiffré par le proxy ? | |
| Accès sortant vers Internet au premier démarrage, ou installation hors ligne ? | |
| Comment l'accès à l'interface sera-t-il restreint ? | |

**Matériel et exploitation**

| Question | Réponse |
|---|---|
| Machine virtuelle ou serveur physique disponible ? | |
| GPU disponible, et avec quelle mémoire vidéo ? | |
| Système d'exploitation | |
| Docker autorisé sur cette machine ? | |
| Qui exploite la machine (sauvegardes, supervision, mises à jour) ? | |
| Politique de sauvegarde applicable | |

---

## 9. Résumé pour l'offre

Le socle minimum défendable : **une machine 8 cœurs, 32 Go de mémoire vive, un GPU de
16 Go, 250 Go de disque, Docker installé**, plus **un compte de service Confluence en
lecture sur tous les espaces visés**, plus **le certificat de l'autorité interne dans les
deux formats attendus** si l'instance est derrière un certificat privé.

Trois éléments doivent apparaître noir sur blanc dans l'offre, car ils changent le
périmètre :

1. Le moteur d'inférence du modèle de langage n'est pas fourni dans la pile Docker et doit
   être installé et licencié séparément.
2. L'application n'embarque pas d'authentification : la restriction d'accès est à la charge
   du client, ou fait l'objet d'un développement chiffré à part.
3. L'obfuscation ne couvre pas les pièces jointes et ne purge pas l'historique des versions
   de Confluence : la suppression complète demande une intervention d'un administrateur
   Confluence.

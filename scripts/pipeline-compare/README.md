# Comparer les deux pipelines

La référence se trouve dans `.worktrees/detection-baseline`, au commit `9349c5a4`,
point de création de `feature/findings-improvement` confirmé par le reflog Git.
Le worktree est en HEAD détachée. Il ne contient aucun changement de cette branche,
y compris ceux qui ne sont pas encore commités dans le répertoire principal.

| Service | Version actuelle | Référence |
|---|---|---|
| Interface | http://127.0.0.1:4200 | http://127.0.0.1:4201 |
| API | http://127.0.0.1:8080/ai-sentinel | http://127.0.0.1:8181/ai-sentinel |
| gRPC | `127.0.0.1:50051` | `127.0.0.1:50052` |
| PostgreSQL | `127.0.0.1:5433/ai-sentinel` | `127.0.0.1:5437/ai-sentinel-baseline` |
| Services internes Armeria, si activés | `8190` | `8191` |

## Lancement sans IntelliJ

Depuis la racine du dépôt, démarrer la base de référence :

```bash
scripts/pipeline-compare/run.sh baseline db
```

Puis exécuter chacune des commandes suivantes dans un terminal distinct :

```bash
scripts/pipeline-compare/run.sh baseline api
```

```bash
scripts/pipeline-compare/run.sh baseline detector
```

```bash
scripts/pipeline-compare/run.sh baseline ui
```

Remplacer `baseline` par `current` pour lancer la version actuelle avec sa base
existante, également sans IntelliJ. Ne pas lancer un service déjà ouvert dans l’IDE.
Arrêter chaque service avec `Ctrl+C`. Pour arrêter uniquement la nouvelle base :

```bash
docker stop ai-sentinel-baseline-database-1
```

Le volume `ai-sentinel-baseline_database` conserve les nouveaux résultats.
Les exports et logs de référence restent dans le worktree.

## Préparation effectuée sur ce poste

- Base neuve, conteneur, utilisateur, mot de passe et volume dédiés. Aucun résultat
  ni historique de scan copié. Le backend ancien initialise son propre schéma.
- Réglages de connexion Confluence et réglages globaux de détection copiés une seule
  fois depuis la base actuelle. Les types et seuils par type restent ceux de l’ancien
  `data.sql` : c’est une comparaison de la branche complète, pas uniquement des regex.
- Secrets locaux dans `.worktrees/pipeline-compare/baseline.env` et `current.env`,
  exclus de Git, permissions `0600`. La clé de chiffrement est reprise pour lire
  les identifiants Confluence copiés. Les arguments JVM reprennent `PROD-vpn` :
  proxy local sur `8118` et truststore du poste. Le VPN doit être connecté.
- Environnement Python copié indépendamment, avec les mêmes versions de bibliothèques
  que l’environnement actuel. Le chemin du paquet editable pointe vers le worktree.
  Les protobufs sont régénérés depuis l’ancien contrat ; aucun code de détection actuel
  n’est partagé. Ce n’est pas une reconstitution des anciennes versions des bibliothèques.
- Dépendances frontend installées depuis le lockfile de référence ; compilation Maven
  séparée. Java 25, Python 3.13, pnpm et Docker du poste sont réutilisés.
- Les commandes désactivent le benchmark automatique de concurrence Ministral et la
  reprise automatique des scans au démarrage. Les scans restent déclenchés manuellement.
  LM Studio reste partagé : les deux versions peuvent tourner côte à côte, mais lancer
  les scans successivement pour comparer leurs durées sans concurrence sur le modèle.

## Vérification

Avec les trois services de référence démarrés, ce contrôle lit les deux bases,
vérifie le proxy frontend, le backend et la santé gRPC sans lancer de scan :

```bash
.worktrees/detection-baseline/pii-detector-service/.venv/bin/python scripts/pipeline-compare/check.py
```

La création initiale a préservé les 18 068 événements de scan, 3 checkpoints et
1 150 décisions de traitement de la base actuelle. La référence démarre sans résultat.
Le contrôle complet a réussi, y compris l’appel frontend → API → gRPC → LM Studio.
Les services de référence et leur base ont ensuite été arrêtés ; les commandes
ci-dessus les relancent avec les données et réglages conservés.
L’accès Confluence dépend du VPN ; une erreur du tunnel ne remet pas en cause
l’isolation des services locaux.

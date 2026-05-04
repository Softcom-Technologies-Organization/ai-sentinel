# Dev Container POC — ai-sentinel

POC d'environnement de dev unifié pour ai-sentinel via Dev Container, mode natif IntelliJ 2025.3+.

## Ce que ça fournit

- **Service `workspace`** : conteneur Debian Bookworm avec Java 25, Python 3.13, Node 22, Maven, Docker-in-Docker, Claude Code installé.
- **Service `postgres`** : Postgres 16, **réseau interne uniquement** (DNS `postgres:5432` depuis le workspace). Pas exposé sur l'hôte → zéro conflit avec un autre postgres qui tournerait localement.
- **Service `pgadmin`** : pgAdmin 4 sur http://localhost:5050 (`admin@local` / `admin`).
- **Persistance** : volumes nommés pour `~/.m2`, `~/.npm`, `~/.cache/pip`, `~/.claude`, `node_modules`, `pgdata`, `pgadmin-data`.

Les apps métier (`pii-detector`, `pii-reporting-api`, `pii-reporting-ui`) **ne tournent pas dans Docker** en mode dev container : elles sont lancées depuis IntelliJ via les `.run/*.xml`.

## Prérequis hôte

- Docker Desktop (testé avec 29.4.0)
- IntelliJ IDEA Ultimate **2025.3+** ou **2026.1+** (mode Dev Container natif)
- 16 Go RAM minimum, 24 Go recommandés

## Démarrage — via JetBrains Gateway (recommandé)

Le mode Gateway lance un IntelliJ **backend dans le conteneur** + un client léger sur l'hôte. Avantage : settings 100% isolés du host (pas de pollution Scoop/PATH Windows). C'est le mode supporté nativement pour les dev containers JetBrains.

1. Lance **JetBrains Gateway** (à installer séparément depuis [jetbrains.com/gateway](https://www.jetbrains.com/remote-development/gateway/)).
2. Section **Dev Containers** → bouton **New Dev Container**.
3. **From local project** → sélectionner le dossier racine `ai-sentinel`.
4. Gateway lit `.devcontainer/devcontainer.json`, lance le build (5-15 min la 1ʳᵉ fois — features Java/Python/Node + Claude Code).
5. Choisir l'**IDE backend** (IntelliJ IDEA Ultimate). Le backend est téléchargé dans le conteneur la première fois.
6. **JetBrains Client** se connecte automatiquement → tu vois ton projet dans une UI similaire à IntelliJ.
7. Java SDK / Node interpreter : **détectés automatiquement** dans le conteneur.
8. **Python interpreter** : `setup.sh` crée un venv à `pii-detector-service/.venv`. Au premier démarrage, IntelliJ affiche une notification *"Python interpreter detected"* en bas à droite → cliquer **Use this interpreter**. Le SDK est enregistré pour de bon (les démarrages suivants n'ont plus rien à confirmer).
   - Si la run config `server` affiche encore `<No interpreter>` après avoir accepté la notif : ouvrir le dialog (comme dans la capture), choisir le venv dans le dropdown — IntelliJ le mémorise pour la run config.
   - Si la notif n'apparaît pas : **File → Project Structure → SDKs → +** → Python SDK → existing virtualenv → `/workspaces/ai-sentinel/pii-detector-service/.venv/bin/python`.

## Auto-purge des chemins hôte pollués

`.idea/workspace.xml` (généré par IntelliJ desktop si le projet a été ouvert hors dev container) peut contenir des chemins absolus Windows (ex. `C:\Users\...\scoop\...\pnpm.cmd`). Ces chemins cassent l'IDE quand il tourne dans le conteneur Linux.

Le `postStartCommand` du `devcontainer.json` exécute `purge-host-paths.sh` à **chaque démarrage** du conteneur, qui supprime les lignes pollués (`nodejs_package_manager_path`, `ts.external.directory.path`, etc. quand elles contiennent un drive letter Windows). IntelliJ régénère ensuite ces settings par auto-détection dans le contexte du conteneur.

→ Aucune intervention manuelle requise par dev. Le fix vit dans `.devcontainer/`, versionné avec le projet.

## Démarrage — via IntelliJ natif (alternative, déconseillée)

Si tu insistes pour utiliser ton IntelliJ desktop habituel :
1. Ouvrir le projet dans IntelliJ 2025.3+ → il détecte `.devcontainer/`.
2. **Limitation connue** : IntelliJ desktop applique ses settings utilisateur globaux (Node interpreter, package manager) au projet → si tu as un Node Scoop sur l'hôte, il essaiera d'utiliser `C:\...\scoop\...\pnpm.cmd` dans le conteneur Linux et plantera.
3. Workaround : reconfigurer manuellement Node interpreter + Package manager dans Settings → Languages & Frameworks → Node.js, à chaque ouverture. **Mode Gateway évite ce problème.**

## Clé d'encryption DB (`PII_DATABASE_ENCRYPTION_KEY`)

Les fichiers `devcontainer.json` (`containerEnv`) et `docker-compose.dev.yml` (`environment` du service `workspace`) contiennent un placeholder `PII_DATABASE_ENCRYPTION_KEY: changeme`. Cette KEK (Key Encryption Key) sert à `pii-reporting-api` pour chiffrer les PII en base via AES-256-GCM (cf. `pii-reporting-api/ENCRYPTION.md`). **Avant de lancer l'API**, remplacer `changeme` par une vraie clé base64 32 bytes — sinon l'init du chiffrement échoue.

Génération d'une clé :

```bash
# Dans le terminal du dev container (Linux)
openssl rand -base64 32
```

Ou côté hôte Windows (script existant qui pose la valeur dans une variable utilisateur) :

```powershell
.\pii-reporting-api\create-new-encryption-key.ps1
```

Coller la valeur générée aux **deux endroits** (`devcontainer.json` et `docker-compose.dev.yml`) en remplacement de `changeme`, puis **Rebuild Container** pour propager.

⚠️ Ne pas commiter une vraie clé. Le placeholder `changeme` est volontairement gardé sous Git pour signaler l'étape obligatoire — toute valeur réelle doit rester locale (la modif de `devcontainer.json`/`docker-compose.dev.yml` ne doit pas être pushée).

## Premier lancement Claude Code

Dans le terminal IntelliJ (qui tourne dans le conteneur) :
```bash
claude
```
Suivre le flow OAuth (abonnement Anthropic). Les credentials sont persistés dans le volume `ai-sentinel-claude` → pas de re-login après rebuild.

## Lancer les apps depuis IntelliJ

Les `.run/*.xml` versionnés (`server.run.xml`, `PROD.run.xml`, etc.) ne sur-définissent **pas** `DB_HOST`/`DB_PORT` → ils héritent des `containerEnv` du `devcontainer.json` (`DB_HOST=postgres`, `DB_PORT=5432`) quand on les lance depuis le conteneur. Côté hôte (hors dev container), Postgres est exposé sur `localhost:5433` via un compose séparé (cf. `docker-compose.yml` racine).

Exception : `server-initial.run.xml` et `PROD-initial.run.xml` ont en dur `DB_HOST=127.0.0.1` + `DB_PORT=5433` → utilisables seulement hors dev container, ou à adapter manuellement avant lancement dans le conteneur.

### Frontend Angular dans le devcontainer

Pour le frontend, **utiliser la run config `start (devcontainer)`** (générée automatiquement par `setup.sh`), pas `start`. La config `start` lance `ng serve` qui bind sur `127.0.0.1` dans le conteneur — le port-forwarding 4200 ne peut pas atteindre cette interface et `http://localhost:4200` renvoie `ERR_EMPTY_RESPONSE` côté hôte. La variante `start (devcontainer)` exécute le script `start:dev` (`ng serve --host 0.0.0.0`) qui écoute sur toutes les interfaces, ce qui rend le port-forwarding fonctionnel.

Le fichier `.run/start (devcontainer).run.xml` est généré localement par `setup.sh` et non versionné (`.run/` est dans `.gitignore` pour les nouveaux fichiers).

## Conflits avec le `docker-compose.yml` principal

Si tu as encore un postgres / pgadmin / autre service du `docker-compose.yml` principal qui tourne, il faut l'arrêter avant de démarrer le dev container :
```bash
docker compose -f docker-compose.yml down
```
Le dev container fournit son propre postgres + pgadmin (réseau interne, pgAdmin sur 5050).

## Limitations connues

- **Java 25** : très récent (sept. 2025). Si la feature `ghcr.io/devcontainers/features/java:1` ne supporte pas encore 25, fallback temporaire sur Java 21 (downgrade temporaire dans `devcontainer.json`).
- **`-initial.run.xml`** : ces variantes ont des env vars DB en dur pour usage hôte (cf. section précédente).
- **Python SDK premier démarrage** : 1 clic requis sur la notification *"Python interpreter detected"* (cf. section démarrage Gateway, point 8). Pas trouvé de mécanisme officiel JetBrains pour pré-configurer un Python SDK depuis `devcontainer.json` côté backend Gateway.
- **Worktrees parallèles** : non couvert par ce POC. Voir `docs/dev-container-brief.md` §5 pour la stratégie cible.
- **Infisical** : retiré du compose dev → les apps doivent consommer les creds postgres via les env vars du `workspace`, pas via Infisical SDK.

## Reset complet

Pour repartir de zéro (perd tous les caches) :
```bash
docker compose -f .devcontainer/docker-compose.dev.yml down -v
```
Puis "Rebuild Container" dans IntelliJ.

## Structure des fichiers

```
.devcontainer/
├── devcontainer.json        # Config principale
├── docker-compose.dev.yml   # Services workspace + postgres + pgadmin
├── setup.sh                 # postCreateCommand (Maven offline, npm ci, pip install)
└── README.md                # Ce fichier
```

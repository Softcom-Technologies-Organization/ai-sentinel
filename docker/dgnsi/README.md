# Release candidate DGNSI — Strata et Qwen3.8-Flash-Next

Branche : `release/v1.2.0-rc.2-strata-qwen`. Tag des trois images :
`v1.2.0-rc.2-strata-qwen`. Les commandes ci-dessous s'exécutent en Bash ou zsh,
depuis la racine du dépôt ou le répertoire de déploiement contenant les deux fichiers Compose.

## Configuration retenue

| Fonction | Configuration |
|---|---|
| Application | Trois services Docker, PostgreSQL et Infisical existants |
| Juge | Strata sur l'hôte Apple Silicon, port `8081` |
| Modèle | `qwen3.8-flash-next-iq3_s`, quantification IQ3_S |
| Prompt | `per_type`, repris du benchmark du 1er novembre 2026 |
| Décision | Seuil de confiance `0.95`, mode initial `PROPOSE` |
| Inférence | Contexte `32768`, quatre requêtes simultanées, sortie `2048`, température `0`, thinking désactivé |
| Détection initiale | Presidio, Regex et Ministral selon la configuration PostgreSQL |

Le juge traite les résultats d'un scan ; il ne remplace pas Ministral pour leur extraction.
L'overlay fixe `LLM_JUDGE_HOST=host.docker.internal` et `LLM_JUDGE_PORT=8081` côté API.
L'adresse du détecteur Ministral reste dans `lm_studio_host` / `lm_studio_port`.
Depuis Docker, cette adresse doit également désigner un service accessible au conteneur,
par exemple `host.docker.internal:1234`, jamais `localhost` pour un serveur situé sur l'hôte.

La référence conservée est le [rapport du benchmark](../../benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/RAPPORT.md),
avec résultats bruts, protocole, prompts et empreintes SHA-256. Sur le test synthétique :
précision 99,82 %, rappel 91,56 %, F1 95,51 %. Ce score mesure le jugement des candidats,
pas le rappel du scan Confluence complet.

Les travaux Jev antérieurs ont été conservés dans le stash
`bf226a9f0bcdf0b75bc7c443d3fd44b4e176ee23`, intitulé
`pre-rc-strata-qwen-20261102 feature/findings-improvement Jev WIP and local research`.
Ne pas réappliquer ce stash sur la RC : il contient une autre expérimentation de filtrage.

## Strata sur l'hôte

Installer Strata et les fichiers du modèle avant le déploiement. Le modèle du benchmark
occupe environ 84 Go ; il faut aussi la mémoire du contexte et celle de l'application.
Le preset `Qwen3.8 local` correspond à celui du benchmark. Vérifier son contenu avec :

```bash
strata launch --preset 'Qwen3.8 local' --port 8081 --context 32768 --parallel 4 --no-tui --plan
```

Fournir une clé à Strata dans la variable d'environnement `LLM_JUDGE_API_KEY`, puis
enregistrer la même valeur dans Infisical, environnement `prod`, pour le détecteur.
Ne pas écrire cette clé dans le dépôt ni dans une commande littérale. Le port `8081`
évite le conflit avec l'API de l'application, qui utilise `8080`.

```bash
strata launch --preset 'Qwen3.8 local' --port 8081 --context 32768 --parallel 4 --access lan --api-key-env LLM_JUDGE_API_KEY --no-tui
```

Le mode `lan` permet l'accès depuis Docker. Limiter l'accès réseau au poste Docker et
conserver l'authentification. Si le preset tourne déjà avec d'autres paramètres, Strata
refuse de le remplacer silencieusement : l'arrêter explicitement lorsque les benchmarks
ou autres utilisateurs ont terminé. La préparation de cette RC ne modifie pas le serveur en cours.

Sans clé, Strata refuse par défaut le nom `host.docker.internal` avec un HTTP 403
de protection contre le DNS rebinding. Le lancement authentifié ci-dessus évite ce refus.
Un serveur déjà actif en mode local sans clé n'est donc pas une validation du chemin Docker.

L'overlay permet aussi de passer `LLM_JUDGE_API_KEY` depuis l'environnement du shell.
Infisical reste la source recommandée ; ne pas créer deux valeurs différentes.

## Construire et publier

Sur le Mac cible, ces builds produisent les images `linux/arm64` :

```bash
docker build --platform linux/arm64 -f pii-detector-service/Dockerfile -t ghcr.io/softcom-technologies-organization/ai-sentinel-pii-detector:v1.2.0-rc.2-strata-qwen .
docker build --platform linux/arm64 -f pii-reporting-api/Dockerfile -t ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:v1.2.0-rc.2-strata-qwen .
docker build --platform linux/arm64 -f pii-reporting-ui/Dockerfile -t ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-ui:v1.2.0-rc.2-strata-qwen pii-reporting-ui
```

Pour une machine Docker x86, remplacer `linux/arm64` par `linux/amd64`.
Le workflow `.github/workflows/build-test-publish-docker-images.yml` exécute les tests
puis publie les deux architectures. Un push de cette branche `release/**` déclenche
la publication avec le suffixe de branche comme tag. Le lancement manuel peut utiliser
ce même tag. La préparation locale de la RC ne déclenche aucune publication.

Pour transférer les images de l'application au poste DGNSI :

```bash
docker save -o /private/tmp/ai-sentinel-v1.2.0-rc.2-strata-qwen.tar ghcr.io/softcom-technologies-organization/ai-sentinel-pii-detector:v1.2.0-rc.2-strata-qwen ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:v1.2.0-rc.2-strata-qwen ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-ui:v1.2.0-rc.2-strata-qwen
```

Le fichier doit contenir l'architecture du poste destinataire. Pour une installation
ne disposant pas encore des images d'infrastructure, télécharger puis exporter aussi
`alpine:3.20`, `postgres:18`, `postgres:18-alpine`, `redis:7-alpine`,
`infisical/infisical:v0.153.4` et `mailhog/mailhog:latest` ; `dpage/pgadmin4:latest`
est nécessaire seulement avec le profil `admin`. Le premier bootstrap Infisical
installe `curl` et `jq` via les dépôts Alpine : transférer les images ne suffit pas
à rendre une installation neuve entièrement hors ligne. Une installation existante
déjà configurée évite cette étape.

## Mettre à jour une installation existante

Arrêter les scans et les jobs de jugement avant la sauvegarde. Conserver les fichiers
Compose précédents et les anciennes images pour un retour à la version précédente.
Sauvegarder PostgreSQL **et** le répertoire `secrets/` : une base sans sa clé de
chiffrement ne permet pas de récupérer les détections.

```bash
export DGNSI_BACKUP_DIR="$HOME/ai-sentinel-backups/rc-20261102"
mkdir -p "$DGNSI_BACKUP_DIR"
chmod 700 "$DGNSI_BACKUP_DIR"
docker compose -f docker-compose.yml -f docker-compose-dgnsi.yml exec -T postgres sh -c 'pg_dump -U "$(cat /secrets/postgres_user.txt)" -d ai-sentinel -Fc' > "$DGNSI_BACKUP_DIR/ai-sentinel.dump"
tar -czf "$DGNSI_BACKUP_DIR/secrets.tar.gz" secrets
chmod 600 "$DGNSI_BACKUP_DIR/ai-sentinel.dump" "$DGNSI_BACKUP_DIR/secrets.tar.gz"
```

Charger l'archive transférée, puis vérifier les certificats DGNSI :
`certs/corporate-ca.pem` pour Python et `certs/cacerts` pour Java. Le truststore Java
doit contenir les racines publiques **et** l'autorité DGNSI, au format PKCS12, avec
le mot de passe `changeit`. Le bundle Python doit également conserver les racines
nécessaires aux téléchargements externes.

```bash
docker load -i /private/tmp/ai-sentinel-v1.2.0-rc.2-strata-qwen.tar
docker compose -f docker-compose.yml -f docker-compose-dgnsi.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose-dgnsi.yml up -d --wait --wait-timeout 180 --force-recreate pii-detector pii-reporting-api pii-reporting-ui
docker compose -f docker-compose.yml -f docker-compose-dgnsi.yml exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$(cat /secrets/postgres_user.txt)" -d ai-sentinel' < docker/dgnsi/upgrade-strata-judge.sql
```

Le premier démarrage de l'API RC complète le schéma Hibernate, notamment la colonne
du modèle du juge si l'ancienne version ne la possédait pas. Le SQL choisit ensuite
Qwen pour le juge de la configuration existante. `data.sql` ne remplace
pas les paramètres déjà enregistrés, et les scripts `init-scripts/` ne sont pas
exécutés automatiquement sur une base existante en production.

Avant un scan client, vérifier dans les paramètres les détecteurs activés, les types
actifs et leurs seuils. Un type activé ne fonctionne que si son détecteur l'est aussi.
Si Ministral est désactivé ou inaccessible, le juge ne peut pas retrouver les secrets
qui n'ont pas été détectés. Garder les nouvelles règles nationales SSN/Tax ID désactivées
tant que leur activation n'a pas été décidée pour ce client ; ne pas réactiver en bloc
le catalogue de regex. La suppression des filtres agressifs de secrets est dans le code
de la RC et ne dépend pas de la désactivation de tout le post-filtre.

Pour le triage avec affichage des valeurs, définir explicitement
`PII_REPORTING_ALLOW_SECRET_REVEAL=true` dans Infisical `prod`, puis recréer l'API.
La valeur par défaut Docker est `false`, conformément au contrôle d'accès existant.

Le mode `PROPOSE` enregistre les rejets comme `PROPOSED_FALSE_POSITIVE` pour relecture.
Il ne les retire pas des rapports et ne modifie pas Confluence.

## Vérifications avant le scan

```bash
docker compose -f docker-compose.yml -f docker-compose-dgnsi.yml ps
curl --fail --silent http://localhost:8080/ai-sentinel/actuator/health
curl --fail --silent http://localhost:4200/ > /dev/null
```

Dans l'interface, ouvrir le juge : Qwen doit être disponible sur Strata, le seuil doit
être `95 %` et le mode `Proposer` doit être sélectionné. Vérifier un petit lot de
données de test avant d'ouvrir le scan client. Une erreur d'appel, un verdict `UNSURE`
ou une confiance insuffisante doivent conserver la détection.

Conserver la sauvegarde et la clé initiale pendant toute la campagne. Pour revenir en
arrière, arrêter les jobs, rétablir les anciens fichiers Compose/images et restaurer
la sauvegarde si nécessaire. Ne pas supprimer les volumes Docker pour appliquer cette RC.

Les vérifications réalisées sur cette branche et leurs limites sont consignées dans
[VALIDATION.md](VALIDATION.md).

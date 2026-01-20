# Publication Manuelle d'Images Docker

## Objectif

Ce guide explique comment publier manuellement des images Docker avec des tags personnalisés depuis n'importe quelle branche, permettant de tester des images avant de merger sur `develop`.

## ⚠️ IMPORTANT: Localiser le Bouton "Run Workflow"

### Étapes Précises pour Accéder au Workflow Manuel

Le bouton "Run workflow" n'est visible que dans des conditions spécifiques. Suivez **exactement** ces étapes:

#### 1️⃣ Naviguer vers GitHub Actions

```
https://github.com/Softcom-Technologies-Organization/ai-sentinel/actions
```

Ou depuis la page du repo:
- Cliquez sur l'onglet **"Actions"** en haut de la page

#### 2️⃣ Sélectionner le Workflow Correct

Dans la **barre latérale gauche**, vous verrez une liste de workflows. Cliquez sur:

```
📋 CI/CD - Tests and Publish
```

**⚠️ ATTENTION**: Ne cliquez PAS sur un run existant dans la liste centrale !

#### 3️⃣ Vérifier la Branche

En haut de la page, vous verrez un **sélecteur de branche**:

```
Branch: [main ▼]
```

**Changez pour votre branche**: `feature/config-ui`

#### 4️⃣ Localiser le Bouton

Une fois sur la bonne branche, le bouton **"Run workflow"** apparaît:

```
┌─────────────────────────────────────────┐
│  This workflow has a workflow_dispatch   │
│  event trigger.                          │
│                                          │
│  [Run workflow ▼]  ← CLIQUEZ ICI         │
└─────────────────────────────────────────┘
```

**Position**: En haut à droite, à côté du bouton de filtre

#### 5️⃣ Si le Bouton N'Apparaît Pas

**Causes possibles**:

1. **Workflow pas encore synchronisé**
   ```bash
   # Vérifier que le workflow est bien poussé
   git log --oneline -1 .github/workflows/build-test-publish-docker-images.yml
   ```
   
   **Solution**: Attendre 1-2 minutes que GitHub actualise, puis rafraîchir la page

2. **Vous n'êtes pas sur la bonne branche**
   
   **Solution**: Vérifier le sélecteur de branche en haut

3. **Permissions insuffisantes**
   
   **Solution**: Vous devez avoir les droits "Write" ou "Admin" sur le repo

4. **Vous regardez un run existant**
   
   **Solution**: Retourner à la vue du workflow (étape 2)

### Capture d'Écran de Référence

```
┌──────────────────────────────────────────────────────────────┐
│ GitHub Actions                                                │
├──────────────────────────────────────────────────────────────┤
│                                                                │
│  Workflows               CI/CD - Tests and Publish            │
│  ├─ CI/CD - Tests...    ─────────────────────────────         │
│  ├─ CodeQL             Branch: feature/config-ui ▼            │
│  └─ ...                                                        │
│                         [Run workflow ▼] ← BOUTON ICI         │
│  ──────────────────────────────────────────────────           │
│  All workflows (35)    ⚙️ This workflow has a...              │
│  ├─ Run #123          event trigger.                          │
│  ├─ Run #122                                                   │
│  └─ ...                                                        │
└──────────────────────────────────────────────────────────────┘
```

## Fonctionnalités

Le workflow `CI/CD - Tests and Publish` supporte maintenant:
- ✅ Publication manuelle depuis **n'importe quelle branche**
- ✅ Tags personnalisés pour les images Docker
- ✅ Sélection individuelle des services à builder (Detector, API, UI)
- ✅ Tests automatiques avant publication

## Utilisation

### 1. Accéder au Workflow Manuel

1. Allez sur GitHub : `https://github.com/Softcom-Technologies-Organization/ai-sentinel/actions`
2. Sélectionnez le workflow **"CI/CD - Tests and Publish"** dans la liste de gauche
3. Sélectionnez votre branche dans le dropdown en haut (ex: `feature/config-ui`)
4. Cliquez sur le bouton **"Run workflow"** (en haut à droite)

### 2. Configurer la Publication

Un formulaire s'affiche avec les options suivantes:

#### Branch Selection
- **Use workflow from**: Choisissez la branche depuis laquelle publier
  - Exemples: `feature/new-feature`, `bugfix/issue-123`, `develop`, `main`
  - **Par défaut**: La branche actuellement affichée

#### Custom Tag (Optional)
- **Custom tag for images**: Tag personnalisé pour les images
  - Par défaut: `manual`
  - Exemples: `test-v1`, `feature-123`, `hotfix-xyz`
  - Le tag sera appliqué à **toutes** les images sélectionnées

#### Services à Builder
Cochez les services que vous souhaitez builder:
- ☑️ **Build PII Detector** (par défaut: activé)
- ☑️ **Build Reporting API** (par défaut: activé)
- ☑️ **Build Reporting UI** (par défaut: activé)

### 3. Lancer la Publication

Cliquez sur le bouton vert **"Run workflow"** dans le formulaire pour démarrer la publication.

## Exemples d'Utilisation

### Exemple 1: Tester une Feature Complète

**Contexte**: Vous travaillez sur une branche `feature/pii-settings` et voulez tester l'intégration complète.

**Configuration**:
```
Branch: feature/pii-settings
Custom tag: test-pii-settings
Build PII Detector: ✓
Build Reporting API: ✓
Build Reporting UI: ✓
```

**Images produites**:
```
ghcr.io/softcom-technologies-organization/ai-sentinel-pii-detector:test-pii-settings
ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:test-pii-settings
ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-ui:test-pii-settings
```

**Utilisation dans docker-compose**:
```yaml
services:
  pii-detector:
    image: ghcr.io/softcom-technologies-organization/ai-sentinel-pii-detector:test-pii-settings
  
  pii-reporting-api:
    image: ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:test-pii-settings
  
  pii-reporting-ui:
    image: ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-ui:test-pii-settings
```

### Exemple 2: Tester Uniquement le Backend

**Contexte**: Modification uniquement de l'API et du détecteur.

**Configuration**:
```
Branch: feature/api-improvement
Custom tag: api-test-v2
Build PII Detector: ✓
Build Reporting API: ✓
Build Reporting UI: ✗
```

**Images produites**:
```
ghcr.io/softcom-technologies-organization/ai-sentinel-pii-detector:api-test-v2
ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:api-test-v2
```

### Exemple 3: Hotfix Urgent

**Contexte**: Correctif urgent à tester avant le déploiement.

**Configuration**:
```
Branch: hotfix/critical-bug
Custom tag: hotfix-2025-01-12
Build PII Detector: ✗
Build Reporting API: ✓
Build Reporting UI: ✗
```

**Image produite**:
```
ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:hotfix-2025-01-12
```

## Stratégie de Tagging

Le workflow applique différentes stratégies de tagging selon le contexte:

### 1. Publication Manuelle avec Custom Tag

Si vous utilisez `workflow_dispatch` avec un custom tag:
```bash
Image tag: <custom-tag>
Exemple: test-pii-settings
```

### 2. Push sur main

Publication automatique avec version + latest:
```bash
Image tags: <version>, latest
Exemple: 1.0.0, latest
```

### 3. Push sur develop

Publication automatique avec version uniquement:
```bash
Image tag: <version>
Exemple: 1.0.0
```

### 4. Autre branche (fallback)

Si aucun custom tag n'est fourni sur une branche feature:
```bash
Image tag: <version>-<branch-name>
Exemple: 1.0.0-feature-pii-settings
```

## Process de Test

### Workflow Complet

1. **Développement Local**
   ```bash
   git checkout -b feature/my-feature
   # Développer et tester localement
   git commit -am "feat: nouvelle fonctionnalité"
   git push origin feature/my-feature
   ```

2. **Publication Manuelle**
   - Aller sur GitHub Actions
   - Lancer le workflow manuel depuis `feature/my-feature`
   - Tag: `test-my-feature`

3. **Test en Environnement**
   ```bash
   # Mettre à jour docker-compose avec le nouveau tag
   docker-compose pull
   docker-compose up -d
   # Tester l'application
   ```

4. **Validation et Merge**
   ```bash
   # Si les tests sont OK
   git checkout develop
   git merge feature/my-feature
   git push origin develop
   # Workflow automatique publie avec tag de version
   ```

## Sécurité et Permissions

### Permissions Requises

Pour exécuter le workflow manuel, vous devez avoir:
- **Write access** ou **Admin** au repository
- Permissions pour exécuter les GitHub Actions

### Images Publiées

Les images sont publiées sur:
```
GitHub Container Registry (ghcr.io)
```

Avec les permissions:
- **Public** ou **Private** selon la configuration du repository
- Authentification requise pour pull des images privées

### Authentification Docker

Pour pull des images privées:
```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin
docker pull ghcr.io/softcom-technologies-organization/ai-sentinel-reporting-api:test-my-feature
```

## Bonnes Pratiques

### ✅ À Faire

1. **Nommage des Tags**
   - Utiliser des noms descriptifs: `test-feature-name`, `hotfix-issue-123`
   - Inclure la date pour les tests: `test-2025-01-12`
   - Préfixer par le type: `feature-`, `bugfix-`, `hotfix-`

2. **Sélection des Services**
   - Builder uniquement les services modifiés pour gagner du temps
   - Builder tous les services pour tests d'intégration complets

3. **Nettoyage**
   - Supprimer les images de test après validation
   - Nettoyer les tags temporaires dans GitHub Container Registry

### ❌ À Éviter

1. **Tags Génériques**
   - Éviter: `test`, `temp`, `tmp`
   - Risque de confusion et d'écrasement

2. **Publication Sans Tests**
   - Le workflow exécute les tests automatiquement
   - Ne pas skip les tests

3. **Accumulation d'Images**
   - Ne pas laisser des dizaines d'images de test
   - Nettoyer régulièrement

## Dépannage

### Bouton "Run Workflow" Invisible

**Problème**: Le bouton n'apparaît pas dans GitHub Actions.

**Solutions par ordre de priorité**:

1. **Vérifier que vous êtes sur la vue du workflow**
   - URL doit ressembler à: `.../actions/workflows/build-test-publish-docker-images.yml`
   - PAS sur un run spécifique: `.../actions/runs/123456`

2. **Vérifier la branche sélectionnée**
   - Changer le sélecteur de branche en haut pour `feature/config-ui`
   - Rafraîchir la page après changement de branche

3. **Attendre la synchronisation GitHub**
   - Attendre 1-2 minutes après le push
   - Forcer le rafraîchissement: Ctrl+F5 (Windows) ou Cmd+Shift+R (Mac)

4. **Vérifier les permissions**
   - Vous devez avoir au minimum "Write" access
   - Contacter un admin si nécessaire

5. **Vérifier le workflow**
   ```bash
   # Localement, vérifier que le workflow contient workflow_dispatch
   grep -A 5 "workflow_dispatch:" .github/workflows/build-test-publish-docker-images.yml
   ```

### Workflow Fails au Build

**Problème**: Le build échoue lors du workflow manuel.

**Solutions**:
```bash
# Vérifier que le build fonctionne localement
docker build -t test-local ./pii-reporting-api
docker build -t test-local ./pii-reporting-ui
docker build -t test-local ./pii-detector-service
```

### Tests Échouent

**Problème**: Les tests échouent avant la publication.

**Cause**: Le code ne respecte pas les standards de qualité.

**Solution**: Corriger les tests avant de publier.

### Image Non Visible dans le Registry

**Problème**: L'image est publiée mais non visible.

**Vérification**:
1. Aller sur `https://github.com/orgs/Softcom-Technologies-Organization/packages?repo_name=ai-sentinel`
2. Vérifier la présence du tag
3. Vérifier les permissions du package

## Ressources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Dispatch Event](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#workflow_dispatch)
- [Docker Build Push Action](https://github.com/docker/build-push-action)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

## Support

Pour toute question ou problème:
1. Vérifier la documentation ci-dessus
2. Consulter les logs du workflow sur GitHub Actions
3. Contacter l'équipe DevOps

---

**Dernière mise à jour**: 2025-01-12  
**Version du workflow**: v2.0

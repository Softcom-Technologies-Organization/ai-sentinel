# Guide d'utilisation du script test-piiranha-v3.py

Ce guide explique comment installer et exécuter le script `test-piiranha-v3.py`, qui utilise le modèle PIIRANHA pour détecter et masquer les informations personnelles (PII) dans des textes.

## Qu'est-ce que PIIRANHA ?

PIIRANHA (Personal Information Identification and Redaction with Advanced Natural Human-like Accuracy) est un modèle d'intelligence artificielle développé pour détecter les informations personnelles dans les textes. Il peut identifier de nombreux types d'informations sensibles comme :

- Noms et prénoms
- Adresses email
- Numéros de téléphone
- Adresses postales
- Numéros de cartes de crédit
- Numéros de sécurité sociale
- Et bien d'autres...

## Prérequis

Avant d'exécuter le script, vous devez installer les éléments suivants :

1. **Python 3.6 ou supérieur**
   - Téléchargez et installez Python depuis [python.org](https://www.python.org/downloads/)
   - Assurez-vous que Python est ajouté à votre PATH système

2. **Environnement virtuel Python (recommandé)**
   ```
   python -m venv .venv
   ```

3. **Activation de l'environnement virtuel**
   - Windows:
     ```
     .venv\Scripts\activate
     ```
   - macOS/Linux:
     ```
     source .venv/bin/activate
     ```

4. **Bibliothèques Python requises**
   ```
   pip install transformers huggingface_hub
   ```

5. **Clé API Hugging Face**
   - Créez un compte sur [Hugging Face](https://huggingface.co/)
   - Générez une clé API dans les paramètres de votre compte
   - Définissez la clé comme variable d'environnement:
     - Windows:
       ```
       set HUGGING_FACE_API_KEY=votre_clé_api
       ```
     - macOS/Linux:
       ```
       export HUGGING_FACE_API_KEY=votre_clé_api
       ```

## Correction d'un bug connu

Avant d'exécuter le script, vous devez corriger une erreur de typo à la ligne 125 du fichier `test-piiranha-v3.py`. Ouvrez le fichier dans un éditeur de texte et modifiez :

```python
# Ligne 125 - Avant correction
masked_text = t+ext

# Après correction
masked_text = text
```

## Exécution du script

Une fois les prérequis installés et le bug corrigé, vous pouvez exécuter le script :

```
python .venv\test-piiranha-v3.py
```

### Ce qui se passe lors de l'exécution

1. **Téléchargement du modèle**
   - Le script télécharge automatiquement les fichiers du modèle PIIRANHA depuis Hugging Face
   - Les fichiers sont stockés dans le cache local de Hugging Face

2. **Chargement du modèle**
   - Le modèle et le tokenizer sont chargés en mémoire
   - Un pipeline de détection est créé

3. **Exécution des tests**
   - Le script exécute des tests sur des exemples prédéfinis en anglais et en français
   - Les résultats montrent les informations personnelles détectées et les textes anonymisés

4. **Mode interactif**
   - Vous pouvez entrer vos propres textes pour tester la détection
   - Tapez 'quit' pour quitter le mode interactif

## Exemple de sortie attendue

Voici un exemple de ce que vous devriez voir lors de l'exécution du script :

```
📥 Téléchargement du modèle...
✅ Téléchargement terminé
🔄 Chargement du modèle...
✅ Modèle chargé avec succès

============================================================
DÉMONSTRATION DE DÉTECTION DE PII
============================================================

🇬🇧 Anglais:
Texte original: Hello, my name is John Smith. You can reach me at john.smith@company.com or call 555-123-4567. I live at 123 Main Street, New York, NY 10001.

📍 Entités détectées:
  • 'John' → Prénom (confiance: 99.9%)
  • 'Smith' → Nom de famille (confiance: 99.8%)
  • 'john.smith@company.com' → Email (confiance: 99.9%)
  • '555-123-4567' → Numéro de téléphone (confiance: 99.7%)
  • '123 Main Street' → Rue (confiance: 98.5%)
  • 'New York' → Ville (confiance: 99.2%)
  • '10001' → Code postal (confiance: 99.6%)

🔐 Texte anonymisé: Hello, my name is [GIVENNAME] [SURNAME]. You can reach me at [EMAIL] or call [TELEPHONENUM]. I live at [STREET], [CITY], NY [ZIPCODE].

📊 Résumé: Prénom: 1, Nom de famille: 1, Email: 1, Numéro de téléphone: 1, Rue: 1, Ville: 1, Code postal: 1
```

## Résolution des problèmes courants

### Erreur : "No module named 'transformers'"

```
pip install transformers
```

### Erreur : "No module named 'huggingface_hub'"

```
pip install huggingface_hub
```

### Erreur : "NameError: name 't' is not defined"

Corrigez la ligne 125 comme indiqué dans la section "Correction d'un bug connu".

### Erreur : "Unable to load weights from safetensors"

Installez la bibliothèque safetensors :

```
pip install safetensors
```

### Erreur : "HUGGING_FACE_API_KEY not found"

Assurez-vous d'avoir défini la variable d'environnement HUGGING_FACE_API_KEY :

```
set HUGGING_FACE_API_KEY=votre_clé_api  # Windows
export HUGGING_FACE_API_KEY=votre_clé_api  # macOS/Linux
```

### Erreur : "ValueError: Unrecognized configuration class"

Si vous rencontrez cette erreur, assurez-vous d'utiliser la bonne classe de modèle. Le script `test-piiranha-v3.py` utilise `AutoModelForTokenClassification` tandis que `download-piiranha.py` utilise `AutoModelForSequenceClassification`.

## Remarques supplémentaires

- Le premier téléchargement du modèle peut prendre plusieurs minutes selon votre connexion internet
- Le modèle occupe environ 500 Mo d'espace disque
- L'exécution sur CPU peut être lente; si vous disposez d'un GPU compatible avec PyTorch, vous pouvez modifier la ligne 72 pour utiliser le GPU

## Utilisation du script convert_model.py

Le script `convert_model.py` permet de convertir le modèle PIIRANHA au format ONNX pour une inférence plus rapide et une meilleure portabilité.

### Prérequis

Avant d'exécuter le script, vous devez installer les dépendances requises :

```
pip install -r requirements.txt
```

Ou installer les packages individuellement :

```
pip install transformers torch optimum[onnxruntime] onnx onnxruntime
```

### Exécution du script

Une fois les dépendances installées, vous pouvez exécuter le script :

```
python convert_model.py
```

### Ce que fait le script

1. Télécharge le modèle PIIRANHA depuis Hugging Face
2. Convertit le modèle au format ONNX
3. Sauvegarde le modèle converti dans le dossier `models/piiranha-onnx`
4. Effectue un test simple pour vérifier que le modèle fonctionne correctement

### Résolution des problèmes

Si vous rencontrez une erreur concernant des packages manquants, suivez les instructions affichées par le script pour installer les dépendances requises.

## Tests avec pytest

Le projet inclut une suite de tests complète utilisant pytest pour valider le fonctionnement de la classe `PIIDetector`.

### Installation des dépendances de test

Les dépendances de test sont incluses dans le fichier `requirements.txt`. Pour les installer :

```bash
pip install -r requirements.txt
```

Ou installer pytest et ses extensions individuellement :

```bash
pip install pytest pytest-cov pytest-mock pytest-asyncio pytest-xdist
```

### Structure des tests

```
tests/
├── __init__.py
└── test_pii_detector.py    # Tests pour la classe PIIDetector
```

### Exécution des tests

#### Exécuter tous les tests
```bash
pytest
```

#### Exécuter les tests avec couverture de code
```bash
pytest --cov=pii-grpc-service --cov-report=html
```

#### Exécuter un test spécifique
```bash
pytest tests/test_pii_detector.py::TestPIIDetector::test_detect_pii_success -v
```

#### Exécuter les tests en parallèle
```bash
pytest -n auto
```

### Configuration pytest

Le fichier `pytest.ini` configure automatiquement :
- **Couverture de code** : Seuil minimum de 80%
- **Rapports** : HTML, XML et terminal
- **Marqueurs** : Pour catégoriser les tests (unit, integration, slow, etc.)
- **Filtres d'avertissements** : Supprime les warnings non critiques
- **Variables d'environnement** : Optimisations mémoire

### Types de tests inclus

#### Tests unitaires (34 tests)
- **Initialisation** : Tests des paramètres par défaut et personnalisés
- **Chargement de modèle** : Tests de téléchargement et chargement
- **Détection PII** : Tests de détection simple et par batch
- **Masquage** : Tests de masquage des informations sensibles
- **Résumé** : Tests de génération de résumés
- **Gestion d'erreurs** : Tests des cas d'erreur et exceptions
- **Edge cases** : Tests avec textes vides, caractères spéciaux
- **Performance** : Tests de logging et optimisations mémoire

#### Fonctionnalités testées
- ✅ Détection d'emails par regex
- ✅ Traitement de textes longs (chunking)
- ✅ Filtrage par seuil de confiance
- ✅ Gestion mémoire (CPU/CUDA)
- ✅ Mapping des labels français
- ✅ Tests paramétrés (différents seuils, devices)

### Mocks et fixtures

Les tests utilisent des mocks pour :
- **Éviter le chargement réel du modèle** (plus rapide)
- **Simuler les réponses du pipeline** Hugging Face
- **Tester les cas d'erreur** sans dépendances externes
- **Contrôler les variables d'environnement**

### Rapports de couverture

Après exécution avec `--cov`, consultez :
- **Terminal** : Résumé de couverture par fichier
- **HTML** : Rapport détaillé dans `htmlcov/index.html`
- **XML** : Rapport pour intégration CI/CD dans `coverage.xml`

### Marqueurs disponibles

```bash
# Tests rapides uniquement
pytest -m "not slow"

# Tests unitaires seulement
pytest -m unit

# Tests d'intégration
pytest -m integration

# Tests nécessitant un GPU
pytest -m gpu
```

### Exemple de sortie

```bash
$ pytest tests/test_pii_detector.py -v
================================ test session starts ================================
platform win32 -- Python 3.13.4, pytest-8.4.1
collected 34 items

tests/test_pii_detector.py::TestPIIDetector::test_init_default_parameters PASSED
tests/test_pii_detector.py::TestPIIDetector::test_detect_pii_success PASSED
tests/test_pii_detector.py::TestPIIDetector::test_mask_pii_success PASSED
...

================================ 34 passed in 12.39s ================================
```

### Intégration continue

Les tests peuvent être intégrés dans un pipeline CI/CD :

```yaml
# Exemple GitHub Actions
- name: Run tests
  run: |
    pip install -r requirements.txt
    pytest --cov=pii-grpc-service --cov-report=xml
    
- name: Upload coverage
  uses: codecov/codecov-action@v3
  with:
    file: ./coverage.xml
```

### Développement et contribution

Pour ajouter de nouveaux tests :
1. Créez des méthodes commençant par `test_`
2. Utilisez les fixtures existantes (`detector`, `sample_entities`)
3. Ajoutez des marqueurs appropriés (`@pytest.mark.unit`)
4. Documentez le comportement testé dans la docstring

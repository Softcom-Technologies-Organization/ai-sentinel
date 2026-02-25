import os
import warnings
from typing import List, Dict, Tuple

from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer, AutoModelForTokenClassification, pipeline

# Supprimer les avertissements
warnings.filterwarnings("ignore")

class PIIDetector:
    """Détecteur d'informations personnelles utilisant le modèle Piiranha"""

    def __init__(self, model_id: str = "iiiorg/piiranha-v1-detect-personal-information"):
        self.model_id = model_id
        self.tokenizer = None
        self.model = None
        self.pipe = None
        self.label_mapping = {
            'ACCOUNTNUM': 'Numéro de compte',
            'BUILDINGNUM': 'Numéro de bâtiment',
            'CITY': 'Ville',
            'CREDITCARDNUMBER': 'Numéro de carte de crédit',
            'DATEOFBIRTH': 'Date de naissance',
            'DRIVERLICENSENUM': 'Numéro de permis de conduire',
            'EMAIL': 'Email',
            'GIVENNAME': 'Prénom',
            'IDCARDNUM': "Numéro de carte d'identité",
            'PASSWORD': 'Mot de passe',
            'SOCIALNUM': 'Numéro de sécurité sociale',
            'STREET': 'Rue',
            'SURNAME': 'Nom de famille',
            'TAXNUM': 'Numéro fiscal',
            'TELEPHONENUM': 'Numéro de téléphone',
            'USERNAME': "Nom d'utilisateur",
            'ZIPCODE': 'Code postal'
        }

    def download_model(self):
        """Télécharge les fichiers du modèle depuis Hugging Face"""
        filenames = [
            "config.json",
            "model.safetensors",
            "tokenizer.json",
            "tokenizer_config.json",
        ]

        print("[DOWNLOAD] Téléchargement du modèle...")
        for filename in filenames:
            hf_hub_download(
                repo_id=self.model_id,
                filename=filename,
            )
        print("[OK] Téléchargement terminé")

    def load_model(self):
        """Charge le modèle et crée le pipeline"""
        print("[LOADING] Chargement du modèle...")

        # Charger tokenizer et modèle
        self.tokenizer = AutoTokenizer.from_pretrained(self.model_id, legacy=False)
        self.model = AutoModelForTokenClassification.from_pretrained(self.model_id)

        # Créer le pipeline
        self.pipe = pipeline(
            "token-classification",
            model=self.model,
            tokenizer=self.tokenizer,
            aggregation_strategy="simple",
            device=-1  # CPU
        )

        print("[OK] Modèle chargé avec succès")

    def detect_pii(self, text: str, threshold: float = 0.5) -> List[Dict]:
        """
        Détecte les informations personnelles dans un texte

        Args:
            text: Le texte à analyser
            threshold: Seuil de confiance minimum (0-1)

        Returns:
            Liste des entités détectées avec leurs positions et scores
        """
        if not self.pipe:
            raise ValueError("Le modèle doit être chargé avant utilisation")

        # Détection
        results = self.pipe(text)

        # Filtrer par seuil et formater
        detected_entities = []
        for entity in results:
            if entity['score'] >= threshold:
                detected_entities.append({
                    'text': entity['word'].strip(),
                    'type': entity['entity_group'],
                    'type_fr': self.label_mapping.get(entity['entity_group'], entity['entity_group']),
                    'start': entity['start'],
                    'end': entity['end'],
                    'score': entity['score']
                })

        return detected_entities

    def mask_pii(self, text: str, threshold: float = 0.5) -> Tuple[str, List[Dict]]:
        """
        Masque les informations personnelles dans un texte

        Args:
            text: Le texte à anonymiser
            threshold: Seuil de confiance minimum

        Returns:
            Tuple (texte masqué, liste des entités détectées)
        """
        entities = self.detect_pii(text, threshold)

        # Trier par position décroissante pour remplacer sans décaler les indices
        entities_sorted = sorted(entities, key=lambda x: x['start'], reverse=True)

        masked_text = text
        for entity in entities_sorted:
            mask = f"[{entity['type']}]"
            masked_text = masked_text[:entity['start']] + mask + masked_text[entity['end']:]

        return masked_text, entities

    def get_summary(self, text: str, threshold: float = 0.5) -> Dict[str, int]:
        """
        Retourne un résumé des types de PII détectés

        Args:
            text: Le texte à analyser
            threshold: Seuil de confiance minimum

        Returns:
            Dictionnaire avec le compte de chaque type de PII
        """
        entities = self.detect_pii(text, threshold)
        summary = {}

        for entity in entities:
            pii_type = entity['type_fr']
            summary[pii_type] = summary.get(pii_type, 0) + 1

        return summary


# Exemple d'utilisation
if __name__ == "__main__":
    # Initialiser le détecteur
    detector = PIIDetector()

    # Télécharger et charger le modèle
    detector.download_model()
    detector.load_model()

    # Tests
    print("\n" + "="*60)
    print("DÉMONSTRATION DE DÉTECTION DE PII")
    print("="*60)

    test_cases = [
        {
            'lang': '🇬🇧 Anglais',
            'text': "Hello, my name is John Smith. You can reach me at john.smith@company.com or call 555-123-4567. I live at 123 Main Street, New York, NY 10001."
        },
        {
            'lang': '🇫🇷 Français',
            'text': "Bonjour, je suis Marie Dupont. Mon email est marie.dupont@entreprise.fr et j'habite au 15 rue de la Paix, 75001 Paris."
        },
        {
            'lang': '🔒 Données sensibles',
            'text': "My SSN is 123-45-6789 and my credit card number is 4111-1111-1111-1111"
        }
    ]

    for test in test_cases:
        print(f"\n{test['lang']}:")
        print(f"Texte original: {test['text']}")

        # Détecter les PII
        entities = detector.detect_pii(test['text'], threshold=0.5)

        if entities:
            print("\n📍 Entités détectées:")
            for entity in entities:
                print(f"  • '{entity['text']}' → {entity['type_fr']} (confiance: {entity['score']:.1%})")

        # Masquer les PII
        masked_text, _ = detector.mask_pii(test['text'], threshold=0.5)
        print(f"\n🔐 Texte anonymisé: {masked_text}")

        # Résumé
        summary = detector.get_summary(test['text'], threshold=0.5)
        if summary:
            print(f"\n📊 Résumé: {', '.join([f'{k}: {v}' for k, v in summary.items()])}")

    print("\n" + "="*60)

    # Mode interactif
    print("\n💡 Vous pouvez maintenant tester avec vos propres textes!")
    print("Entrez 'quit' pour quitter.\n")

    while True:
        user_text = input("Entrez un texte à analyser: ")
        if user_text.lower() == 'quit':
            break

        if user_text.strip():
            entities = detector.detect_pii(user_text)
            masked, _ = detector.mask_pii(user_text)

            print(f"\n🔍 Résultats:")
            if entities:
                for entity in entities:
                    print(f"  • '{entity['text']}' → {entity['type_fr']} ({entity['score']:.1%})")
                print(f"\n🔐 Anonymisé: {masked}")
            else:
                print("  Aucune information personnelle détectée.")
        print()

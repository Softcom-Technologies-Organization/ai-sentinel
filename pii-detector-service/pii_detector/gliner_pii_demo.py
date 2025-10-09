"""
Script de démonstration du modèle GLiNER PII large v1.0
Télécharge le modèle depuis HuggingFace et détecte les 17 types de PII gérés par piiranha-v1
"""

import warnings
from gliner import GLiNER

# Supprimer les avertissements
warnings.filterwarnings("ignore")


class GLiNERPIIDetector:
    """Détecteur d'informations personnelles utilisant GLiNER PII large v1.0"""

    # Les 17 types de PII détectés par piiranha-v1
    PIIRANHA_PII_TYPES = {
        'ACCOUNTNUM': 'account number',
        'BUILDINGNUM': 'building number',
        'CITY': 'city',
        'CREDITCARDNUMBER': 'credit card number',
        'DATEOFBIRTH': 'date of birth',
        'DRIVERLICENSENUM': 'driver license number',
        'EMAIL': 'email',
        'GIVENNAME': 'first name',
        'IDCARDNUM': 'ID card number',
        'PASSWORD': 'password',
        'SOCIALNUM': 'social security number',
        'STREET': 'street',
        'SURNAME': 'last name',
        'TAXNUM': 'tax number',
        'TELEPHONENUM': 'phone number',
        'USERNAME': 'username',
        'ZIPCODE': 'zip code'
    }

    def __init__(self, model_id: str = "knowledgator/gliner-pii-large-v1.0"):
        """
        Initialise le détecteur GLiNER PII
        
        Args:
            model_id: Identifiant du modèle HuggingFace
        """
        self.model_id = model_id
        self.model = None

    def download_and_load_model(self):
        """Télécharge et charge le modèle GLiNER depuis HuggingFace"""
        print(f"[DOWNLOAD] Téléchargement du modèle {self.model_id}...")
        print("(Le téléchargement peut prendre quelques minutes lors de la première exécution)")
        
        # GLiNER télécharge automatiquement le modèle lors du from_pretrained
        self.model = GLiNER.from_pretrained(self.model_id)
        
        print("[OK] Modèle téléchargé et chargé avec succès")

    def detect_pii(self, text: str, threshold: float = 0.3):
        """
        Détecte les informations personnelles dans un texte
        
        Args:
            text: Le texte à analyser
            threshold: Seuil de confiance minimum (0-1)
            
        Returns:
            Liste des entités détectées avec leurs types et scores
        """
        if not self.model:
            raise ValueError("Le modèle doit être chargé avant utilisation")

        # Convertir les types PII en labels pour GLiNER
        labels = list(self.PIIRANHA_PII_TYPES.values())

        # Détecter les entités
        entities = self.model.predict_entities(text, labels, threshold=threshold)

        return entities

    def display_results(self, text: str, entities: list):
        """
        Affiche les résultats de détection de manière formatée
        
        Args:
            text: Le texte original
            entities: Liste des entités détectées
        """
        print("\n" + "="*80)
        print("RÉSULTATS DE DÉTECTION PII")
        print("="*80)
        print(f"\nTexte analysé:\n{text}")
        
        if entities:
            print(f"\n📍 {len(entities)} entités PII détectées:\n")
            
            # Grouper par type
            by_type = {}
            for entity in entities:
                pii_type = entity['label']
                if pii_type not in by_type:
                    by_type[pii_type] = []
                by_type[pii_type].append(entity)
            
            # Afficher par type
            for pii_type, type_entities in sorted(by_type.items()):
                print(f"\n  {pii_type.upper()}:")
                for entity in type_entities:
                    print(f"    • '{entity['text']}' (confiance: {entity['score']:.1%})")
            
            # Résumé
            print(f"\n📊 Résumé:")
            for pii_type, type_entities in sorted(by_type.items()):
                print(f"  • {pii_type}: {len(type_entities)} occurrence(s)")
        else:
            print("\n⚠️ Aucune information personnelle détectée")
        
        print("\n" + "="*80)

    def get_sample_text_with_all_pii_types(self) -> str:
        """
        Retourne un texte d'exemple contenant une occurrence de chaque type de PII
        
        Returns:
            Texte contenant les 17 types de PII
        """
        sample_text = """
Customer Information Record - Confidential

Personal Details:
- Full Name: Jean Dupont (First name: Jean, Last name: Dupont)
- Date of Birth: 15/03/1985
- Username: jdupont2024
- Password: MySecureP@ss123!

Contact Information:
- Email: jean.dupont@email-example.com
- Phone Number: +33 6 12 34 56 78
- Address: 42 Rue de la République, Building Number 5B, City: Lyon, Zip Code: 69002

Financial Information:
- Account Number: FR7630006000011234567890189
- Credit Card Number: 4532-1234-5678-9010
- Tax Number: 1850312345678

Government IDs:
- Social Security Number: 1 85 03 75 116 234 56
- ID Card Number: 123456789ABC
- Driver License Number: 123456789012

Note: This is a sample document for testing PII detection systems.
All information is fictional and for demonstration purposes only.
        """
        return sample_text.strip()


def main():
    """Fonction principale de démonstration"""
    print("\n" + "="*80)
    print("DÉMONSTRATION GLiNER PII LARGE V1.0")
    print("Détection des 17 types de PII gérés par piiranha-v1")
    print("="*80 + "\n")

    # Initialiser le détecteur
    detector = GLiNERPIIDetector()

    # Télécharger et charger le modèle
    detector.download_and_load_model()

    # Obtenir le texte d'exemple avec tous les types de PII
    sample_text = detector.get_sample_text_with_all_pii_types()

    # Détecter les PII
    print("\n[ANALYSE] Analyse du texte en cours...")
    entities = detector.detect_pii(sample_text, threshold=0.3)

    # Afficher les résultats
    detector.display_results(sample_text, entities)

    # Afficher la liste des types de PII recherchés
    print("\n📋 Types de PII recherchés (17 types de piiranha-v1):")
    print("-" * 80)
    for piiranha_type, gliner_label in detector.PIIRANHA_PII_TYPES.items():
        print(f"  {piiranha_type:<20} → {gliner_label}")

    # Statistiques de couverture
    detected_types = set([entity['label'] for entity in entities])
    expected_types = set(detector.PIIRANHA_PII_TYPES.values())
    coverage = len(detected_types) / len(expected_types) * 100

    print(f"\n✅ Couverture: {len(detected_types)}/{len(expected_types)} types détectés ({coverage:.1f}%)")

    if detected_types != expected_types:
        missing = expected_types - detected_types
        print(f"\n⚠️ Types non détectés dans cet exemple:")
        for missing_type in sorted(missing):
            print(f"  • {missing_type}")


if __name__ == "__main__":
    main()

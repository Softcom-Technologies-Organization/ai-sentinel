#!/usr/bin/env python
"""
Script to directly test the Piiranha model with our test text.

This script runs a direct test of the Piiranha model without using the gRPC service,
to see if the model itself can detect PII in our test text.
"""

import os
import sys
from pathlib import Path

# Try to import the PIIDetector class
try:
    # First try to import from the local directory
    sys.path.insert(0, str(Path(__file__).parent.absolute()))
    from test_piiranha import PIIDetector
    print("Imported PIIDetector from test_piiranha.py")
except ImportError:
    try:
        # Then try to import from the pii-grpc-service directory
        sys.path.insert(0, str(Path(__file__).parent.parent.parent / "pii_detector"))
        from service.detector.pii_detector import PIIDetector
        print("Imported PIIDetector from service.detector.pii_detector")
    except ImportError:
        print("Error: Could not import PIIDetector class.")
        print("Please make sure either test_piiranha.py or pii-grpc-service/service/detector/pii_detector.py exists.")
        sys.exit(1)

def main():
    """Run a direct test of the Piiranha model."""
    # Test text containing various patterns of PII
    test_text = """Bonjour, Je m'appelle Jean Dupont et je travaille pour l'État de Vaud. Voici mes coordonnées: Emails: jean.dupont@gmail.com (email standard) jean_dupont123@outlook.com (email avec underscore et chiffres) jean.dupont@vd.ch (email @vd.ch exclu par la regex) jean.dupont@subdomain.example.co.uk (email avec sous-domaine et TLD plus long) jean..dupont@example.com (email avec double point) jean@localhost (email sans TLD - techniquement valide mais souvent non détecté) Numéros de téléphone suisses: 021 123 45 67 (format standard) +41 21 123 45 67 (format avec préfixe international) 0041 21 123 45 67 (format avec préfixe international) 0211234567 (format sans espaces) +41211234567 (format international sans espaces) 021-123-45-67 (format avec tirets) +41 (0)21 123 45 67 (format avec zéro entre parenthèses) Numéros AVS: 756.1234.5678.90 (format standard détecté par regex) 756.1234.5678.9 (format incorrect - un chiffre en moins) 7561234567890 (format sans points) 756-1234-5678-90 (format avec tirets) 756 1234 5678 90 (format avec espaces) Autres informations personnelles (pour tester Piiranha): Adresse: Rue du Lac 15, 1000 Lausanne Date de naissance: 15.06.1985 Numéro de carte de crédit: 4111 1111 1111 1111 Numéro de passeport: X1234567 Mot de passe: MonMotDePasse123!"""
    
    print("Running direct Piiranha model test...")
    print(f"Text length: {len(test_text)} characters")
    
    # Check if HUGGING_FACE_API_KEY is set
    if not os.environ.get("HUGGING_FACE_API_KEY"):
        print("WARNING: HUGGING_FACE_API_KEY environment variable is not set.")
        print("The model may not be able to download without an API key.")
    
    try:
        # Initialize the detector
        detector = PIIDetector()
        
        # Download and load the model
        print("\nDownloading and loading the model...")
        detector.download_model()
        detector.load_model()
        
        # Set threshold
        threshold = 0.5
        print(f"\nUsing threshold: {threshold}")
        
        # Detect PII
        print("\nDetecting PII entities...")
        entities = detector.detect_pii(test_text, threshold)
        
        # Display results
        print("\n" + "="*60)
        print("PIIRANHA DETECTION RESULTS")
        print("="*60)
        
        if entities:
            print(f"\n📍 Detected {len(entities)} PII entities:")
            for entity in entities:
                print(f"  • '{entity['text']}' → {entity.get('type_fr', entity.get('type_label', entity['type']))} (confidence: {entity['score']:.1%})")
        else:
            print("\n📍 No PII entities detected.")
        
        # Get masked content
        print("\nMasking PII entities...")
        masked_content, _ = detector.mask_pii(test_text, threshold)
        print(f"\n🔐 Masked content: {masked_content[:100]}... (truncated)")
        
        # Get summary
        summary = detector.get_summary(test_text, threshold)
        if summary:
            summary_str = ", ".join([f"{k}: {v}" for k, v in summary.items()])
            print(f"\n📊 Summary: {summary_str}")
        else:
            print("\n📊 No summary available.")
        
        print("\n" + "="*60)
        
    except Exception as e:
        print(f"\nError running Piiranha model: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()

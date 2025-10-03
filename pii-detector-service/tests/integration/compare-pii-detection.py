#!/usr/bin/env python
"""
Script to compare PII detection using regex patterns and the Piiranha model.

This script runs both regex-based detection and Piiranha model-based detection
on the same text and compares the results to evaluate the effectiveness of each approach.
"""

import os
import sys
import subprocess
import re
from pathlib import Path
import time
import importlib.util

def run_regex_detection(test_text):
    """Run PII detection using regex patterns and return the results."""
    print("\n" + "="*80)
    print("RUNNING REGEX-BASED PII DETECTION")
    print("="*80)

    # Define regex patterns from the issue description
    regex_patterns = [
        {
            "label": "Email",
            "regex": r"\b[A-Za-z0-9._%+-]+@\b(?!vd\b)[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"
        },
        {
            "label": "Telephone",
            "regex": r"(^0|^(\+41)|^0041)\s?\d{2}\s?\d{3}\s?\d{2}\s?\d{2}$"
        },
        {
            "label": "AVS",
            "regex": r"756\.\d{4}\.\d{4}\.\d{2}"
        }
    ]

    # Define improved regex patterns
    improved_regex_patterns = [
        {
            "label": "Email (Improved)",
            "regex": r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"
        },
        {
            "label": "Telephone (Improved)",
            "regex": r"\b(0\d{2}|(\+41|0041)\s*\d{2})[\s\.-]?\d{3}[\s\.-]?\d{2}[\s\.-]?\d{2}\b"
        },
        {
            "label": "AVS (Improved)",
            "regex": r"756[\.\s\-]?\d{4}[\.\s\-]?\d{4}[\.\s\-]?\d{2}"
        }
    ]

    # Process the text with original regex patterns
    print("\nOriginal Regex Patterns:")
    original_results = {}

    for pattern in regex_patterns:
        print(f"Searching for {pattern['label']}...")

        # For telephone numbers, we need to check each word/token separately
        if pattern['label'] == "Telephone":
            matches = []
            # Split text into words and check each one
            for word in re.split(r'[\s,;.!?()]+', test_text):
                if re.match(pattern['regex'], word):
                    matches.append(word)
        else:
            # For other patterns, find all matches in the text
            matches = re.findall(pattern['regex'], test_text)

        # Store results
        original_results[pattern['label']] = matches
        print(f"  Found {len(matches)} {pattern['label']} entities")

    # Process the text with improved regex patterns
    print("\nImproved Regex Patterns:")
    improved_results = {}

    for pattern in improved_regex_patterns:
        print(f"Searching for {pattern['label']}...")

        # Use finditer to get match objects
        matches = []
        for match in re.finditer(pattern['regex'], test_text):
            # Get the full match text
            matches.append(match.group(0))

        # Store results
        improved_results[pattern['label']] = matches
        print(f"  Found {len(matches)} {pattern['label']} entities")

    return {
        "original": original_results,
        "improved": improved_results
    }

def run_piiranha_detection(test_text):
    """Run PII detection using the Piiranha model directly and return the results."""
    print("\n" + "="*80)
    print("RUNNING PIIRANHA-BASED PII DETECTION")
    print("="*80)

    # Default parameters
    threshold = 0.5  # Lower threshold to catch more entities
    print(f"Threshold: {threshold}")

    try:
        # Try to import the PIIDetector class
        PIIDetector = None
        
        # First try to import from the local directory (test-piiranha.py with hyphen)
        test_piiranha_path = Path(__file__).parent / "test-piiranha.py"
        if test_piiranha_path.exists():
            spec = importlib.util.spec_from_file_location("test_piiranha", test_piiranha_path)
            test_piiranha_module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(test_piiranha_module)
            PIIDetector = test_piiranha_module.PIIDetector
            print("Imported PIIDetector from test-piiranha.py")
        else:
            # Then try to import from the pii_detector directory
            try:
                sys.path.insert(0, str(Path(__file__).parent.parent / "pii_detector"))
                from service.detector.pii_detector import PIIDetector
                print("Imported PIIDetector from service.detector.pii_detector")
            except ImportError:
                print("Error: Could not import PIIDetector class.")
                return {"entities": [], "summary": {}}

        # Initialize the detector
        detector = PIIDetector()

        # Download and load the model
        print("\nDownloading and loading the model...")
        detector.download_model()
        detector.load_model()

        print("\nDetecting PII entities...")

        # Detect PII
        entities = detector.detect_pii(test_text, threshold)
        print(f"Piiranha detected {len(entities)} entities")

        # Get summary
        summary = detector.get_summary(test_text, threshold)
        print(f"Summary: {summary}")

        # Format the results to match the expected structure
        formatted_entities = []
        for entity in entities:
            formatted_entities.append({
                "text": entity["text"],
                "type": entity.get("type_fr", entity["type"])
            })

        return {
            "entities": formatted_entities,
            "summary": summary
        }

    except Exception as e:
        print(f"\nError running Piiranha detection: {e}")
        import traceback
        traceback.print_exc()

        return {"entities": [], "summary": {}}

def parse_piiranha_output(output):
    """Parse the output from the Piiranha detection to extract entities and summary."""
    entities = []
    summary = {}

    # Extract entities
    entity_section = False
    for line in output.split('\n'):
        if '📍 Detected' in line and 'PII entities:' in line:
            entity_section = True
            continue
        elif entity_section and line.strip().startswith('•'):
            # Extract entity text and type
            parts = line.split('→')
            if len(parts) >= 2:
                text = parts[0].strip().strip("'• ")
                type_info = parts[1].split('(')[0].strip()
                entities.append({"text": text, "type": type_info})
        elif entity_section and (line.strip().startswith('🔐') or line.strip() == ''):
            entity_section = False

    # Extract summary
    summary_line = None
    for line in output.split('\n'):
        if '📊 Summary:' in line:
            summary_line = line.replace('📊 Summary:', '').strip()
            break

    if summary_line:
        for item in summary_line.split(','):
            if ':' in item:
                key, value = item.split(':')
                summary[key.strip()] = int(value.strip())

    return {
        "entities": entities,
        "summary": summary
    }

def compare_results(regex_results, piiranha_results):
    """Compare the results from regex and Piiranha detection."""
    print("\n" + "="*80)
    print("COMPARISON OF DETECTION METHODS")
    print("="*80)

    # Compare entity counts
    print("\nEntity Count Comparison:")
    print("-" * 60)
    print(f"{'PII Type':<25} {'Original Regex':<15} {'Improved Regex':<15} {'Piiranha':<15}")
    print("-" * 60)

    # Collect all PII types from both methods
    all_types = set()
    for label in regex_results["original"].keys():
        all_types.add(label)
    for label in regex_results["improved"].keys():
        all_types.add(label.replace(" (Improved)", ""))
    for entity_type in piiranha_results["summary"].keys():
        all_types.add(entity_type)

    # Map Piiranha types to regex types for comparison
    type_mapping = {
        "Email": ["Email"],
        "Telephone": ["Numéro de téléphone"],
        "AVS": ["Numéro de sécurité sociale"]
    }

    # Print comparison table
    for pii_type in sorted(all_types):
        # Skip improved types as they're handled with their base type
        if "(Improved)" in pii_type:
            continue

        # Get counts for each method
        original_count = len(regex_results["original"].get(pii_type, []))

        # For improved regex, use the corresponding improved type
        improved_type = f"{pii_type} (Improved)"
        improved_count = len(regex_results["improved"].get(improved_type, []))

        # For Piiranha, sum counts of mapped types
        piiranha_count = 0
        if pii_type in type_mapping:
            for mapped_type in type_mapping[pii_type]:
                piiranha_count += piiranha_results["summary"].get(mapped_type, 0)
        else:
            piiranha_count = piiranha_results["summary"].get(pii_type, 0)

        print(f"{pii_type:<25} {original_count:<15} {improved_count:<15} {piiranha_count:<15}")

    # Print additional types detected by Piiranha but not by regex
    print("\nAdditional PII Types Detected by Piiranha:")
    for pii_type, count in piiranha_results["summary"].items():
        is_mapped = False
        for regex_type, mapped_types in type_mapping.items():
            if pii_type in mapped_types:
                is_mapped = True
                break

        if not is_mapped and pii_type not in all_types:
            print(f"  • {pii_type}: {count}")

    # Effectiveness analysis
    print("\nEffectiveness Analysis:")
    print("-" * 60)

    # Compare regex vs Piiranha for common PII types
    for regex_type, piiranha_types in type_mapping.items():
        original_entities = set(regex_results["original"].get(regex_type, []))
        improved_entities = set(regex_results["improved"].get(f"{regex_type} (Improved)", []))

        piiranha_entities = set()
        for entity in piiranha_results["entities"]:
            if entity["type"] in piiranha_types:
                piiranha_entities.add(entity["text"])

        # Calculate overlap
        original_piiranha_overlap = len(original_entities.intersection(piiranha_entities))
        improved_piiranha_overlap = len(improved_entities.intersection(piiranha_entities))

        # Calculate unique detections
        original_unique = len(original_entities - piiranha_entities)
        improved_unique = len(improved_entities - piiranha_entities)
        piiranha_unique = len(piiranha_entities - improved_entities)

        print(f"\n{regex_type} Detection:")
        print(f"  Original Regex: {len(original_entities)} entities")
        print(f"  Improved Regex: {len(improved_entities)} entities")
        print(f"  Piiranha: {len(piiranha_entities)} entities")
        print(f"  Overlap (Original Regex & Piiranha): {original_piiranha_overlap} entities")
        print(f"  Overlap (Improved Regex & Piiranha): {improved_piiranha_overlap} entities")
        print(f"  Unique to Original Regex: {original_unique} entities")
        print(f"  Unique to Improved Regex: {improved_unique} entities")
        print(f"  Unique to Piiranha: {piiranha_unique} entities")

    # Overall recommendation
    print("\nOverall Recommendation:")
    print("-" * 60)
    print("Based on the comparison:")
    print("1. Improved regex patterns significantly outperform the original patterns")
    print("2. Piiranha detects additional PII types not covered by regex patterns")
    print("3. For the common PII types (Email, Telephone, AVS):")

    # Determine which method is better for each type
    for regex_type, piiranha_types in type_mapping.items():
        improved_entities = set(regex_results["improved"].get(f"{regex_type} (Improved)", []))

        piiranha_entities = set()
        for entity in piiranha_results["entities"]:
            if entity["type"] in piiranha_types:
                piiranha_entities.add(entity["text"])

        improved_unique = len(improved_entities - piiranha_entities)
        piiranha_unique = len(piiranha_entities - improved_entities)

        if improved_unique > piiranha_unique:
            print(f"   - For {regex_type}: Improved regex patterns perform better")
        elif piiranha_unique > improved_unique:
            print(f"   - For {regex_type}: Piiranha performs better")
        else:
            print(f"   - For {regex_type}: Both methods perform similarly")

    # Final recommendation
    print("\nFinal Recommendation:")
    if len(piiranha_results["summary"]) > len(type_mapping):
        print("Piiranha is recommended as the primary PII detection method because it detects more types of PII.")
        print("Consider using improved regex patterns as a fallback or for specific PII types where they perform better.")
    else:
        print("Improved regex patterns are sufficient for the specific PII types being targeted.")
        print("However, if broader PII detection is needed, Piiranha would be the better choice.")

def main():
    """Run the comparison of PII detection methods."""
    # Test text containing various patterns of PII
    test_text = """Bonjour, Je m'appelle Jean Dupont et je travaille pour l'État de Vaud. Voici mes coordonnées: Emails: jean.dupont@gmail.com (email standard détecté par regex) jean_dupont123@outlook.com (email avec underscore et chiffres) jean.dupont@vd.ch (email @vd.ch exclu par la regex) jean.dupont@subdomain.example.co.uk (email avec sous-domaine et TLD plus long) jean..dupont@example.com (email avec double point - invalide mais pourrait tromper la regex) jean@localhost (email sans TLD - techniquement valide mais souvent non détecté) Numéros de téléphone suisses: 021 123 45 67 (format standard détecté par regex) +41 21 123 45 67 (format avec préfixe international +41) 0041 21 123 45 67 (format avec préfixe international 0041) 0211234567 (format sans espaces) +41211234567 (format international sans espaces) 021-123-45-67 (format avec tirets) +41 (0)21 123 45 67 (format avec zéro entre parenthèses - non détecté par regex) Numéros AVS: 756.1234.5678.90 (format standard détecté par regex) 756.1234.5678.9 (format incorrect - un chiffre en moins) 7561234567890 (format sans points) 756-1234-5678-90 (format avec tirets) 756 1234 5678 90 (format avec espaces) Autres informations personnelles (pour tester Piiranha): Adresse: Rue du Lac 15, 1000 Lausanne Date de naissance: 15.06.1985 Numéro de carte de crédit: 4111 1111 1111 1111 Numéro de passeport: X1234567 Mot de passe: MonMotDePasse123!"""

    print("Running PII detection comparison...")
    print(f"Text length: {len(test_text)} characters")

    # Run Piiranha detection directly
    print("\nRunning Piiranha detection directly...")
    piiranha_results = run_piiranha_detection(test_text)

    # Run regex detection
    regex_results = run_regex_detection(test_text)

    # Compare results
    compare_results(regex_results, piiranha_results)

if __name__ == "__main__":
    main()

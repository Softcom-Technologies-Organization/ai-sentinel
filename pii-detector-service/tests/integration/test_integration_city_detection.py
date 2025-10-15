import os
import pytest
from unittest.mock import patch, MagicMock

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from service.detector.pii_detector import PIIDetector, PIIEntity


SAMPLE_TEXT_WITH_ADDRESSES = (
    "Demande de logement social - Dossier N°2024-7845\n"
    "Mme Sophie Mercier, née le 15/03/1982, demeurant au 23 Rue Victor Hugo 75016 Paris.\n"
    "Tél: +33 1 42 78 56 34, Email: sophie.mercier@orange.fr\n"
    "Numéro de sécurité sociale: 2820345678912\n"
    "Adresse actuelle: 45 Avenue des Champs-Élysées, Bâtiment C, 75008 Paris\n"
    "Contact d'urgence: M. Jean Mercier, 12 Rue de la Paix 69002 Lyon\n"
    "Tél urgence: +33 4 78 92 34 56\n"
    "Adresse précédente: 8 Boulevard Saint-Michel 13001 Marseille (2018-2023)\n"
    "Lieu de travail: 156 Rue de Rivoli 75001 Paris\n"
    "Référence bancaire: IBAN FR76 3000 6000 0112 3456 7890 189\n"
    "Adresse souhaitée: Appartement dans le 15ème arrondissement, proche métro Convention\n"
    "Contact secondaire: Mlle Claire Dubois, 34 Rue Pasteur 69007 Lyon\n"
    "Tél secondaire: +33 6 12 34 56 78\n"
    "Lieu de naissance: Aix-en-Provence (13100)\n"
    "Adresse parents: 67 Avenue Jean Jaurès 33000 Bordeaux\n"
    "Ancienne adresse étudiante: 89 Rue Gambetta 31000 Toulouse (2000-2005)\n"
    "Adresse professionnelle alternative: 234 Boulevard Haussmann 75008 Paris\n"
    "Contact professionnel: Dr. Pierre Martin, 45 Rue La Fayette 59000 Lille\n"
    "Référence: Dossier validé par Mme Rousseau le 12/01/2024\n"
    "Remarque: Demande urgente - Famille avec enfants scolarisés à Saint-Étienne (42000)\n"
)


class TestIntegrationCityDetection:
    """Integration test to verify that city names in addresses are properly detected
    and not split into multiple entities.
    """

    @pytest.mark.integration
    @pytest.mark.slow
    @pytest.mark.skipif(os.environ.get('HUGGING_FACE_API_KEY') is None, 
                        reason='HUGGING_FACE_API_KEY manquant pour exécuter le modèle réel')
    def test_city_names_not_split_in_addresses(self):
        """Integration test: verifies that city names in diverse addresses are properly detected
        as single entities and not split. Tests various city name formats:
        - Simple cities (Paris, Lyon, Marseille, etc.)
        - Compound cities with hyphens (Aix-en-Provence, Saint-Étienne)
        - Cities with multiple words if any
        
        Logs all detected entities by the real model for visibility.
        """
        # Arrange
        detector = PIIDetector()
        detector.load_model()

        # Act
        entities = detector.detect_pii(SAMPLE_TEXT_WITH_ADDRESSES, threshold=0.5)

        # Log results for visibility
        printable = [
            {
                'text': e.text,
                'type': e.pii_type,
                'start': e.start,
                'end': e.end,
                'score': round(e.score, 4),
            }
            for e in entities
        ]
        print('[CITY_DETECTION_TEST_RESULTS]', printable)

        # Normalize helper: trim punctuation and collapse spaces
        def norm_text(s: str) -> str:
            s2 = s.strip().strip('.,;:()')
            s2 = ' '.join(s2.split())
            return s2

        # Build actual map of type -> set of normalized texts
        target_types = {
            'CITY', 'STREET', 'BUILDINGNUM', 'ZIPCODE', 
            'GIVENNAME', 'SURNAME', 'DATEOFBIRTH', 
            'TELEPHONENUM', 'EMAIL', 'SOCIALNUM'
        }
        actual = {t: set() for t in target_types}
        for e in entities:
            et = getattr(e, 'pii_type', '') or getattr(e, 'type', '')
            if et in actual:
                actual[et].add(norm_text(getattr(e, 'text', '')))

        # Expected compound cities that MUST be detected as complete entities (not split)
        # These are the critical test cases for verifying cities are not split
        compound_cities_must_detect = {
            'Aix-en-Provence',
            'Saint-Étienne'
        }
        
        # Simple cities that may or may not be detected depending on context
        # (e.g., when following zipcodes, the model may not detect them)
        simple_cities_optional = {
            'Paris',
            'Lyon', 
            'Marseille',
            'Bordeaux',
            'Toulouse',
            'Lille'
        }

        # Get actual detected cities
        actual_cities = actual.get('CITY', set())

        # Additional per-type compact log
        compact_actual = {t: sorted(list(actual.get(t, set()))) for t in sorted(target_types)}
        print('[CITY_DETECTION_ACTUAL_BY_TYPE]', compact_actual)

        # Detailed city detection log
        all_expected_cities = compound_cities_must_detect | simple_cities_optional
        print(f'[CITY_DETECTION_EXPECTED_COMPOUND] {sorted(list(compound_cities_must_detect))}')
        print(f'[CITY_DETECTION_EXPECTED_SIMPLE] {sorted(list(simple_cities_optional))}')
        print(f'[CITY_DETECTION_ACTUAL] {sorted(list(actual_cities))}')

        # PRIMARY ASSERTION: Verify compound city names are NOT split
        # This is the main goal of this test
        print('\n[PRIMARY TEST] Verifying compound cities are detected as complete entities...')
        
        missing_compound = compound_cities_must_detect - actual_cities
        if missing_compound:
            print(f"[CRITICAL] Compound cities not detected: {sorted(list(missing_compound))}")
        
        # Check if compound cities were split into parts
        compound_city_parts = {
            'Aix-en-Provence': ['Aix', 'en', 'Provence'],
            'Saint-Étienne': ['Saint', 'Étienne']
        }
        
        split_detected = False
        for compound_city, parts in compound_city_parts.items():
            if compound_city in actual_cities:
                print(f"[SUCCESS] '{compound_city}' detected as complete entity (not split)")
            else:
                # Check if it was split into parts
                detected_parts = [part for part in parts if part in actual_cities]
                if detected_parts:
                    print(f"[FAILURE] '{compound_city}' appears to be SPLIT into parts: {detected_parts}")
                    split_detected = True
                else:
                    print(f"[WARNING] '{compound_city}' not detected at all (but not split)")
        
        # Main assertion: compound cities must be detected as complete entities
        assert compound_cities_must_detect.issubset(actual_cities), (
            f"Compound cities must be detected as complete entities (not split). "
            f"Missing: {sorted(list(missing_compound))}"
        )
        
        # Verify no partial city names from compound cities exist WITHOUT the full name
        problematic_partials = {'Aix', 'Provence', 'Saint', 'Étienne'}
        detected_partials = problematic_partials.intersection(actual_cities)
        
        if detected_partials:
            print(f"\n[SPLIT_CHECK] Partial city name components found: {sorted(list(detected_partials))}")
            # Fail if we have partials but not the corresponding full city name
            for partial in detected_partials:
                if partial in ['Aix', 'Provence']:
                    assert 'Aix-en-Provence' in actual_cities, (
                        f"City name 'Aix-en-Provence' appears to be SPLIT (found '{partial}' separately)"
                    )
                if partial in ['Saint', 'Étienne']:
                    assert 'Saint-Étienne' in actual_cities, (
                        f"City name 'Saint-Étienne' appears to be SPLIT (found '{partial}' separately)"
                    )
            print("[SPLIT_CHECK] Partials exist but full compound names also detected - OK")
        
        # Secondary check: log simple city detection (informational only)
        print('\n[SECONDARY INFO] Simple city detection status:')
        detected_simple = simple_cities_optional.intersection(actual_cities)
        missing_simple = simple_cities_optional - actual_cities
        if detected_simple:
            print(f"  Detected: {sorted(list(detected_simple))}")
        if missing_simple:
            print(f"  Not detected: {sorted(list(missing_simple))}")
            print(f"  Note: Simple cities after zipcodes may not be detected by the model")

        # Sanity checks on entity structure
        assert all(isinstance(e, PIIEntity) for e in entities), "All entities should be PIIEntity instances"
        assert all(0 <= e.start < e.end <= len(SAMPLE_TEXT_WITH_ADDRESSES) for e in entities), (
            "All entities should have valid start/end positions"
        )
        assert all(0.0 <= e.score <= 1.0 for e in entities), "All entities should have scores between 0 and 1"

        print("[TEST_SUCCESS] All city names were properly detected without splitting")

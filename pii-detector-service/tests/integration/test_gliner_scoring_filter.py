import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from pii_detector.service.detector.gliner_detector import GLiNERDetector


class TestGlinerScoringFilter:
    """Test pour vérifier que le post-filtre des scoring thresholds fonctionne correctement."""

    @pytest.mark.integration
    @pytest.mark.slow
    def test_phone_number_high_threshold_filters_false_positives(self):
        """
        Vérifie que le threshold élevé (0.95) pour TELEPHONENUM filtre les faux positifs
        comme les dates, montants, et codes qui étaient détectés comme des téléphones.
        """
        # Arrange - Charger le fichier text-input2.txt qui contient beaucoup de nombres
        tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        input_file_path = os.path.join(tests_dir, 'resources', 'text-input2.txt')
        
        with open(input_file_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Initialiser GLiNER detector (utilisera la config avec scoring thresholds)
        print('\nInitialisation de GLiNER avec post-filtre...')
        detector = GLiNERDetector()
        detector.load_model()

        # Act - Détecter les PII
        print('Détection des PII avec GLiNER...')
        entities = detector.detect_pii(content, threshold=0.3)  # Threshold de base bas

        # Filter uniquement les téléphones pour analyse
        phone_entities = [e for e in entities if e.pii_type == 'TELEPHONENUM']
        
        # Log des résultats
        print('\n' + '=' * 80)
        print(f'[TEST POST-FILTRE GLiNER] Fichier: {input_file_path}')
        print(f'[TEST POST-FILTRE GLiNER] Nombre total de PII détectés: {len(entities)}')
        print(f'[TEST POST-FILTRE GLiNER] Nombre de téléphones détectés: {len(phone_entities)}')
        print('=' * 80)
        
        if phone_entities:
            print('\nTéléphones détectés (devraient être des vrais numéros):')
            for entity in phone_entities:
                print(f'  • Texte: "{entity.text}" | Score: {entity.score:.4f} | '
                      f'Position: [{entity.start}-{entity.end}]')
        else:
            print('\nAucun téléphone détecté (bon si le document n\'en contient pas)')
        
        # Résumé par type de PII
        print('\n' + '=' * 80)
        print('[RÉSUMÉ PAR TYPE DE PII]')
        print('=' * 80)
        
        pii_by_type = {}
        for entity in entities:
            pii_type = entity.pii_type
            if pii_type not in pii_by_type:
                pii_by_type[pii_type] = []
            pii_by_type[pii_type].append(entity.text)
        
        for pii_type in sorted(pii_by_type.keys()):
            texts = pii_by_type[pii_type]
            print(f'\n{pii_type} ({len(texts)} occurrence(s)):')
            for text in texts[:5]:  # Afficher max 5 exemples
                print(f'  - "{text}"')
            if len(texts) > 5:
                print(f'  ... et {len(texts) - 5} autres')
        
        # Assert - Vérifications
        print('\n' + '=' * 80)
        print('[VÉRIFICATIONS]')
        print('=' * 80)
        
        # Avec le threshold de 0.95 pour TELEPHONENUM, on ne devrait plus avoir
        # les faux positifs comme "2010", "8 1700", "692.20", "04.08.2010", etc.
        # qui avaient un score de 0.85
        
        # Vérifier qu'aucun téléphone n'a un score < 0.95
        for entity in phone_entities:
            assert entity.score >= 0.95, \
                f"ÉCHEC: Téléphone '{entity.text}' détecté avec score {entity.score:.3f} < 0.95"
        
        print(f'✅ Tous les téléphones détectés ont un score >= 0.95')
        
        # Vérifier que les dates/montants courants ne sont PAS détectés comme téléphones
        false_positives = [
            "2010", "8 1700", "692.20", "04.08.2010", "07.2010", 
            "22154", "301121", "RZ-1737"
        ]
        
        phone_texts = {e.text for e in phone_entities}
        detected_false_positives = [fp for fp in false_positives if fp in phone_texts]
        
        if detected_false_positives:
            print(f'❌ Faux positifs encore détectés: {detected_false_positives}')
            pytest.fail(f"Des faux positifs sont encore détectés comme téléphones: {detected_false_positives}")
        else:
            print(f'✅ Aucun des faux positifs connus n\'est détecté comme téléphone')
        
        # Au final, on devrait avoir beaucoup moins de téléphones détectés
        # (probablement 0-3 vrais numéros au lieu de 67+)
        print(f'\n✅ Nombre final de téléphones: {len(phone_entities)} (vs 67+ avant le filtre)')
        
        print('\n' + '=' * 80)
        print('[TEST TERMINÉ] Post-filtre GLiNER fonctionne correctement')
        print('=' * 80 + '\n')

    @pytest.mark.integration
    def test_scoring_overrides_loaded(self):
        """Vérifie que les scoring overrides sont bien chargés depuis la configuration."""
        # Arrange & Act
        detector = GLiNERDetector()
        
        # Assert
        assert detector.scoring_overrides is not None, \
            "scoring_overrides devrait être initialisé"
        
        assert 'TELEPHONENUM' in detector.scoring_overrides, \
            "TELEPHONENUM devrait être dans scoring_overrides"
        
        assert detector.scoring_overrides['TELEPHONENUM'] == 0.95, \
            "TELEPHONENUM threshold devrait être 0.95"
        
        print(f'\n✅ Scoring overrides chargés: {len(detector.scoring_overrides)} types configurés')
        print(f'✅ TELEPHONENUM threshold: {detector.scoring_overrides["TELEPHONENUM"]}')

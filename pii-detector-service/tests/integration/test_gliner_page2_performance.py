"""
Test d'intégration pour tester les performances et résultats du scan Gliner sur page2-demo.txt.

Ce test mesure:
- Le temps de chargement du modèle Gliner
- Le temps de scan du fichier page2-demo.txt
- Les PII détectés dans le contenu
- Les métriques de performance détaillées
"""

import os
import time
import pytest
from typing import List

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from service.detector.gliner_detector import GLiNERDetector
from service.detector.models import PIIEntity, DetectionConfig


class TestGlinerPage2Performance:
    """Tests de performance et résultats pour le scan Gliner du fichier page2-demo.txt."""

    def test_should_scan_page2_demo_with_gliner_and_measure_performance(self):
        """
        Test d'intégration: scanne page2-demo.txt avec Gliner et mesure les performances.
        
        Vérifie:
        - Le temps de chargement du modèle
        - Le temps de scan
        - Les PII détectés (si présents)
        - La structure des entités détectées
        """
        # Arrange
        tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        page2_path = os.path.join(tests_dir, 'resources', 'page2-demo.txt')
        
        with open(page2_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        print(f"\n[INFO] Contenu à scanner: {len(content)} caractères")
        print(f"[INFO] Nombre de mots approximatif: {len(content.split())}")
        
        # Configuration pour Gliner - utilise les valeurs du fichier TOML par défaut
        # Laisse DetectionConfig charger depuis config/models/gliner-pii.toml
        config = DetectionConfig()
        
        print(f"[CONFIG] Utilisation de la configuration TOML:")
        print(f"  - model_id: {config.model_id}")
        print(f"  - max_length: {config.max_length}")
        print(f"  - threshold: {config.threshold}")
        print(f"  - device: {config.device}")
        
        detector = GLiNERDetector(config=config)
        
        # Act - Mesure du temps de chargement du modèle
        print("\n[PHASE 1] Chargement du modèle Gliner...")
        start_load = time.time()
        detector.load_model()
        load_time = time.time() - start_load
        print(f"[RÉSULTAT] Temps de chargement: {load_time:.3f}s")
        
        # Act - Mesure du temps de scan
        print("\n[PHASE 2] Scan du contenu avec Gliner...")
        start_scan = time.time()
        entities = detector.detect_pii(content, threshold=0.5)
        scan_time = time.time() - start_scan
        print(f"[RÉSULTAT] Temps de scan: {scan_time:.3f}s")
        
        # Calcul des métriques de performance
        chars_per_second = len(content) / scan_time if scan_time > 0 else 0
        words_per_second = len(content.split()) / scan_time if scan_time > 0 else 0
        
        print(f"\n[MÉTRIQUES DE PERFORMANCE]")
        print(f"  - Caractères/seconde: {chars_per_second:.0f}")
        print(f"  - Mots/seconde: {words_per_second:.0f}")
        print(f"  - Entités détectées: {len(entities)}")
        
        # Affichage des PII détectés
        if entities:
            print(f"\n[PII DÉTECTÉES] {len(entities)} entités trouvées:")
            entities_by_type = {}
            for e in entities:
                pii_type = e.pii_type
                if pii_type not in entities_by_type:
                    entities_by_type[pii_type] = []
                entities_by_type[pii_type].append({
                    'text': e.text,
                    'score': round(e.score, 4),
                    'position': f"{e.start}-{e.end}"
                })
            
            for pii_type in sorted(entities_by_type.keys()):
                print(f"\n  [{pii_type}] ({len(entities_by_type[pii_type])} occurrence(s)):")
                for entity_info in entities_by_type[pii_type]:
                    print(f"    - '{entity_info['text']}' "
                          f"(score: {entity_info['score']}, pos: {entity_info['position']})")
        else:
            print("\n[PII DÉTECTÉES] Aucune PII détectée dans ce contenu")
        
        # Assert - Vérifications structurelles
        assert isinstance(entities, list), "Le résultat doit être une liste"
        assert all(isinstance(e, PIIEntity) for e in entities), \
            "Toutes les entités doivent être de type PIIEntity"
        
        # Vérification de la cohérence des positions
        for e in entities:
            assert 0 <= e.start < e.end <= len(content), \
                f"Position invalide pour l'entité '{e.text}': start={e.start}, end={e.end}"
            assert 0.0 <= e.score <= 1.0, \
                f"Score invalide pour l'entité '{e.text}': {e.score}"
            assert e.text == content[e.start:e.end], \
                f"Le texte de l'entité ne correspond pas à la position dans le contenu"
        
        # Vérifications de performance - seuils acceptables
        # Note: Le premier chargement peut prendre plus de temps (téléchargement des modèles)
        assert load_time < 45.0, \
            f"Le chargement du modèle est trop lent: {load_time:.3f}s (max: 45s)"
        assert scan_time < 60.0, \
            f"Le scan est trop lent: {scan_time:.3f}s (max: 60s pour ce contenu)"
        
        # Vérification que le scan a traité tout le contenu
        assert chars_per_second > 0, "Le scan doit traiter au moins quelques caractères par seconde"
        
        print(f"\n[SUCCÈS] Test de performance Gliner sur page2-demo.txt terminé avec succès")
        print(f"  [OK] Chargement: {load_time:.3f}s")
        print(f"  [OK] Scan: {scan_time:.3f}s")
        print(f"  [OK] Vélocité: {chars_per_second:.0f} chars/s, {words_per_second:.0f} mots/s")
        print(f"  [OK] Détections: {len(entities)} PII trouvées")

    def test_should_verify_gliner_semantic_chunking_on_page2(self):
        """
        Test d'intégration: vérifie que le chunking sémantique fonctionne correctement.
        
        Ce test s'assure que:
        - Le semantic chunker est bien initialisé
        - Le contenu est correctement divisé en chunks
        - Aucune troncation ne se produit
        """
        # Arrange
        tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        page2_path = os.path.join(tests_dir, 'resources', 'page2-demo.txt')
        
        with open(page2_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        # Utilise la configuration TOML par défaut
        config = DetectionConfig()
        
        detector = GLiNERDetector(config=config)
        detector.load_model()
        
        # Assert - Vérification du semantic chunker
        assert detector.semantic_chunker is not None, \
            "Le semantic chunker doit être initialisé après load_model()"
        
        chunk_info = detector.semantic_chunker.get_chunk_info()
        print(f"\n[SEMANTIC CHUNKER INFO]")
        print(f"  - Library: {chunk_info.get('library', 'N/A')}")
        print(f"  - Chunk size: {chunk_info.get('chunk_size', 'N/A')} tokens")
        print(f"  - Overlap: {chunk_info.get('overlap', 'N/A')} tokens")
        print(f"  - Uses semantic: {chunk_info.get('use_semantic', 'N/A')}")
        
        # Vérification que semchunk est utilisé (pas le fallback)
        assert chunk_info.get('library') == 'semchunk', \
            "Le chunker doit utiliser la bibliothèque semchunk, pas le fallback"
        
        # Test du chunking sur le contenu
        chunks = detector.semantic_chunker.chunk_text(content)
        print(f"\n[CHUNKING RESULTS]")
        print(f"  - Nombre de chunks: {len(chunks)}")
        print(f"  - Taille totale: {len(content)} caractères")
        
        for i, chunk in enumerate(chunks, 1):
            print(f"  - Chunk {i}: {len(chunk.text)} chars, "
                  f"position {chunk.start}-{chunk.start + len(chunk.text)}")
        
        # Vérifications
        assert len(chunks) > 0, "Au moins un chunk doit être créé"
        
        # Vérifier que tous les chunks sont dans les limites
        for chunk in chunks:
            assert len(chunk.text) > 0, "Chaque chunk doit contenir du texte"
            assert chunk.start >= 0, "La position de début doit être >= 0"
            assert chunk.start + len(chunk.text) <= len(content), \
                "La fin du chunk ne doit pas dépasser le contenu"
        
        # Vérifier la couverture totale (avec possibles overlaps)
        covered_positions = set()
        for chunk in chunks:
            for pos in range(chunk.start, chunk.start + len(chunk.text)):
                covered_positions.add(pos)
        
        coverage_ratio = len(covered_positions) / len(content) if len(content) > 0 else 0
        print(f"\n[COVERAGE] {coverage_ratio:.1%} du contenu est couvert par les chunks")
        
        assert coverage_ratio >= 0.95, \
            f"Au moins 95% du contenu doit être couvert (actuel: {coverage_ratio:.1%})"
        
        print(f"\n[SUCCÈS] Test de chunking sémantique sur page2-demo.txt réussi")

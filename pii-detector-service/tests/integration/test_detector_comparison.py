"""
Test d'intégration comparant différentes configurations de détection PII.

Ce test évalue les performances et la qualité de détection pour:
1. GLiNER seul (baseline)
2. GLiNER + Presidio (détection améliorée)
3. GLiNER + Regex + Presidio (détection complète)

Métriques mesurées:
- Temps de traitement (secondes)
- Vélocité (chars/seconde)
- Nombre de PII détectées
- Types de PII détectées
- Couverture par type de PII
"""

import logging
import sys
import time
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import List, Dict

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from pii_detector.service.detector.gliner_detector import GLiNERDetector
from pii_detector.service.detector.composite_detector import CompositePIIDetector
from pii_detector.service.detector.regex_detector import RegexDetector
from pii_detector.service.detector.models import PIIEntity


class SimpleDetectionConfig:
    """Configuration simple pour les tests sans chargement TOML."""
    def __init__(
        self,
        model_id: str,
        device: str,
        max_length: int,
        threshold: float,
        batch_size: int,
        stride_tokens: int,
        long_text_threshold: int,
        custom_filenames: dict = None
    ):
        self.model_id = model_id
        self.device = device
        self.max_length = max_length
        self.threshold = threshold
        self.batch_size = batch_size
        self.stride_tokens = stride_tokens
        self.long_text_threshold = long_text_threshold
        self.custom_filenames = custom_filenames or {}

# Configuration du logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Tenter d'importer Presidio
try:
    from pii_detector.service.detector.presidio_detector import PresidioDetector
    PRESIDIO_AVAILABLE = True
    logger.info("Presidio disponible - tous les tests seront exécutés")
except ImportError as e:
    PRESIDIO_AVAILABLE = False
    PresidioDetector = None
    logger.warning(f"Presidio non disponible: {e}")
    logger.warning("Les tests avec Presidio utiliseront GLiNER + Regex uniquement")


@dataclass
class DetectionResult:
    """Résultat de détection pour une configuration."""
    config_name: str
    execution_time_seconds: float
    text_length_chars: int
    chars_per_second: float
    total_pii_detected: int
    unique_pii_types: List[str]
    pii_by_type: Dict[str, int]
    entities: List[PIIEntity]


@dataclass
class ComparisonReport:
    """Rapport de comparaison entre configurations."""
    gliner_only: DetectionResult
    gliner_presidio: DetectionResult
    gliner_regex_presidio: DetectionResult
    text_content_preview: str


class DetectorComparisonSuite:
    """Suite de tests pour comparer les configurations de détection."""
    
    def __init__(self, test_input_path: Path):
        """
        Initialise la suite de tests.
        
        Args:
            test_input_path: Chemin vers le fichier test-input.txt
        """
        self.test_input_path = test_input_path
        self.test_text = self._load_test_text()
        logger.info(f"Texte de test chargé: {len(self.test_text)} caractères")
        
        # Initialiser le modèle GLiNER une seule fois avec des paramètres explicites
        logger.info("Initialisation du modèle GLiNER (chargement unique)...")
        config = SimpleDetectionConfig(
            model_id="nvidia/gliner-pii",
            device="cpu",
            max_length=720,
            threshold=0.3,
            batch_size=4,
            stride_tokens=100,
            long_text_threshold=10000,
            custom_filenames={"config.json": "gliner_config.json"}
        )
        self.gliner_detector = GLiNERDetector(config=config)
        self.gliner_detector.download_model()
        self.gliner_detector.load_model()
        logger.info("Modèle GLiNER chargé et prêt pour tous les tests")
    
    def _load_test_text(self) -> str:
        """Charge le contenu du fichier test-input.txt."""
        with open(self.test_input_path, 'r', encoding='utf-8') as f:
            return f.read()
    
    def _normalize_entity_key(self, entity: PIIEntity) -> str:
        """
        Crée une clé normalisée pour comparer les entités.
        
        Args:
            entity: Entité PII
            
        Returns:
            Clé de comparaison (texte + position approximative)
        """
        text_snippet = self.test_text[entity.start:entity.end].lower().strip()
        # Utiliser une fenêtre de tolérance de 5 caractères pour la position
        start_bucket = entity.start // 5 * 5
        return f"{text_snippet}_{start_bucket}"
    
    def _compare_detector_differences(
        self,
        presidio_result: DetectionResult,
        gliner_result: DetectionResult
    ) -> Dict[str, List[PIIEntity]]:
        """
        Compare les PII détectées par Presidio vs GLiNER.
        
        Args:
            presidio_result: Résultats de Presidio
            gliner_result: Résultats de GLiNER
            
        Returns:
            Dictionnaire avec les clés 'presidio_only' et 'gliner_only'
        """
        logger.info("\n" + "="*80)
        logger.info("ANALYSE DES DIFFÉRENCES PRESIDIO vs GLiNER")
        logger.info("="*80)
        
        # Créer des ensembles de clés normalisées pour chaque détecteur
        presidio_keys = {self._normalize_entity_key(e): e for e in presidio_result.entities}
        gliner_keys = {self._normalize_entity_key(e): e for e in gliner_result.entities}
        
        # Trouver les différences
        presidio_only_keys = set(presidio_keys.keys()) - set(gliner_keys.keys())
        gliner_only_keys = set(gliner_keys.keys()) - set(presidio_keys.keys())
        
        presidio_only = [presidio_keys[key] for key in presidio_only_keys]
        gliner_only = [gliner_keys[key] for key in gliner_only_keys]
        
        # Logger les résultats
        logger.info(f"\n📊 Résumé de la comparaison:")
        logger.info(f"  • Total Presidio: {len(presidio_result.entities)} PII")
        logger.info(f"  • Total GLiNER: {len(gliner_result.entities)} PII")
        logger.info(f"  • Détectées uniquement par Presidio: {len(presidio_only)}")
        logger.info(f"  • Détectées uniquement par GLiNER: {len(gliner_only)}")
        logger.info(f"  • Détectées par les deux: {len(presidio_result.entities) - len(presidio_only)}")
        
        # Logger les PII détectées uniquement par Presidio
        if presidio_only:
            logger.info(f"\n🔵 PII détectées UNIQUEMENT par Presidio ({len(presidio_only)}):")
            logger.info("-" * 80)
            for idx, entity in enumerate(sorted(presidio_only, key=lambda e: e.start), 1):
                text_snippet = self.test_text[entity.start:entity.end]
                pii_type = str(entity.pii_type.value) if hasattr(entity.pii_type, 'value') else str(entity.pii_type)
                logger.info(f"  [{idx:3d}] {pii_type:20s} | pos {entity.start:5d}-{entity.end:5d} | "
                           f"score {entity.score:.3f} | '{text_snippet}'")
        else:
            logger.info(f"\n🔵 Aucune PII détectée uniquement par Presidio")
        
        # Logger les PII détectées uniquement par GLiNER
        if gliner_only:
            logger.info(f"\n🟢 PII détectées UNIQUEMENT par GLiNER ({len(gliner_only)}):")
            logger.info("-" * 80)
            for idx, entity in enumerate(sorted(gliner_only, key=lambda e: e.start), 1):
                text_snippet = self.test_text[entity.start:entity.end]
                pii_type = str(entity.pii_type.value) if hasattr(entity.pii_type, 'value') else str(entity.pii_type)
                logger.info(f"  [{idx:3d}] {pii_type:20s} | pos {entity.start:5d}-{entity.end:5d} | "
                           f"score {entity.score:.3f} | '{text_snippet}'")
        else:
            logger.info(f"\n🟢 Aucune PII détectée uniquement par GLiNER")
        
        # Analyse par type de PII
        logger.info(f"\n📈 Analyse par type de PII:")
        logger.info("-" * 80)
        
        if presidio_only:
            presidio_only_types = Counter([
                str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) 
                for e in presidio_only
            ])
            logger.info(f"\n  Types détectés uniquement par Presidio:")
            for pii_type, count in sorted(presidio_only_types.items(), key=lambda x: x[1], reverse=True):
                logger.info(f"    • {pii_type}: {count}")
        
        if gliner_only:
            gliner_only_types = Counter([
                str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) 
                for e in gliner_only
            ])
            logger.info(f"\n  Types détectés uniquement par GLiNER:")
            for pii_type, count in sorted(gliner_only_types.items(), key=lambda x: x[1], reverse=True):
                logger.info(f"    • {pii_type}: {count}")
        
        return {
            'presidio_only': presidio_only,
            'gliner_only': gliner_only
        }
    
    def test_presidio_only(self) -> DetectionResult:
        """
        Test avec Presidio seul (pour diagnostic).
        
        Returns:
            Résultats de détection
        """
        logger.info("\n" + "="*80)
        logger.info("TEST 0: PRESIDIO SEUL (DIAGNOSTIC)")
        logger.info("="*80)
        
        if not PRESIDIO_AVAILABLE:
            logger.warning("Presidio non disponible - test ignoré")
            return None
        
        # Configuration Presidio
        logger.info("Initialisation de Presidio...")
        presidio_detector = PresidioDetector()
        presidio_detector.load_model()
        
        # Exécution de la détection
        logger.info("Exécution de la détection...")
        start_time = time.time()
        entities = presidio_detector.detect_pii(self.test_text, threshold=0.5)
        end_time = time.time()
        
        execution_time = end_time - start_time
        chars_per_second = len(self.test_text) / execution_time if execution_time > 0 else 0
        
        # Analyse des résultats - convertir tous les types en strings pour éviter les erreurs de tri
        pii_types = [str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) for e in entities]
        unique_types = sorted(set(pii_types))
        pii_by_type = dict(Counter(pii_types))
        
        result = DetectionResult(
            config_name="Presidio seul",
            execution_time_seconds=execution_time,
            text_length_chars=len(self.test_text),
            chars_per_second=chars_per_second,
            total_pii_detected=len(entities),
            unique_pii_types=unique_types,
            pii_by_type=pii_by_type,
            entities=entities
        )
        
        self._log_result(result)
        return result
    
    def test_gliner_only(self) -> DetectionResult:
        """
        Test avec GLiNER seul (baseline).
        
        Returns:
            Résultats de détection
        """
        logger.info("\n" + "="*80)
        logger.info("TEST 1: GLiNER SEUL (BASELINE)")
        logger.info("="*80)
        
        # Exécution de la détection avec le détecteur préchargé
        logger.info("Exécution de la détection...")
        start_time = time.time()
        entities = self.gliner_detector.detect_pii(self.test_text, threshold=0.5)
        end_time = time.time()
        
        execution_time = end_time - start_time
        chars_per_second = len(self.test_text) / execution_time if execution_time > 0 else 0
        
        # Analyse des résultats - convertir tous les types en strings pour éviter les erreurs de tri
        pii_types = [str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) for e in entities]
        unique_types = sorted(set(pii_types))
        pii_by_type = dict(Counter(pii_types))
        
        result = DetectionResult(
            config_name="GLiNER seul",
            execution_time_seconds=execution_time,
            text_length_chars=len(self.test_text),
            chars_per_second=chars_per_second,
            total_pii_detected=len(entities),
            unique_pii_types=unique_types,
            pii_by_type=pii_by_type,
            entities=entities
        )
        
        self._log_result(result)
        return result
    
    def test_gliner_presidio(self) -> DetectionResult:
        """
        Test avec GLiNER + Presidio.
        
        Returns:
            Résultats de détection
        """
        logger.info("\n" + "="*80)
        logger.info("TEST 2: GLiNER + PRESIDIO")
        logger.info("="*80)
        
        if not PRESIDIO_AVAILABLE:
            logger.warning("Presidio non disponible - test avec GLiNER + Regex seulement")
            return self.test_gliner_regex_only()
        
        # Configuration Presidio
        logger.info("Initialisation de Presidio...")
        presidio_detector = PresidioDetector()
        
        # Configuration composite avec le détecteur GLiNER préchargé
        composite_detector = CompositePIIDetector(
            ml_detector=self.gliner_detector,
            regex_detector=None,
            presidio_detector=presidio_detector,
            enable_regex=False,
            enable_presidio=True
        )
        
        # Exécution de la détection
        logger.info("Exécution de la détection...")
        start_time = time.time()
        entities = composite_detector.detect_pii(self.test_text, threshold=0.5)
        end_time = time.time()
        
        execution_time = end_time - start_time
        chars_per_second = len(self.test_text) / execution_time if execution_time > 0 else 0
        
        # Analyse des résultats - convertir tous les types en strings pour éviter les erreurs de tri
        pii_types = [str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) for e in entities]
        unique_types = sorted(set(pii_types))
        pii_by_type = dict(Counter(pii_types))
        
        result = DetectionResult(
            config_name="GLiNER + Presidio",
            execution_time_seconds=execution_time,
            text_length_chars=len(self.test_text),
            chars_per_second=chars_per_second,
            total_pii_detected=len(entities),
            unique_pii_types=unique_types,
            pii_by_type=pii_by_type,
            entities=entities
        )
        
        self._log_result(result)
        return result
    
    def test_gliner_regex_only(self) -> DetectionResult:
        """
        Test avec GLiNER + Regex (sans Presidio).
        
        Returns:
            Résultats de détection
        """
        logger.info("\n" + "="*80)
        logger.info("TEST 2b: GLiNER + REGEX (SANS PRESIDIO)")
        logger.info("="*80)
        
        # Configuration Regex
        logger.info("Initialisation de Regex...")
        regex_config = SimpleDetectionConfig(
            model_id="regex-detector",
            device="cpu",
            max_length=0,
            threshold=0.5,
            batch_size=1,
            stride_tokens=0,
            long_text_threshold=0
        )
        regex_detector = RegexDetector(config=regex_config)
        
        # Configuration composite avec le détecteur GLiNER préchargé
        composite_detector = CompositePIIDetector(
            ml_detector=self.gliner_detector,
            regex_detector=regex_detector,
            presidio_detector=None,
            enable_regex=True,
            enable_presidio=False
        )
        
        # Exécution de la détection
        logger.info("Exécution de la détection...")
        start_time = time.time()
        entities = composite_detector.detect_pii(self.test_text, threshold=0.5)
        end_time = time.time()
        
        execution_time = end_time - start_time
        chars_per_second = len(self.test_text) / execution_time if execution_time > 0 else 0
        
        # Analyse des résultats - convertir tous les types en strings pour éviter les erreurs de tri
        pii_types = [str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) for e in entities]
        unique_types = sorted(set(pii_types))
        pii_by_type = dict(Counter(pii_types))
        
        result = DetectionResult(
            config_name="GLiNER + Regex",
            execution_time_seconds=execution_time,
            text_length_chars=len(self.test_text),
            chars_per_second=chars_per_second,
            total_pii_detected=len(entities),
            unique_pii_types=unique_types,
            pii_by_type=pii_by_type,
            entities=entities
        )
        
        self._log_result(result)
        return result
    
    def test_gliner_regex_presidio(self) -> DetectionResult:
        """
        Test avec GLiNER + Regex + Presidio (configuration complète).
        
        Returns:
            Résultats de détection
        """
        logger.info("\n" + "="*80)
        logger.info("TEST 3: GLiNER + REGEX + PRESIDIO (COMPLET)")
        logger.info("="*80)
        
        if not PRESIDIO_AVAILABLE:
            logger.warning("Presidio non disponible - test avec GLiNER + Regex seulement")
            return self.test_gliner_regex_only()
        
        # Configuration Regex
        logger.info("Initialisation de Regex...")
        regex_config = SimpleDetectionConfig(
            model_id="regex-detector",
            device="cpu",
            max_length=0,
            threshold=0.5,
            batch_size=1,
            stride_tokens=0,
            long_text_threshold=0
        )
        regex_detector = RegexDetector(config=regex_config)
        
        # Configuration Presidio
        logger.info("Initialisation de Presidio...")
        presidio_detector = PresidioDetector()
        
        # Configuration composite avec le détecteur GLiNER préchargé
        composite_detector = CompositePIIDetector(
            ml_detector=self.gliner_detector,
            regex_detector=regex_detector,
            presidio_detector=presidio_detector,
            enable_regex=True,
            enable_presidio=True
        )
        
        # Exécution de la détection
        logger.info("Exécution de la détection...")
        start_time = time.time()
        entities = composite_detector.detect_pii(self.test_text, threshold=0.5)
        end_time = time.time()
        
        execution_time = end_time - start_time
        chars_per_second = len(self.test_text) / execution_time if execution_time > 0 else 0
        
        # Analyse des résultats - convertir tous les types en strings pour éviter les erreurs de tri
        pii_types = [str(e.pii_type.value) if hasattr(e.pii_type, 'value') else str(e.pii_type) for e in entities]
        unique_types = sorted(set(pii_types))
        pii_by_type = dict(Counter(pii_types))
        
        result = DetectionResult(
            config_name="GLiNER + Regex + Presidio",
            execution_time_seconds=execution_time,
            text_length_chars=len(self.test_text),
            chars_per_second=chars_per_second,
            total_pii_detected=len(entities),
            unique_pii_types=unique_types,
            pii_by_type=pii_by_type,
            entities=entities
        )
        
        self._log_result(result)
        return result
    
    def _log_result(self, result: DetectionResult):
        """
        Affiche les résultats de détection de manière lisible.
        
        Args:
            result: Résultats à afficher
        """
        logger.info(f"\n[RÉSULTATS] {result.config_name}")
        logger.info(f"  Temps d'exécution: {result.execution_time_seconds:.2f}s")
        logger.info(f"  Longueur du texte: {result.text_length_chars:,} caractères")
        logger.info(f"  Vélocité: {result.chars_per_second:.0f} chars/s")
        logger.info(f"  Total PII détectées: {result.total_pii_detected}")
        logger.info(f"  Types uniques de PII: {len(result.unique_pii_types)}")
        logger.info(f"  Types: {', '.join(result.unique_pii_types)}")
        
        logger.info(f"\n  Répartition par type:")
        for pii_type, count in sorted(result.pii_by_type.items(), key=lambda x: x[1], reverse=True):
            logger.info(f"    - {pii_type}: {count}")
        
        # Logger chaque PII détectée
        logger.info(f"\n  Détail des PII détectées:")
        for idx, entity in enumerate(result.entities, 1):
            text_snippet = self.test_text[entity.start:entity.end]
            logger.info(f"    [{idx:3d}] {entity.pii_type:20s} | pos {entity.start:5d}-{entity.end:5d} | score {entity.score:.3f} | '{text_snippet}'")
    
    def generate_comparison_report(
        self,
        gliner_only: DetectionResult,
        gliner_presidio: DetectionResult,
        gliner_regex_presidio: DetectionResult
    ) -> str:
        """
        Génère un rapport de comparaison détaillé.
        
        Args:
            gliner_only: Résultats GLiNER seul
            gliner_presidio: Résultats GLiNER + Presidio
            gliner_regex_presidio: Résultats GLiNER + Regex + Presidio
            
        Returns:
            Rapport formaté en texte
        """
        report = []
        report.append("\n" + "="*80)
        report.append("RAPPORT DE COMPARAISON - CONFIGURATIONS DE DÉTECTION PII")
        report.append("="*80)
        
        # Résumé exécutif
        report.append("\n📊 RÉSUMÉ EXÉCUTIF")
        report.append("-" * 80)
        report.append(f"Texte analysé: {gliner_only.text_length_chars:,} caractères")
        report.append(f"\nConfiguration 1 - GLiNER seul (baseline):")
        report.append(f"  • Temps: {gliner_only.execution_time_seconds:.2f}s")
        report.append(f"  • Vélocité: {gliner_only.chars_per_second:.0f} chars/s")
        report.append(f"  • PII détectées: {gliner_only.total_pii_detected}")
        report.append(f"  • Types uniques: {len(gliner_only.unique_pii_types)}")
        
        report.append(f"\nConfiguration 2 - GLiNER + Presidio:")
        report.append(f"  • Temps: {gliner_presidio.execution_time_seconds:.2f}s")
        report.append(f"  • Vélocité: {gliner_presidio.chars_per_second:.0f} chars/s")
        report.append(f"  • PII détectées: {gliner_presidio.total_pii_detected}")
        report.append(f"  • Types uniques: {len(gliner_presidio.unique_pii_types)}")
        report.append(f"  • Gain vs baseline: {gliner_presidio.total_pii_detected - gliner_only.total_pii_detected:+d} PII")
        
        report.append(f"\nConfiguration 3 - GLiNER + Regex + Presidio:")
        report.append(f"  • Temps: {gliner_regex_presidio.execution_time_seconds:.2f}s")
        report.append(f"  • Vélocité: {gliner_regex_presidio.chars_per_second:.0f} chars/s")
        report.append(f"  • PII détectées: {gliner_regex_presidio.total_pii_detected}")
        report.append(f"  • Types uniques: {len(gliner_regex_presidio.unique_pii_types)}")
        report.append(f"  • Gain vs baseline: {gliner_regex_presidio.total_pii_detected - gliner_only.total_pii_detected:+d} PII")
        
        # Analyse de performance
        report.append("\n\n⚡ ANALYSE DE PERFORMANCE")
        report.append("-" * 80)
        
        # Temps relatif
        baseline_time = gliner_only.execution_time_seconds
        presidio_overhead = ((gliner_presidio.execution_time_seconds / baseline_time) - 1) * 100
        full_overhead = ((gliner_regex_presidio.execution_time_seconds / baseline_time) - 1) * 100
        
        report.append(f"Overhead temporel:")
        report.append(f"  • GLiNER + Presidio: {presidio_overhead:+.1f}%")
        report.append(f"  • GLiNER + Regex + Presidio: {full_overhead:+.1f}%")
        
        # Vélocité
        report.append(f"\nVélocité (chars/seconde):")
        report.append(f"  • GLiNER seul: {gliner_only.chars_per_second:.0f}")
        report.append(f"  • GLiNER + Presidio: {gliner_presidio.chars_per_second:.0f}")
        report.append(f"  • GLiNER + Regex + Presidio: {gliner_regex_presidio.chars_per_second:.0f}")
        
        # Analyse de couverture
        report.append("\n\n📈 ANALYSE DE COUVERTURE")
        report.append("-" * 80)
        
        # Types détectés
        gliner_types = set(gliner_only.unique_pii_types)
        presidio_types = set(gliner_presidio.unique_pii_types)
        full_types = set(gliner_regex_presidio.unique_pii_types)
        
        types_added_by_presidio = presidio_types - gliner_types
        types_added_by_regex = full_types - presidio_types
        
        report.append(f"Types de PII par configuration:")
        report.append(f"  • GLiNER seul: {len(gliner_types)} types")
        report.append(f"  • GLiNER + Presidio: {len(presidio_types)} types")
        if types_added_by_presidio:
            report.append(f"    Nouveaux types: {', '.join(sorted(types_added_by_presidio))}")
        report.append(f"  • GLiNER + Regex + Presidio: {len(full_types)} types")
        if types_added_by_regex:
            report.append(f"    Nouveaux types: {', '.join(sorted(types_added_by_regex))}")
        
        # Comparaison par type
        report.append(f"\n\nComparaison par type de PII:")
        all_types = sorted(gliner_types | presidio_types | full_types)
        
        for pii_type in all_types:
            count_gliner = gliner_only.pii_by_type.get(pii_type, 0)
            count_presidio = gliner_presidio.pii_by_type.get(pii_type, 0)
            count_full = gliner_regex_presidio.pii_by_type.get(pii_type, 0)
            
            report.append(f"  • {pii_type}:")
            report.append(f"      GLiNER: {count_gliner:3d} | "
                         f"+ Presidio: {count_presidio:3d} ({count_presidio - count_gliner:+d}) | "
                         f"+ Regex: {count_full:3d} ({count_full - count_presidio:+d})")
        
        # Recommandations
        report.append("\n\n🎯 RECOMMANDATIONS")
        report.append("-" * 80)
        
        best_detection = max(
            gliner_only.total_pii_detected,
            gliner_presidio.total_pii_detected,
            gliner_regex_presidio.total_pii_detected
        )
        
        if gliner_regex_presidio.total_pii_detected == best_detection:
            report.append("✅ Configuration recommandée: GLiNER + Regex + Presidio")
            report.append(f"   • Meilleure couverture: {gliner_regex_presidio.total_pii_detected} PII détectées")
            report.append(f"   • Overhead acceptable: {full_overhead:+.1f}%")
            report.append(f"   • {len(full_types)} types de PII supportés")
        elif gliner_presidio.total_pii_detected == best_detection:
            report.append("✅ Configuration recommandée: GLiNER + Presidio")
            report.append(f"   • Bonne couverture: {gliner_presidio.total_pii_detected} PII détectées")
            report.append(f"   • Overhead modéré: {presidio_overhead:+.1f}%")
        else:
            report.append("✅ Configuration recommandée: GLiNER seul")
            report.append("   • Configuration baseline suffisante pour ce texte")
        
        report.append("\n\nConsidérations:")
        report.append("• Presidio apporte une validation supplémentaire pour certains types (email, phone, etc.)")
        report.append("• Regex permet de capturer des patterns structurés manqués par les LLM")
        report.append("• L'overhead temporel reste acceptable pour la plupart des cas d'usage")
        report.append("• La configuration complète offre la meilleure couverture au prix d'un temps légèrement plus long")
        
        report.append("\n" + "="*80 + "\n")
        
        return "\n".join(report)
    
    def run_all_tests(self) -> ComparisonReport:
        """
        Exécute tous les tests et génère un rapport.
        
        Returns:
            Rapport de comparaison complet
        """
        # Test diagnostic Presidio seul
        presidio_only = self.test_presidio_only()
        
        # Exécuter les trois configurations
        gliner_only = self.test_gliner_only()
        gliner_presidio = self.test_gliner_presidio()
        gliner_regex_presidio = self.test_gliner_regex_presidio()
        
        # Comparer Presidio vs GLiNER si Presidio est disponible
        if PRESIDIO_AVAILABLE and presidio_only is not None and gliner_only is not None:
            self._compare_detector_differences(presidio_only, gliner_only)
        
        # Générer le rapport
        report_text = self.generate_comparison_report(
            gliner_only,
            gliner_presidio,
            gliner_regex_presidio
        )
        
        # Afficher le rapport (avec encodage UTF-8 pour les emojis)
        try:
            print(report_text)
        except UnicodeEncodeError:
            # Fallback si la console ne supporte pas UTF-8
            print(report_text.encode('ascii', 'replace').decode('ascii'))
        
        # Créer le rapport de comparaison
        return ComparisonReport(
            gliner_only=gliner_only,
            gliner_presidio=gliner_presidio,
            gliner_regex_presidio=gliner_regex_presidio,
            text_content_preview=self.test_text[:200] + "..."
        )


def main():
    """Point d'entrée principal du test."""
    print("="*80)
    print("TEST D'INTÉGRATION - COMPARAISON DES CONFIGURATIONS DE DÉTECTION")
    print("="*80)
    
    # Chemin vers le fichier de test
    test_input_path = Path(__file__).parent.parent / "resources" / "test-input.txt"
    
    if not test_input_path.exists():
        logger.error(f"Fichier de test introuvable: {test_input_path}")
        sys.exit(1)
    
    logger.info(f"Fichier de test: {test_input_path}")
    
    # Créer la suite de tests
    suite = DetectorComparisonSuite(test_input_path)
    
    # Exécuter tous les tests
    report = suite.run_all_tests()
    
    # Sauvegarder le rapport
    output_file = Path(__file__).parent.parent.parent / "detector_comparison_report.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        comparison_text = suite.generate_comparison_report(
            report.gliner_only,
            report.gliner_presidio,
            report.gliner_regex_presidio
        )
        f.write(comparison_text)
    
    logger.info(f"\n[RAPPORT] Sauvegarde dans: {output_file}")
    
    print("\n[SUCCESS] Test termine avec succes!")


if __name__ == "__main__":
    main()

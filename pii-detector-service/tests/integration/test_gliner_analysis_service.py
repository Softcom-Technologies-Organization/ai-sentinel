"""
Integration test for PII detectors using the analysis service.
This test loads actual detector models and processes text to detect PII entities.
Primary goal: Log comprehensive detection results for manual verification.

Tests included:
1. GLiNER detector (LLM-based)
2. Presidio detector (Microsoft's rule-based)
3. Custom Regex detector (pattern-based)
4. Full pipeline with aggregation of 3 detectors (GLiNER, Presidio, Regex)
"""

import os
import sys
import time
from pathlib import Path
from collections import defaultdict
from unittest.mock import patch, MagicMock

import pytest

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector
from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector
from pii_detector.infrastructure.detector.regex_detector import RegexDetector


# Configurable threshold map for Presidio PII types
# Easily modify these values to adjust detection sensitivity per PII type
PRESIDIO_PII_TYPE_THRESHOLDS = {
    # Contact Information
    "EMAIL": 0.6,
    "PHONE": 0.6,
    "URL": 0.6,
    
    # Financial
    "CREDIT_CARD": 0.6,
    "IBAN": 0.6,
    "CRYPTO_WALLET": 0.6,
    
    # Network
    "IP_ADDRESS": 0.6,
    "MAC_ADDRESS": 0.6,
    
    # Personal Data
    "PERSON_NAME": 0.6,
    "LOCATION": 0.6,
    "DATE": 0.6,
    "AGE": 0.6,
    "NRP": 0.6,
    
    # Medical
    "MEDICAL_LICENSE": 0.6,
    
    # USA
    "US_SSN": 0.6,
    "US_BANK_NUMBER": 0.6,
    "US_DRIVER_LICENSE": 0.6,
    "US_ITIN": 0.6,
    "US_PASSPORT": 0.6,
    
    # UK
    "UK_NHS": 0.6,
    "UK_NINO": 0.6,
    
    # Spain
    "ES_NIF": 0.6,
    "ES_NIE": 0.6,
    
    # Italy
    "IT_FISCAL_CODE": 0.6,
    "IT_DRIVER_LICENSE": 0.6,
    "IT_VAT_CODE": 0.6,
    "IT_PASSPORT": 0.6,
    "IT_IDENTITY_CARD": 0.6,
    
    # Poland
    "PL_PESEL": 0.6,
    
    # Singapore
    "SG_NRIC_FIN": 0.6,
    "SG_UEN": 0.6,
    
    # Australia
    "AU_ABN": 0.6,
    "AU_ACN": 0.6,
    "AU_TFN": 0.6,
    "AU_MEDICARE": 0.6,
    
    # India
    "IN_PAN": 0.6,
    "IN_AADHAAR": 0.6,
    "IN_VEHICLE_REGISTRATION": 0.6,
    "IN_VOTER": 0.6,
    "IN_PASSPORT": 0.6,
    
    # Finland
    "FI_PERSONAL_IDENTITY_CODE": 0.6,
    
    # Korea
    "KR_RRN": 0.6,
    
    # Thailand
    "TH_TNIN": 0.6,
}

# Mapping from internal PII type to Presidio detector_label (entity type)
PII_TYPE_TO_PRESIDIO_LABEL = {
    # Contact Information
    "EMAIL": "EMAIL_ADDRESS",
    "PHONE": "PHONE_NUMBER",
    "URL": "URL",
    
    # Financial
    "CREDIT_CARD": "CREDIT_CARD",
    "IBAN": "IBAN_CODE",
    "CRYPTO_WALLET": "CRYPTO",
    
    # Network
    "IP_ADDRESS": "IP_ADDRESS",
    "MAC_ADDRESS": "MAC_ADDRESS",
    
    # Personal Data
    "PERSON_NAME": "PERSON",
    "LOCATION": "LOCATION",
    "DATE": "DATE_TIME",
    "AGE": "AGE",
    "NRP": "NRP",
    
    # Medical
    "MEDICAL_LICENSE": "MEDICAL_LICENSE",
    
    # USA
    "US_SSN": "US_SSN",
    "US_BANK_NUMBER": "US_BANK_NUMBER",
    "US_DRIVER_LICENSE": "US_DRIVER_LICENSE",
    "US_ITIN": "US_ITIN",
    "US_PASSPORT": "US_PASSPORT",
    
    # UK
    "UK_NHS": "UK_NHS",
    "UK_NINO": "UK_NINO",
    
    # Spain
    "ES_NIF": "ES_NIF",
    "ES_NIE": "ES_NIE",
    
    # Italy
    "IT_FISCAL_CODE": "IT_FISCAL_CODE",
    "IT_DRIVER_LICENSE": "IT_DRIVER_LICENSE",
    "IT_VAT_CODE": "IT_VAT_CODE",
    "IT_PASSPORT": "IT_PASSPORT",
    "IT_IDENTITY_CARD": "IT_IDENTITY_CARD",
    
    # Poland
    "PL_PESEL": "PL_PESEL",
    
    # Singapore
    "SG_NRIC_FIN": "SG_NRIC_FIN",
    "SG_UEN": "SG_UEN",
    
    # Australia
    "AU_ABN": "AU_ABN",
    "AU_ACN": "AU_ACN",
    "AU_TFN": "AU_TFN",
    "AU_MEDICARE": "AU_MEDICARE",
    
    # India
    "IN_PAN": "IN_PAN",
    "IN_AADHAAR": "IN_AADHAAR",
    "IN_VEHICLE_REGISTRATION": "IN_VEHICLE_REGISTRATION",
    "IN_VOTER": "IN_VOTER",
    "IN_PASSPORT": "IN_PASSPORT",
    
    # Finland
    "FI_PERSONAL_IDENTITY_CODE": "FI_PERSONAL_IDENTITY_CODE",
    
    # Korea
    "KR_RRN": "KR_RRN",
    
    # Thailand
    "TH_TNIN": "TH_TNIN",
}


def create_mock_presidio_db_configs():
    """
    Create mock database configuration for all Presidio PII types.
    
    Returns dictionary matching the structure returned by
    DatabaseConfigAdapter.fetch_pii_type_configs(detector='PRESIDIO')
    """
    configs = {}
    
    for pii_type, threshold in PRESIDIO_PII_TYPE_THRESHOLDS.items():
        detector_label = PII_TYPE_TO_PRESIDIO_LABEL.get(pii_type)
        if not detector_label:
            continue
            
        configs[pii_type] = {
            'enabled': True,
            'threshold': float(threshold),
            'detector': 'PRESIDIO',
            'display_name': pii_type.replace('_', ' ').title(),
            'description': f'Detects {pii_type.replace("_", " ").lower()}',
            'category': 'general',
            'country_code': None,
            'detector_label': detector_label
        }
    
    return configs


@pytest.mark.integration
@pytest.mark.slow
def test_gliner_detector_with_text_file():
    """
    Test GLiNER detector with text file containing various PII examples.
    
    This test:
    1. Loads the actual GLiNER model (not mocked)
    2. Reads text from tests/resources/text-for-gliner.txt
    3. Detects PII entities
    4. Logs comprehensive results grouped by PII type
    5. Asserts that results are not empty
    """
    print("\n" + "=" * 80)
    print("GLINER ANALYSIS SERVICE - INTEGRATION TEST")
    print("=" * 80)
    
    # Initialize detector and load model
    print("\n[1/4] Initializing GLiNER detector and loading model...")
    detector = GLiNERDetector()
    detector.load_model()
    print("[OK] Model loaded successfully")
    
    # Read test file
    print("\n[2/4] Reading text file...")
    tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    text_file_path = os.path.join(tests_dir, 'resources', 'text-for-gliner-as-extracted-from-confluence.txt')
    
    with open(text_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"[OK] File loaded: {text_file_path}")
    print(f"[OK] Content length: {len(content)} characters")
    print(f"[OK] Content lines: {len(content.splitlines())} lines")
    
    # Detect PII entities
    print("\n[3/4] Detecting PII entities with GLiNER...")
    threshold = 0.6
    start_time = time.time()
    entities = detector.detect_pii(content, threshold=threshold)
    elapsed_time = time.time() - start_time
    chars_per_second = len(content) / elapsed_time if elapsed_time > 0 else 0
    print(f"[OK] Detection completed in {elapsed_time:.2f}s with threshold={threshold}")
    print(f"[OK] Throughput: {chars_per_second:.0f} chars/second ({len(content)} chars)")
    print(f"[OK] Total entities detected: {len(entities)}")
    
    # Assert results are not empty
    assert len(entities) > 0, "Expected to detect at least one PII entity"
    
    # Log comprehensive results
    print("\n[4/4] DETAILED DETECTION RESULTS:")
    print("=" * 80)
    
    # Group entities by PII type
    entities_by_type = defaultdict(list)
    for entity in entities:
        entities_by_type[entity.pii_type].append(entity)
    
    # Display summary
    print(f"\n[SUMMARY]:")
    print(f"   Total entities detected: {len(entities)}")
    print(f"   Unique PII types found: {len(entities_by_type)}")
    print(f"   Detection threshold: {threshold}")
    
    # Display entities grouped by type
    print(f"\n[ENTITIES BY TYPE]:")
    print("-" * 80)
    
    for pii_type in sorted(entities_by_type.keys(), key=lambda x: str(x)):
        type_entities = entities_by_type[pii_type]
        print(f"\n[{pii_type}] ({len(type_entities)} occurrences)")
        print("   " + "-" * 76)
        
        for idx, entity in enumerate(type_entities, 1):
            # Extract context around the entity (20 chars before and after)
            start = max(0, entity.start - 20)
            end = min(len(content), entity.end + 20)
            context = content[start:end].replace('\n', ' ')
            
            print(f"   {idx}. Text: '{entity.text}'")
            print(f"      Score: {entity.score:.4f}")
            print(f"      Position: [{entity.start}:{entity.end}]")
            print(f"      Context: ...{context}...")
            print()
    
    # Display high-confidence detections (score >= 0.8)
    high_confidence = [e for e in entities if e.score >= 0.8]
    if high_confidence:
        print("\n[HIGH CONFIDENCE DETECTIONS] (score >= 0.8):")
        print("-" * 80)
        for entity in sorted(high_confidence, key=lambda e: e.score, reverse=True):
            print(f"   {entity.pii_type:30} | {entity.score:.4f} | '{entity.text}'")
    
    # Display statistics
    print("\n[STATISTICS]:")
    print("-" * 80)
    scores = [e.score for e in entities]
    print(f"   Average score: {sum(scores) / len(scores):.4f}")
    print(f"   Min score: {min(scores):.4f}")
    print(f"   Max score: {max(scores):.4f}")
    print(f"   High confidence (>=0.8): {len(high_confidence)} ({len(high_confidence) / len(entities) * 100:.1f}%)")
    
    # Display PII types distribution
    print("\n[PII TYPES DISTRIBUTION]:")
    print("-" * 80)
    for pii_type, type_entities in sorted(
        entities_by_type.items(), 
        key=lambda x: len(x[1]), 
        reverse=True
    ):
        count = len(type_entities)
        percentage = (count / len(entities)) * 100
        bar = "#" * int(percentage / 2)
        print(f"   {pii_type:30} | {count:3} | {percentage:5.1f}% | {bar}")
    
    print("\n" + "=" * 80)
    print("TEST COMPLETED SUCCESSFULLY [OK]")
    print("=" * 80 + "\n")


@pytest.mark.integration
@pytest.mark.slow
@patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter')
def test_presidio_detector_with_text_file(mock_get_db_adapter):
    """
    Test Presidio detector with text file containing various PII examples.
    
    This test:
    1. Loads the Presidio detector (Microsoft's rule-based PII detection)
    2. Reads text from tests/resources/text-for-gliner.txt
    3. Detects PII entities
    4. Logs comprehensive results grouped by PII type
    5. Asserts that results are not empty
    
    Note: Database calls are mocked to avoid dependency on PostgreSQL.
    """
    print("\n" + "=" * 80)
    print("PRESIDIO DETECTOR - INTEGRATION TEST")
    print("=" * 80)
    
    # Mock database adapter to return all PII types as enabled
    print("\n[1/4] Setting up database mock...")
    mock_adapter = MagicMock()
    mock_adapter.fetch_pii_type_configs.return_value = create_mock_presidio_db_configs()
    mock_get_db_adapter.return_value = mock_adapter
    print(f"[OK] Database mock configured with {len(PRESIDIO_PII_TYPE_THRESHOLDS)} PII types")
    
    # Initialize detector and load model
    print("\n[2/4] Initializing Presidio detector and loading model...")
    detector = PresidioDetector()
    detector.load_model()
    print("[OK] Model loaded successfully")
    
    # Read test file
    print("\n[3/4] Reading text file...")
    tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    text_file_path = os.path.join(tests_dir, 'resources', 'text-for-gliner-as-extracted-from-confluence.txt')
    
    with open(text_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"[OK] File loaded: {text_file_path}")
    print(f"[OK] Content length: {len(content)} characters")
    print(f"[OK] Content lines: {len(content.splitlines())} lines")
    
    # Detect PII entities
    print("\n[4/4] Detecting PII entities with Presidio...")
    threshold = 0.6
    start_time = time.time()
    entities = detector.detect_pii(content, threshold=threshold)
    elapsed_time = time.time() - start_time
    chars_per_second = len(content) / elapsed_time if elapsed_time > 0 else 0
    print(f"[OK] Detection completed in {elapsed_time:.2f}s with threshold={threshold}")
    print(f"[OK] Throughput: {chars_per_second:.0f} chars/second ({len(content)} chars)")
    print(f"[OK] Total entities detected: {len(entities)}")
    
    # Assert results are not empty
    assert len(entities) > 0, "Expected to detect at least one PII entity"
    
    # Log comprehensive results
    print("\n[5/5] DETAILED DETECTION RESULTS:")
    print("=" * 80)
    
    # Group entities by PII type
    entities_by_type = defaultdict(list)
    for entity in entities:
        entities_by_type[entity.pii_type].append(entity)
    
    # Display summary
    print(f"\n[SUMMARY]:")
    print(f"   Total entities detected: {len(entities)}")
    print(f"   Unique PII types found: {len(entities_by_type)}")
    print(f"   Detection threshold: {threshold}")
    print(f"   Detector source: PRESIDIO (rule-based)")
    
    # Display entities grouped by type
    print(f"\n[ENTITIES BY TYPE]:")
    print("-" * 80)
    
    for pii_type in sorted(entities_by_type.keys(), key=lambda x: str(x)):
        type_entities = entities_by_type[pii_type]
        print(f"\n[{pii_type}] ({len(type_entities)} occurrences)")
        print("   " + "-" * 76)
        
        for idx, entity in enumerate(type_entities, 1):
            # Extract context around the entity (20 chars before and after)
            start = max(0, entity.start - 20)
            end = min(len(content), entity.end + 20)
            context = content[start:end].replace('\n', ' ')
            
            print(f"   {idx}. Text: '{entity.text}'")
            print(f"      Score: {entity.score:.4f}")
            print(f"      Position: [{entity.start}:{entity.end}]")
            print(f"      Context: ...{context}...")
            print()
    
    # Display high-confidence detections (score >= 0.8)
    high_confidence = [e for e in entities if e.score >= 0.8]
    if high_confidence:
        print("\n[HIGH CONFIDENCE DETECTIONS] (score >= 0.8):")
        print("-" * 80)
        for entity in sorted(high_confidence, key=lambda e: e.score, reverse=True):
            print(f"   {entity.pii_type:30} | {entity.score:.4f} | '{entity.text}'")
    
    # Display statistics
    print("\n[STATISTICS]:")
    print("-" * 80)
    scores = [e.score for e in entities]
    print(f"   Average score: {sum(scores) / len(scores):.4f}")
    print(f"   Min score: {min(scores):.4f}")
    print(f"   Max score: {max(scores):.4f}")
    print(f"   High confidence (>=0.8): {len(high_confidence)} ({len(high_confidence) / len(entities) * 100:.1f}%)")
    
    # Display PII types distribution
    print("\n[PII TYPES DISTRIBUTION]:")
    print("-" * 80)
    for pii_type, type_entities in sorted(
        entities_by_type.items(), 
        key=lambda x: len(x[1]), 
        reverse=True
    ):
        count = len(type_entities)
        percentage = (count / len(entities)) * 100
        bar = "#" * int(percentage / 2)
        print(f"   {pii_type:30} | {count:3} | {percentage:5.1f}% | {bar}")
    
    print("\n" + "=" * 80)
    print("TEST COMPLETED SUCCESSFULLY [OK]")
    print("=" * 80 + "\n")


@pytest.mark.integration
@pytest.mark.slow
def test_regex_detector_with_text_file():
    """
    Test custom Regex detector with text file containing various PII examples.
    
    This test:
    1. Loads the custom Regex detector (pattern-based PII detection)
    2. Reads text from tests/resources/text-for-gliner.txt
    3. Detects PII entities using configured regex patterns
    4. Logs comprehensive results grouped by PII type
    5. Asserts that results are not empty
    """
    print("\n" + "=" * 80)
    print("CUSTOM REGEX DETECTOR - INTEGRATION TEST")
    print("=" * 80)
    
    # Initialize detector (no model loading needed for regex)
    print("\n[1/4] Initializing custom Regex detector...")
    detector = RegexDetector()
    detector.load_model()  # No-op for regex, but keeps interface consistency
    print(f"[OK] Detector initialized with {len(detector.patterns)} patterns")
    
    # Read test file
    print("\n[2/4] Reading text file...")
    tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    text_file_path = os.path.join(tests_dir, 'resources', 'text-for-gliner.txt')
    
    with open(text_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"[OK] File loaded: {text_file_path}")
    print(f"[OK] Content length: {len(content)} characters")
    print(f"[OK] Content lines: {len(content.splitlines())} lines")
    
    # Detect PII entities
    print("\n[3/4] Detecting PII entities with custom Regex...")
    threshold = 0.6
    start_time = time.time()
    entities = detector.detect_pii(content, threshold=threshold)
    elapsed_time = time.time() - start_time
    chars_per_second = len(content) / elapsed_time if elapsed_time > 0 else 0
    print(f"[OK] Detection completed in {elapsed_time:.2f}s with threshold={threshold}")
    print(f"[OK] Throughput: {chars_per_second:.0f} chars/second ({len(content)} chars)")
    print(f"[OK] Total entities detected: {len(entities)}")
    
    # Assert results are not empty
    assert len(entities) > 0, "Expected to detect at least one PII entity"
    
    # Log comprehensive results
    print("\n[4/4] DETAILED DETECTION RESULTS:")
    print("=" * 80)
    
    # Group entities by PII type
    entities_by_type = defaultdict(list)
    for entity in entities:
        entities_by_type[entity.pii_type].append(entity)
    
    # Display summary
    print(f"\n[SUMMARY]:")
    print(f"   Total entities detected: {len(entities)}")
    print(f"   Unique PII types found: {len(entities_by_type)}")
    print(f"   Detection threshold: {threshold}")
    print(f"   Detector source: REGEX (pattern-based)")
    
    # Display entities grouped by type
    print(f"\n[ENTITIES BY TYPE]:")
    print("-" * 80)
    
    for pii_type in sorted(entities_by_type.keys(), key=lambda x: str(x)):
        type_entities = entities_by_type[pii_type]
        print(f"\n[{pii_type}] ({len(type_entities)} occurrences)")
        print("   " + "-" * 76)
        
        for idx, entity in enumerate(type_entities, 1):
            # Extract context around the entity (20 chars before and after)
            start = max(0, entity.start - 20)
            end = min(len(content), entity.end + 20)
            context = content[start:end].replace('\n', ' ')
            
            print(f"   {idx}. Text: '{entity.text}'")
            print(f"      Score: {entity.score:.4f}")
            print(f"      Position: [{entity.start}:{entity.end}]")
            print(f"      Context: ...{context}...")
            print()
    
    # Display high-confidence detections (score >= 0.8)
    high_confidence = [e for e in entities if e.score >= 0.8]
    if high_confidence:
        print("\n[HIGH CONFIDENCE DETECTIONS] (score >= 0.8):")
        print("-" * 80)
        for entity in sorted(high_confidence, key=lambda e: e.score, reverse=True):
            print(f"   {entity.pii_type:30} | {entity.score:.4f} | '{entity.text}'")
    
    # Display statistics
    print("\n[STATISTICS]:")
    print("-" * 80)
    scores = [e.score for e in entities]
    print(f"   Average score: {sum(scores) / len(scores):.4f}")
    print(f"   Min score: {min(scores):.4f}")
    print(f"   Max score: {max(scores):.4f}")
    print(f"   High confidence (>=0.8): {len(high_confidence)} ({len(high_confidence) / len(entities) * 100:.1f}%)")
    
    # Display PII types distribution
    print("\n[PII TYPES DISTRIBUTION]:")
    print("-" * 80)
    for pii_type, type_entities in sorted(
        entities_by_type.items(), 
        key=lambda x: len(x[1]), 
        reverse=True
    ):
        count = len(type_entities)
        percentage = (count / len(entities)) * 100
        bar = "#" * int(percentage / 2)
        print(f"   {pii_type:30} | {count:3} | {percentage:5.1f}% | {bar}")
    
    print("\n" + "=" * 80)
    print("TEST COMPLETED SUCCESSFULLY [OK]")
    print("=" * 80 + "\n")


@pytest.mark.integration
@pytest.mark.slow
@patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter')
def test_full_pipeline_all_detectors_with_text_file(mock_get_db_adapter):
    """
    Test full detection pipeline with aggregation of 3 detectors.
    
    This test:
    1. Loads 3 detectors: GLiNER, Presidio, and Regex
    2. Reads text from tests/resources/text-for-gliner.txt
    3. Runs detection with each detector independently
    4. Aggregates all results
    5. Logs comprehensive results showing contribution of each detector
    6. Asserts that results are not empty
    
    Note: Presidio database calls are mocked to avoid dependency on PostgreSQL.
    """
    print("\n" + "=" * 80)
    print("FULL PIPELINE - 3 DETECTORS AGGREGATION - INTEGRATION TEST")
    print("=" * 80)
    
    # Mock database adapter for Presidio
    mock_adapter = MagicMock()
    mock_adapter.fetch_pii_type_configs.return_value = create_mock_presidio_db_configs()
    mock_get_db_adapter.return_value = mock_adapter
    
    # Read test file first
    print("\n[1/5] Reading text file...")
    tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    text_file_path = os.path.join(tests_dir, 'resources', 'text-for-gliner.txt')
    
    with open(text_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"[OK] File loaded: {text_file_path}")
    print(f"[OK] Content length: {len(content)} characters")
    print(f"[OK] Content lines: {len(content.splitlines())} lines")
    
    # Initialize 3 detectors
    print("\n[2/5] Initializing 3 detectors...")
    detectors = []
    detector_names = []
    
    print("   [1/3] GLiNER detector...")
    gliner_detector = GLiNERDetector()
    gliner_detector.load_model()
    detectors.append(('GLiNER', gliner_detector))
    detector_names.append('GLiNER')
    print("   [OK] GLiNER loaded")
    
    print("   [2/3] Presidio detector...")
    presidio_detector = PresidioDetector()
    presidio_detector.load_model()
    detectors.append(('Presidio', presidio_detector))
    detector_names.append('Presidio')
    print("   [OK] Presidio loaded")
    
    print("   [3/3] Regex detector...")
    regex_detector = RegexDetector()
    regex_detector.load_model()
    detectors.append(('Regex', regex_detector))
    detector_names.append('Regex')
    print(f"   [OK] Regex loaded ({len(regex_detector.patterns)} patterns)")
    
    print(f"[OK] {len(detectors)} detectors initialized successfully: {', '.join(detector_names)}")
    
    # Run detection with each detector
    print("\n[3/5] Running detection with each detector...")
    threshold = 0.6
    all_results = []
    
    for detector_name, detector in detectors:
        print(f"   Detecting with {detector_name} (threshold={threshold})...")
        start_time = time.time()
        entities = detector.detect_pii(content, threshold=threshold)
        elapsed_time = time.time() - start_time
        chars_per_second = len(content) / elapsed_time if elapsed_time > 0 else 0
        all_results.append((detector_name, entities))
        print(f"   [OK] {detector_name}: {len(entities)} entities in {elapsed_time:.2f}s")
        print(f"   [OK] {detector_name} throughput: {chars_per_second:.0f} chars/second")
    
    # Aggregate results
    print("\n[4/5] Aggregating results from all 3 detectors...")
    all_entities = []
    for _, entities in all_results:
        all_entities.extend(entities)
    print(f"[OK] Total aggregated entities (with duplicates): {len(all_entities)}")
    
    # Remove duplicates (same position and type)
    unique_entities = []
    seen = set()
    for entity in all_entities:
        key = (entity.start, entity.end, entity.pii_type)
        if key not in seen:
            seen.add(key)
            unique_entities.append(entity)
    
    print(f"[OK] Unique entities (after deduplication): {len(unique_entities)}")
    
    # Assert results are not empty
    assert len(unique_entities) > 0, "Expected to detect at least one PII entity"
    
    # Log comprehensive results
    print("\n[5/5] DETAILED AGGREGATED RESULTS:")
    print("=" * 80)
    
    # Group entities by PII type
    entities_by_type = defaultdict(list)
    for entity in unique_entities:
        entities_by_type[entity.pii_type].append(entity)
    
    # Display summary
    print(f"\n[SUMMARY]:")
    print(f"   Total unique entities: {len(unique_entities)}")
    print(f"   Total with duplicates: {len(all_entities)}")
    print(f"   Unique PII types found: {len(entities_by_type)}")
    print(f"   Detection threshold: {threshold}")
    print(f"   Detectors used: {len(detectors)} ({', '.join(detector_names)})")
    
    # Display contribution by detector
    print(f"\n[DETECTOR CONTRIBUTIONS]:")
    print("-" * 80)
    for detector_name, entities in all_results:
        print(f"   {detector_name:12} {len(entities):4} entities")
    print(f"   {'----':12}")
    print(f"   {'Total:':12} {len(all_entities):4} entities (before deduplication)")
    print(f"   {'Unique:':12} {len(unique_entities):4} entities (after deduplication)")
    
    # Display entities grouped by type
    print(f"\n[ENTITIES BY TYPE]:")
    print("-" * 80)
    
    for pii_type in sorted(entities_by_type.keys(), key=lambda x: str(x)):
        type_entities = entities_by_type[pii_type]
        print(f"\n[{pii_type}] ({len(type_entities)} occurrences)")
        print("   " + "-" * 76)
        
        for idx, entity in enumerate(type_entities, 1):
            # Extract context around the entity (20 chars before and after)
            start = max(0, entity.start - 20)
            end = min(len(content), entity.end + 20)
            context = content[start:end].replace('\n', ' ')
            
            # Determine source detector
            source = getattr(entity, 'source', 'UNKNOWN')
            
            print(f"   {idx}. Text: '{entity.text}'")
            print(f"      Score: {entity.score:.4f}")
            print(f"      Position: [{entity.start}:{entity.end}]")
            print(f"      Source: {source}")
            print(f"      Context: ...{context}...")
            print()
    
    # Display high-confidence detections (score >= 0.8)
    high_confidence = [e for e in unique_entities if e.score >= 0.8]
    if high_confidence:
        print("\n[HIGH CONFIDENCE DETECTIONS] (score >= 0.8):")
        print("-" * 80)
        for entity in sorted(high_confidence, key=lambda e: e.score, reverse=True):
            source = getattr(entity, 'source', 'UNKNOWN')
            print(f"   {entity.pii_type:30} | {entity.score:.4f} | {source:10} | '{entity.text}'")
    
    # Display statistics
    print("\n[STATISTICS]:")
    print("-" * 80)
    scores = [e.score for e in unique_entities]
    print(f"   Average score: {sum(scores) / len(scores):.4f}")
    print(f"   Min score: {min(scores):.4f}")
    print(f"   Max score: {max(scores):.4f}")
    print(f"   High confidence (>=0.8): {len(high_confidence)} ({len(high_confidence) / len(unique_entities) * 100:.1f}%)")
    
    # Display PII types distribution
    print("\n[PII TYPES DISTRIBUTION]:")
    print("-" * 80)
    for pii_type, type_entities in sorted(
        entities_by_type.items(), 
        key=lambda x: len(x[1]), 
        reverse=True
    ):
        count = len(type_entities)
        percentage = (count / len(unique_entities)) * 100
        bar = "#" * int(percentage / 2)
        print(f"   {pii_type:30} | {count:3} | {percentage:5.1f}% | {bar}")
    
    # Display source distribution
    print("\n[SOURCE DETECTOR DISTRIBUTION]:")
    print("-" * 80)
    by_source = defaultdict(int)
    for entity in unique_entities:
        source = getattr(entity, 'source', 'UNKNOWN')
        by_source[source] += 1
    
    for source, count in sorted(by_source.items(), key=lambda x: x[1], reverse=True):
        percentage = (count / len(unique_entities)) * 100
        bar = "#" * int(percentage / 2)
        print(f"   {source:15} | {count:3} | {percentage:5.1f}% | {bar}")
    
    print("\n" + "=" * 80)
    print("TEST COMPLETED SUCCESSFULLY [OK]")
    print("=" * 80 + "\n")


if __name__ == "__main__":
    # Allow running the tests directly for debugging
    print("Running all integration tests...\n")
    
    print("\n### TEST 1/4: GLiNER Detector ###")
    test_gliner_detector_with_text_file()
    
    print("\n### TEST 2/4: Presidio Detector ###")
    test_presidio_detector_with_text_file()
    
    print("\n### TEST 3/4: Custom Regex Detector ###")
    test_regex_detector_with_text_file()
    
    print("\n### TEST 4/4: Full Pipeline (Enabled Detectors) ###")
    test_full_pipeline_all_detectors_with_text_file()
    
    print("\n" + "=" * 80)
    print("ALL TESTS COMPLETED SUCCESSFULLY")
    print("=" * 80)

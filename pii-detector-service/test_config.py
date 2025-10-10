#!/usr/bin/env python
"""Test script to validate llm.toml configuration loading."""

from pii_detector.service.detector.models.detection_config import _load_llm_config, get_enabled_models
from pii_detector.service.detector.multi_detector import should_use_multi_detector, get_multi_model_ids_from_config

print("=" * 60)
print("Testing llm.toml configuration loading")
print("=" * 60)

# Test 1: Load configuration
print("\n1. Loading configuration from config/llm.toml...")
try:
    config = _load_llm_config()
    print("   [OK] Configuration loaded successfully!")
except Exception as e:
    print(f"   [FAIL] Failed to load configuration: {e}")
    exit(1)

# Test 2: Get enabled models
print("\n2. Getting enabled models...")
try:
    models = get_enabled_models(config)
    print(f"   [OK] Found {len(models)} enabled model(s):")
    for model in models:
        print(f"     - {model['name']}: {model['model_id']}")
        print(f"       Priority: {model['priority']}, Device: {model['device']}, Threshold: {model.get('threshold', 'default')}")
except Exception as e:
    print(f"   [FAIL] Failed to get enabled models: {e}")
    exit(1)

# Test 3: Check multi-detector status
print("\n3. Checking multi-detector status...")
try:
    use_multi = should_use_multi_detector()
    print(f"   [OK] Multi-detector enabled: {use_multi}")
    if use_multi:
        print("     -> Will use multi-model aggregation")
    else:
        print("     -> Will use single-model detection")
except Exception as e:
    print(f"   [FAIL] Failed to check multi-detector status: {e}")
    exit(1)

# Test 4: Get model IDs for multi-detector
print("\n4. Getting model IDs for detector initialization...")
try:
    model_ids = get_multi_model_ids_from_config()
    print(f"   [OK] Model IDs: {model_ids}")
except Exception as e:
    print(f"   [FAIL] Failed to get model IDs: {e}")
    exit(1)

# Test 5: Check detection settings
print("\n5. Checking detection settings...")
try:
    detection = config.get("detection", {})
    print(f"   [OK] Detection settings:")
    print(f"     - default_threshold: {detection.get('default_threshold')}")
    print(f"     - multi_detector_enabled: {detection.get('multi_detector_enabled')}")
    print(f"     - log_provenance: {detection.get('log_provenance')}")
    print(f"     - batch_size: {detection.get('batch_size')}")
except Exception as e:
    print(f"   [FAIL] Failed to check detection settings: {e}")
    exit(1)

print("\n" + "=" * 60)
print("All tests passed! Configuration is valid.")
print("=" * 60)

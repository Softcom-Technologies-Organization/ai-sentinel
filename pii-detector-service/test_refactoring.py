"""
Test simple pour vérifier que le refactoring n'a pas cassé les imports.
"""

print("Test des imports après refactoring...\n")

try:
    print("1. Import du package detector...")
    from pii_detector.service.detector import (
        PIIType,
        PIIEntity,
        DetectionConfig,
        PIIDetectionError,
        ModelNotLoadedError,
        ModelLoadError,
        APIKeyError,
        MemoryManager,
        ModelManager,
        EntityProcessor,
        PIIDetector,
        MultiModelPIIDetector,
    )
    print("   [OK] Tous les imports du package detector fonctionnent\n")
    
    print("2. Test création DetectionConfig...")
    config = DetectionConfig(model_id="test", device="cpu")
    print(f"   [OK] DetectionConfig cree: {config.model_id}\n")
    
    print("3. Test création PIIEntity...")
    entity = PIIEntity(
        text="test@example.com",
        pii_type="EMAIL",
        type_label="Email",
        start=0,
        end=16,
        score=0.9
    )
    print(f"   [OK] PIIEntity cree: {entity.text}\n")
    
    print("4. Test enum PIIType...")
    email_type = PIIType.EMAIL
    print(f"   [OK] PIIType.EMAIL: {email_type.value}\n")
    
    print("5. Test création MemoryManager...")
    mem_mgr = MemoryManager()
    print("   [OK] MemoryManager cree\n")
    
    print("6. Test création EntityProcessor...")
    processor = EntityProcessor()
    print(f"   [OK] EntityProcessor cree avec {len(processor.label_mapping)} mappings\n")
    
    print("=" * 60)
    print("[SUCCESS] TOUS LES TESTS PASSENT - LE REFACTORING EST REUSSI !")
    print("=" * 60)
    
except Exception as e:
    print(f"\n[ERREUR]: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

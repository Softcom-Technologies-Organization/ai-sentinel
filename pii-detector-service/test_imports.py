"""Script de test pour verifier que les imports de la nouvelle structure hexagonale fonctionnent."""

print("=== Test des imports de la structure hexagonale ===\n")

# Test 1: Domain Layer
print("[1] Test Domain Layer...")
try:
    from pii_detector.domain import (
        PIIEntity,
        PIIType,
        PIIDetectorProtocol,
        DetectionMerger,
        EntityProcessor,
    )
    print("   [OK] Domain imports OK")
    print(f"   - PIIEntity: {PIIEntity}")
    print(f"   - PIIType: {PIIType}")
    print(f"   - PIIDetectorProtocol: {PIIDetectorProtocol}")
except ImportError as e:
    print(f"   [FAIL] Domain imports FAILED: {e}")

# Test 2: Application Layer
print("\n[2] Test Application Layer...")
try:
    from pii_detector.application import (
        DetectionConfig,
        DetectorFactory,
        create_default_factory,
    )
    print("   [OK] Application imports OK")
    print(f"   - DetectionConfig: {DetectionConfig}")
    print(f"   - DetectorFactory: {DetectorFactory}")
except ImportError as e:
    print(f"   [FAIL] Application imports FAILED: {e}")

# Test 3: Infrastructure Layer (ne devrait rien exporter)
print("\n[3] Test Infrastructure Layer (doit etre PRIVATE)...")
try:
    from pii_detector import infrastructure
    exports = getattr(infrastructure, '__all__', [])
    if len(exports) == 0:
        print("   [OK] Infrastructure is correctly PRIVATE (no public exports)")
    else:
        print(f"   [WARNING] Infrastructure exports: {exports}")
except ImportError as e:
    print(f"   [FAIL] Infrastructure import FAILED: {e}")

print("\n=== Tous les tests d'import termines ===")

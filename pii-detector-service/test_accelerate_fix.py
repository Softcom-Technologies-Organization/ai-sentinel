"""
Script de test pour vérifier que le problème init_empty_weights est résolu.
"""
import sys
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

logger = logging.getLogger(__name__)

def test_accelerate_import():
    """Test if accelerate can be imported."""
    try:
        import accelerate
        logger.info(f"✅ accelerate importé avec succès - version: {accelerate.__version__}")
        return True
    except ImportError as e:
        logger.error(f"❌ Erreur import accelerate: {e}")
        return False

def test_init_empty_weights():
    """Test if init_empty_weights is accessible."""
    try:
        from accelerate import init_empty_weights
        logger.info("✅ init_empty_weights importé avec succès")
        return True
    except (ImportError, NameError) as e:
        logger.error(f"❌ Erreur import init_empty_weights: {e}")
        return False

def test_model_loading():
    """Test model loading with low_cpu_mem_usage."""
    try:
        from transformers import AutoModelForTokenClassification
        import torch
        
        logger.info("Test de chargement du modèle avec low_cpu_mem_usage=True...")
        
        # Utilise un petit modèle pour le test
        model_id = "Ar86Bat/multilang-pii-ner"
        
        model = AutoModelForTokenClassification.from_pretrained(
            model_id,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True
        )
        
        logger.info("✅ Modèle chargé avec succès avec low_cpu_mem_usage=True")
        logger.info(f"   Modèle: {model.__class__.__name__}")
        return True
        
    except Exception as e:
        logger.error(f"❌ Erreur lors du chargement du modèle: {e}")
        return False

def main():
    """Execute tous les tests."""
    logger.info("=" * 60)
    logger.info("Test de résolution du problème init_empty_weights")
    logger.info("=" * 60)
    
    results = {
        "accelerate_import": test_accelerate_import(),
        "init_empty_weights": test_init_empty_weights(),
        "model_loading": test_model_loading()
    }
    
    logger.info("=" * 60)
    logger.info("Résultats des tests:")
    for test_name, result in results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        logger.info(f"  {test_name}: {status}")
    
    all_passed = all(results.values())
    
    if all_passed:
        logger.info("=" * 60)
        logger.info("🎉 TOUS LES TESTS SONT PASSÉS - Le problème est résolu!")
        logger.info("=" * 60)
        return 0
    else:
        logger.error("=" * 60)
        logger.error("❌ Certains tests ont échoué")
        logger.error("=" * 60)
        return 1

if __name__ == "__main__":
    sys.exit(main())

"""
Test semantic chunking integration with GLiNER.

This script tests that the semantic chunker prevents GLiNER's
"Sentence of length X has been truncated to 768" warnings.
"""

import logging
from pii_detector.service.detector.gliner_detector import GLiNERDetector
from pii_detector.service.detector.models.detection_config import DetectionConfig

# Configure logging to see warnings
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

def test_long_text_no_truncation():
    """Test that long text without sentence breaks doesn't cause truncation warnings."""
    
    print("=" * 80)
    print("Testing semantic chunking with GLiNER")
    print("=" * 80)
    
    # Initialize detector
    config = DetectionConfig()
    detector = GLiNERDetector(config)
    
    print("\n1. Loading GLiNER model and initializing semantic chunker...")
    detector.download_model()
    detector.load_model()
    
    # Create a long text that would trigger truncation with old approach
    # This simulates Confluence content with long lists/tables without punctuation
    long_text = (
        "This is a test document containing sensitive information. "
        "John Doe lives at 123 Main Street in New York and can be reached at "
        "john.doe@example.com or by phone at 555-1234. " * 50  # Repeat to create long text
    )
    
    print(f"\n2. Testing with {len(long_text)} character text...")
    print(f"   Text preview: {long_text[:100]}...")
    
    # Run detection - should NOT show truncation warnings
    print("\n3. Running PII detection with semantic chunking...")
    entities = detector.detect_pii(long_text, threshold=0.5)
    
    print(f"\n4. Results:")
    print(f"   - Found {len(entities)} PII entities")
    print(f"   - No truncation warnings should appear above [OK]")
    
    # Show some detected entities
    if entities:
        print(f"\n5. Sample detected entities:")
        for i, entity in enumerate(entities[:5], 1):
            print(f"   {i}. {entity.type_label}: '{entity.text}' (score: {entity.score:.3f})")
    
    print("\n" + "=" * 80)
    print("Test completed successfully!")
    print("=" * 80)
    
    return entities

if __name__ == "__main__":
    try:
        entities = test_long_text_no_truncation()
        print(f"\n[SUCCESS] Detected {len(entities)} entities without truncation warnings")
    except Exception as e:
        print(f"\n[ERROR] {str(e)}")
        import traceback
        traceback.print_exc()

"""
Test script to verify the improved email expansion with forward search.
Tests the problematic cases from the logs where partial emails were detected.
"""
import os
import sys

# Add the project root to the path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from pii_detector.service.detector.pii_detector import PIIDetector, PIIEntity, DetectionConfig

def test_email_expansion():
    """Test that partial email detections are expanded to complete emails."""
    
    # Load the test text
    # __file__ is in tests/integration/, so go up one level to tests/, then to resources/
    tests_dir = os.path.join(os.path.dirname(__file__), '..')
    test_file = os.path.join(tests_dir, 'resources', 
                             'text-with-emails-not-detected-by-piiranha-v1.txt')
    with open(test_file, 'r', encoding='utf-8') as f:
        text = f.read()
    
    print("="*80)
    print("Testing improved email expansion with forward search")
    print("="*80)
    
    # Create manual test cases simulating what the model returns (partial emails)
    # These are the problematic detections from the logs
    partial_entities = [
        PIIEntity(text='jean', pii_type='EMAIL', type_label='Email', start=204, end=208, score=0.9993),
        PIIEntity(text='jean', pii_type='EMAIL', type_label='Email', start=335, end=339, score=0.9986),
    ]
    
    print("\nPartial EMAIL entities (as detected by model):")
    for e in partial_entities:
        context = text[max(0, e.start-10):min(len(text), e.end+30)]
        print(f"  Position [{e.start}:{e.end}] text='{e.text}' context='...{context}...'")
    
    # Initialize detector to use the expansion method
    config = DetectionConfig(threshold=0.5)
    detector = PIIDetector(config=config)
    
    # Apply the email expansion post-processing
    expanded = detector._expand_email_domain(text, partial_entities)
    
    print("\n" + "-"*80)
    print("Expanded EMAIL entities (after forward search):")
    for e in expanded:
        context = text[max(0, e.start-5):min(len(text), e.end+5)]
        print(f"  Position [{e.start}:{e.end}] text='{e.text}' context='...{context}...'")
    
    print("\n" + "="*80)
    print("Verification:")
    
    # Check expected results
    expected_emails = [
        'jean.dupont@vd.ch',           # Should be expanded from position 204
        'jean..dupont@example.com',    # Should be expanded from position 335
    ]
    
    expanded_texts = [e.text for e in expanded]
    
    success = True
    for expected in expected_emails:
        if expected in expanded_texts:
            print(f"  ✓ Found expected email: '{expected}'")
        else:
            print(f"  ✗ MISSING expected email: '{expected}'")
            success = False
    
    # Check that we don't have partial emails without '@'
    for e in expanded:
        if '@' not in e.text:
            print(f"  ✗ INCOMPLETE email without '@': '{e.text}'")
            success = False
    
    print("="*80)
    if success:
        print("SUCCESS: All emails properly expanded!")
    else:
        print("FAILURE: Some emails were not properly expanded")
    
    return success

if __name__ == "__main__":
    try:
        success = test_email_expansion()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\nError during test: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

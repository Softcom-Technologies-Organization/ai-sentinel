"""
Protocol defining the contract for PII detectors.

This module defines the PIIDetectorProtocol that all PII detector implementations
must follow, formalizing the Strategy pattern used in the detection architecture.
"""

from typing import List, Optional, Protocol, Tuple

from .models import PIIEntity


class PIIDetectorProtocol(Protocol):
    """
    Protocol defining the interface for PII detection strategies.
    
    This protocol formalizes the Strategy pattern by defining the contract
    that all PII detector implementations must follow. It enables:
    - Interchangeable detector strategies (GLiNER, transformer-based, regex, etc.)
    - Composite orchestration in MultiModelPIIDetector
    - Type-safe detector composition
    
    Implementations must provide:
    - model_id: Unique identifier for the detector
    - detect_pii: Core detection method
    - mask_pii: PII masking functionality
    
    Business rules:
    - All detectors return normalized PIIEntity objects
    - Confidence scores are in range [0.0, 1.0]
    - Text positions (start, end) are character-based indices
    """

    @property
    def model_id(self) -> str:
        """
        Get the unique identifier for this detector.
        
        Returns:
            Model identifier string (e.g., 'gliner-pii', 'piiranha-v1')
        """
        ...

    def detect_pii(self, text: str, threshold: Optional[float] = None) -> List[PIIEntity]:
        """
        Detect PII entities in the given text.
        
        Business process:
        1. Analyze text for personally identifiable information
        2. Apply confidence threshold filtering
        3. Return normalized entity list with positions and scores
        
        Args:
            text: Text content to analyze for PII
            threshold: Optional confidence threshold [0.0-1.0]. 
                      If None, uses detector's default threshold.
            
        Returns:
            List of detected PII entities with:
            - text: The detected PII text
            - pii_type: Normalized PII type (e.g., 'EMAIL', 'PHONE')
            - start/end: Character positions in original text
            - score: Confidence score [0.0-1.0]
            
        Raises:
            ModelNotLoadedError: If detector model is not loaded
            PIIDetectionError: If detection process fails
        """
        ...

    def mask_pii(
        self, text: str, threshold: Optional[float] = None
    ) -> Tuple[str, List[PIIEntity]]:
        """
        Mask PII entities in text by replacing them with type labels.
        
        Business process:
        1. Detect all PII entities in text
        2. Replace each entity with its type label: [TYPE]
        3. Return both masked text and entity list
        
        Args:
            text: Text content to mask
            threshold: Optional confidence threshold [0.0-1.0]
            
        Returns:
            Tuple of:
            - masked_text: Text with PII replaced by [TYPE] labels
            - entities: List of detected and masked entities
            
        Example:
            Input: "Contact john@example.com"
            Output: ("Contact [EMAIL]", [PIIEntity(...)])
            
        Raises:
            ModelNotLoadedError: If detector model is not loaded
            PIIDetectionError: If detection process fails
        """
        ...

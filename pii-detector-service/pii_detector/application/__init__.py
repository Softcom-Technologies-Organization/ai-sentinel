"""
Application layer for PII detection.
Exports use cases, orchestration components, factories, and application configuration.

This layer orchestrates domain logic and coordinates between domain and infrastructure.
"""

# Import and re-export application configuration
from .config.detection_policy import DetectionConfig

# Import and re-export factories
from .factory.detector_factory import DetectorFactory, create_default_factory

# Import and re-export orchestration components
from .orchestration.composite_detector import CompositePIIDetector
from .orchestration.multi_detector import MultiModelPIIDetector

__all__ = [
    # ===== CONFIGURATION =====
    "DetectionConfig",
    
    # ===== FACTORIES =====
    "DetectorFactory",
    "create_default_factory",
    
    # ===== ORCHESTRATION =====
    "CompositePIIDetector",
    "MultiModelPIIDetector",
]

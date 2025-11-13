"""
Application layer for PII detection.
Exports use cases, orchestration components, factories, and application configuration.

This layer orchestrates domain logic and coordinates between domain and infrastructure.
"""

# Import and re-export application configuration
from .config.detection_policy import DetectionConfig

# NOTE: DetectorFactory and orchestration components are NOT imported here to avoid circular imports
# Import them directly from their modules when needed:
#   from pii_detector.application.factory.detector_factory import DetectorFactory, create_default_factory
#   from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
#   from pii_detector.application.orchestration.multi_detector import MultiModelPIIDetector

__all__ = [
    # ===== CONFIGURATION =====
    "DetectionConfig",
]

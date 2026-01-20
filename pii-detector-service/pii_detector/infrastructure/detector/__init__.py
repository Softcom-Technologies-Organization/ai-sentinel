"""Infrastructure - Detector Adapters.

⚠️ PRIVATE IMPLEMENTATION - Not exported by default.

This package contains concrete detector implementations:
- GLiNER detector
- Presidio detector  
- Regex detector
- PiiraNha detector

These are infrastructure concerns and should be accessed through:
- DetectorFactory (application layer)
- PIIDetectorProtocol (domain layer)

DO NOT import directly from this package in domain or application layers.
"""

__all__ = []

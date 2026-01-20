"""Infrastructure - Model Management.

⚠️ PRIVATE IMPLEMENTATION - Not exported by default.

This package contains technical concerns for model lifecycle:
- Model cache (memory management)
- Memory manager (resource optimization)
- Model loader/unloader

These are infrastructure concerns and should be accessed through:
- DetectorFactory (application layer)
- Detector implementations (infrastructure layer)

DO NOT import directly from this package in domain or application layers.
"""

__all__ = []

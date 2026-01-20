"""
Infrastructure layer - Technical implementations.

⚠️ Most components here are PRIVATE (not exported by default).
Only export what's needed for backward compatibility or specific use cases.

The infrastructure layer contains all technical implementations:
- Adapters (IN: API/gRPC, OUT: External services)
- Concrete detector implementations
- Model management
- Text processing utilities
"""

# Intentionally minimal exports - infrastructure details should not be exposed
# Use application layer factories and ports instead
__all__ = []

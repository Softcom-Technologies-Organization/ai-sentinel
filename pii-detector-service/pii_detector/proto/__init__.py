"""
Proto package for PII detection service.

This package contains the Protocol Buffers definitions and generated code for the PII detection service.
"""

# Import and expose generated Protocol Buffer modules
try:
    from .generated import pii_detection_pb2
    from .generated import pii_detection_pb2_grpc

    # Expose them at the package level
    __all__ = ['pii_detection_pb2', 'pii_detection_pb2_grpc']
except ImportError:
    # If imports fail, the modules might not be generated yet
    pii_detection_pb2 = None
    pii_detection_pb2_grpc = None
    __all__ = []

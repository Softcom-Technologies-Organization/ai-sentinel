"""
Proto package for PII detection service.

This package contains the Protocol Buffers definitions and generated code for the PII detection service.
"""

# Import and expose generated Protocol Buffer modules
try:
    import sys
    import os

    # Add the proto directory to the Python path
    proto_dir = os.path.dirname(os.path.abspath(__file__))
    if proto_dir not in sys.path:
        sys.path.insert(0, proto_dir)

    # Import the modules directly
    import pii_detection_pb2
    import pii_detection_pb2_grpc

    # Expose them at the package level
    __all__ = ['pii_detection_pb2', 'pii_detection_pb2_grpc']
except ImportError:
    # If imports fail, the modules might not be generated yet
    pass

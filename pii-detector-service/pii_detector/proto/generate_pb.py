"""
Generate gRPC Python code from proto file.

This script generates the Python code for the gRPC service defined in the proto file.
"""

import logging
import os
import sys
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def generate_grpc_code():
    """Generate Python code from proto file."""
    try:
        # Get the directory of this script
        script_dir = Path(__file__).parent.absolute()

        # Navigate to project root (3 levels up from script location)
        # pii_detector/proto -> pii_detector -> pii-detector-service -> ai-sentinel
        project_root = script_dir.parent.parent.parent

        # Proto file path (in proto folder at project root)
        proto_dir = project_root / "proto"
        proto_file = proto_dir / "pii_detection.proto"

        if not proto_file.exists():
            logger.error(f"Proto file not found: {proto_file}")
            sys.exit(1)

        # Output directory (generated folder inside pii_detector/proto)
        output_dir = script_dir / "generated"
        
        # Create generated directory if it doesn't exist
        output_dir.mkdir(parents=True, exist_ok=True)
        logger.info(f"Output directory: {output_dir}")

        # Command to generate Python code
        cmd = f'python -m grpc_tools.protoc -I"{proto_dir}" --python_out="{output_dir}" --grpc_python_out="{output_dir}" "{proto_file}"'

        logger.info(f"Generating gRPC code from {proto_file}")
        logger.info(f"Command: {cmd}")

        # Execute the command
        result = os.system(cmd)

        if result != 0:
            logger.error("Failed to generate gRPC code")
            sys.exit(1)

        logger.info("gRPC code generated successfully")
        
        # Fix imports in generated gRPC file
        # grpc_tools.protoc generates absolute imports like: import pii_detection_pb2
        # We need to change them to: from pii_detector.proto.generated import pii_detection_pb2
        grpc_file = output_dir / "pii_detection_pb2_grpc.py"
        if grpc_file.exists():
            logger.info(f"Fixing imports in {grpc_file}")
            
            with open(grpc_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Replace absolute import with relative import within the package
            original_import = "import pii_detection_pb2 as pii__detection__pb2"
            fixed_import = "from pii_detector.proto.generated import pii_detection_pb2 as pii__detection__pb2"
            
            if original_import in content:
                content = content.replace(original_import, fixed_import)
                
                with open(grpc_file, 'w', encoding='utf-8') as f:
                    f.write(content)
                
                logger.info("Import fixed successfully")
            else:
                logger.warning(f"Expected import statement not found in {grpc_file}")
        else:
            logger.warning(f"gRPC file not found: {grpc_file}")

        # Create __init__.py file if it doesn't exist
        init_file = output_dir / "__init__.py"
        if not init_file.exists():
            with open(init_file, 'w') as f:
                f.write('"""Generated proto package for PII detection service."""\n')
            logger.info(f"Created {init_file}")

    except Exception as e:
        logger.error(f"Error generating gRPC code: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    generate_grpc_code()

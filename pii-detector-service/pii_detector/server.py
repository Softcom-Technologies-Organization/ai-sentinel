"""
PII Detection gRPC Server.

This script starts the gRPC server for the PII detection service.

Note: Before running this script, you need to:
1. Install the required dependencies: pip install -r requirements.txt
2. Generate the gRPC code: python -m proto.generate_pb
3. Set the HUGGING_FACE_API_KEY environment variable
"""

import os
import sys
import logging
import argparse
from pathlib import Path

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.absolute()))

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def verify_dependencies(debug: bool = False) -> None:
    """Preflight check for critical runtime dependencies.
    
    This validates that the current Python interpreter can import key libraries
    (e.g., transformers and its `pipeline` symbol). It logs useful diagnostics
    (versions, locations) and raises ImportError with actionable guidance when
    something is missing or shadowed by stale caches.
    """
    try:
        import importlib
        transformers = importlib.import_module("transformers")
        # Explicitly fetch the pipeline symbol to ensure it is available
        from transformers import pipeline as hf_pipeline  # noqa: F401

        if debug:
            logger.debug(f"Transformers version: {getattr(transformers, '__version__', 'unknown')}")
            logger.debug(f"Transformers module file: {getattr(transformers, '__file__', 'unknown')}")
    except Exception as e:
        logger.error("Dependency preflight failed while importing transformers/pipeline.")
        logger.error(f"Details: {e}")
        logger.info("Troubleshooting steps (Windows PowerShell):")
        logger.info("  1) Ensure you're using the intended interpreter (venv vs system Python).")
        logger.info("  2) Reinstall deps: py -m pip install -U pip && py -m pip install -r requirements.txt")
        logger.info("  3) Clean caches in repo: .\\scripts\\clean.ps1 (provided in this repo)")
        logger.info("  4) If problem persists, clear pip cache: py -m pip cache purge")
        logger.info("  5) Check if a local 'pipeline.py' shadows imports (none expected in this repo).")
        raise ImportError("Failed to import transformers/pipeline. Please (re)install requirements and clean caches.") from e


# Function to set logging level
def set_logging_level(debug=False):
    """Set the logging level based on the debug flag."""
    if debug:
        logger.setLevel(logging.DEBUG)
        # Also set the root logger to DEBUG
        logging.getLogger().setLevel(logging.DEBUG)
        logger.debug("Debug logging enabled")

        # Log system information for debugging
        logger.debug("System information:")
        logger.debug(f"  Python version: {sys.version}")
        logger.debug(f"  Platform: {sys.platform}")
        logger.debug(f"  Executable: {sys.executable}")
        logger.debug(f"  Current working directory: {os.getcwd()}")
        logger.debug(f"  Process ID: {os.getpid()}")

        # Log Python path
        logger.debug("Python path:")
        for p in sys.path:
            logger.debug(f"  {p}")

        # Check if proto directory exists
        proto_dir = Path(__file__).parent.absolute() / "proto"
        logger.debug(f"Proto directory: {proto_dir}")
        logger.debug(f"Proto directory exists: {proto_dir.exists()}")

        # Check if generated files exist
        pb2_file = proto_dir / "pii_detection_pb2.py"
        pb2_grpc_file = proto_dir / "pii_detection_pb2_grpc.py"
        logger.debug(f"pii_detection_pb2.py exists: {pb2_file.exists()}")
        logger.debug(f"pii_detection_pb2_grpc.py exists: {pb2_grpc_file.exists()}")

        # Log environment variable count
        logger.debug(f"  Environment variables count: {len(os.environ)}")


def check_environment(api_key=None):
    """Check if the environment is properly set up."""
    from config import get_config
    
    # Get API key from centralized config or provided argument
    try:
        config = get_config()
        env_api_key = config.model.huggingface_api_key
    except ValueError:
        # Config validation failed, API key not set
        env_api_key = None
    
    hugging_face_api_key = api_key or env_api_key

    # Log the API key status for debugging
    if env_api_key:
        masked_key = env_api_key[:3] + "..." if len(env_api_key) > 3 else "***"
        logger.debug(f"Found HUGGING_FACE_API_KEY in configuration: {masked_key}")
    else:
        logger.debug("HUGGING_FACE_API_KEY not found in configuration")

    if not hugging_face_api_key:
        logger.error("HUGGING_FACE_API_KEY environment variable is not set")
        logger.error("Please set it before running the server or provide it using --api-key")

        # Provide instructions for setting environment variables
        if sys.platform == "win32":
            logger.info("To set environment variables in Windows:")
            logger.info("  1. Temporary (current session only):")
            logger.info("     set HUGGING_FACE_API_KEY=your_api_key")
            logger.info("  2. Permanent (user variables):")
            logger.info("     setx HUGGING_FACE_API_KEY your_api_key")
            logger.info("  3. Permanent (system variables, requires admin):")
            logger.info("     setx /M HUGGING_FACE_API_KEY your_api_key")
            logger.info("  4. Or use the --api-key command-line argument:")
            logger.info("     python server.py --api-key your_api_key")
            logger.info("Note: After setting environment variables, you may need to restart your command prompt or IDE.")

        # Print environment variables for debugging
        logger.debug("Environment variables visible to Python:")
        for key, value in os.environ.items():
            # Mask sensitive values
            if "key" in key.lower() or "token" in key.lower() or "secret" in key.lower() or "password" in key.lower():
                masked_value = value[:3] + "..." if value else None
                logger.debug(f"  {key}: {masked_value}")
            else:
                logger.debug(f"  {key}: {value}")

        sys.exit(1)

    # Set the environment variable if it was provided via command line
    if api_key:
        os.environ["HUGGING_FACE_API_KEY"] = api_key
        logger.info("Using API key provided via command line")

    # Check if the gRPC code has been generated
    try:
        logger.debug("Attempting to import gRPC code...")

        # Check if proto directory exists
        proto_dir = Path(__file__).parent.absolute() / "proto"
        logger.debug(f"Proto directory: {proto_dir}")
        logger.debug(f"Proto directory exists: {proto_dir.exists()}")

        # Check if generated files exist
        pb2_file = proto_dir / "pii_detection_pb2.py"
        pb2_grpc_file = proto_dir / "pii_detection_pb2_grpc.py"
        logger.debug(f"pii_detection_pb2.py exists: {pb2_file.exists()}")
        logger.debug(f"pii_detection_pb2_grpc.py exists: {pb2_grpc_file.exists()}")

        # Try different import approaches
        import_success = False

        # Approach 1: Try the normal import
        if not import_success:
            try:
                logger.debug("Attempting normal import...")
                from proto import pii_detection_pb2
                from proto import pii_detection_pb2_grpc
                logger.debug("Successfully imported gRPC code using normal import")
                import_success = True
            except ImportError as e:
                logger.debug(f"Normal import failed: {str(e)}")

        # Approach 2: Try to import using sys.path manipulation
        if not import_success:
            try:
                logger.debug("Attempting import using sys.path manipulation...")
                # Add proto directory to sys.path if it's not already there
                proto_dir_str = str(proto_dir)
                if proto_dir_str not in sys.path:
                    sys.path.insert(0, proto_dir_str)
                    logger.debug(f"Added {proto_dir_str} to sys.path")

                # Try importing
                import pii_detection_pb2
                import pii_detection_pb2_grpc
                logger.debug("Successfully imported gRPC code using sys.path manipulation")
                import_success = True
            except ImportError as e:
                logger.debug(f"Import using sys.path manipulation failed: {str(e)}")

        # Approach 3: Try to import directly from the file path using importlib
        if not import_success and pb2_file.exists() and pb2_grpc_file.exists():
            try:
                logger.debug("Attempting import using importlib...")
                import importlib.util

                # Import pii_detection_pb2
                pb2_name = "pii_detection_pb2"
                spec = importlib.util.spec_from_file_location(pb2_name, str(pb2_file))
                pii_detection_pb2 = importlib.util.module_from_spec(spec)
                sys.modules[pb2_name] = pii_detection_pb2  # Add to sys.modules
                spec.loader.exec_module(pii_detection_pb2)

                # Import pii_detection_pb2_grpc
                pb2_grpc_name = "pii_detection_pb2_grpc"
                spec = importlib.util.spec_from_file_location(pb2_grpc_name, str(pb2_grpc_file))
                pii_detection_pb2_grpc = importlib.util.module_from_spec(spec)
                sys.modules[pb2_grpc_name] = pii_detection_pb2_grpc  # Add to sys.modules
                spec.loader.exec_module(pii_detection_pb2_grpc)

                logger.debug("Successfully imported gRPC code using importlib")
                import_success = True
            except Exception as e:
                logger.debug(f"Import using importlib failed: {str(e)}")

        # Approach 4: Try to copy the files to a location in sys.path
        if not import_success:
            try:
                logger.debug("Attempting import by copying files...")
                import shutil

                # Create a temporary directory in the current directory
                temp_dir = Path(__file__).parent.absolute() / "temp_modules"
                temp_dir.mkdir(exist_ok=True)
                logger.debug(f"Created temporary directory: {temp_dir}")

                # Copy the files to the temporary directory
                shutil.copy(str(pb2_file), str(temp_dir / "pii_detection_pb2.py"))
                shutil.copy(str(pb2_grpc_file), str(temp_dir / "pii_detection_pb2_grpc.py"))
                logger.debug("Copied files to temporary directory")

                # Add the temporary directory to sys.path
                temp_dir_str = str(temp_dir)
                if temp_dir_str not in sys.path:
                    sys.path.insert(0, temp_dir_str)
                    logger.debug(f"Added {temp_dir_str} to sys.path")

                # Try importing
                import pii_detection_pb2
                import pii_detection_pb2_grpc
                logger.debug("Successfully imported gRPC code by copying files")
                import_success = True
            except Exception as e:
                logger.debug(f"Import by copying files failed: {str(e)}")

        # If all approaches failed, raise an error
        if not import_success:
            raise ImportError("All import approaches failed")

    except Exception as e:
        logger.error(f"gRPC code not found: {str(e)}")
        logger.error("Please run: python -m proto.generate_pb")
        sys.exit(1)


def main():
    """Start the gRPC server."""
    parser = argparse.ArgumentParser(description="PII Detection gRPC Server")
    parser.add_argument(
        "--port", type=int, default=50051,
        help="Port to listen on (default: 50051)"
    )
    parser.add_argument(
        "--workers", type=int, default=10,
        help="Maximum number of worker threads (default: 10)"
    )
    parser.add_argument(
        "--api-key", type=str,
        help="Hugging Face API key (overrides HUGGING_FACE_API_KEY environment variable)"
    )
    parser.add_argument(
        "--debug", action="store_true",
        help="Enable debug logging"
    )
    args = parser.parse_args()

    # Set logging level based on debug flag
    set_logging_level(args.debug)

    # Preflight dependency check (helps catch 'pipeline' import issues early)
    try:
        verify_dependencies(debug=args.debug)
    except ImportError as e:
        logger.error(str(e))
        sys.exit(1)

    # Check if the environment is properly set up
    check_environment(api_key=args.api_key)

    # Import the server module
    try:
        from service.server.pii_service import serve

        # Start the server
        server = serve(port=args.port, max_workers=args.workers)

        logger.info(f"Server started on port {args.port} with {args.workers} workers")
        logger.info("Press Ctrl+C to stop the server")

        # Keep the server running until interrupted
        try:
            server.wait_for_termination()
        except KeyboardInterrupt:
            logger.info("Server shutting down...")

    except ImportError as e:
        logger.error(f"Error importing server module: {str(e)}")
        msg = str(e).lower()
        if 'pipeline' in msg:
            logger.error("Hint: This often indicates an issue with the Hugging Face 'transformers' installation or a shadowed module named 'pipeline'.")
            logger.info("Try: .\\scripts\\clean.ps1; then reinstall deps: py -m pip install -U pip && py -m pip install -r requirements.txt")
            logger.info("Also ensure you're using the intended interpreter (virtualenv) and no local pipeline.py shadows imports.")
        else:
            logger.error("Please make sure all dependencies are installed (py -m pip install -r requirements.txt)")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Error starting server: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()

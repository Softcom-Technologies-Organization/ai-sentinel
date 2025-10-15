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

GRPC_PROTO_PII_DETECTION_FILE = "pii_detection_pb2_grpc.py"

PII_DETECTION_PROTO_FILE_NAME = "pii_detection_pb2.py"

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.parent.absolute()))

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


def _log_system_information():
    """Log system information for debugging."""
    logger.debug("System information:")
    logger.debug(f"  Python version: {sys.version}")
    logger.debug(f"  Platform: {sys.platform}")
    logger.debug(f"  Executable: {sys.executable}")
    logger.debug(f"  Current working directory: {os.getcwd()}")
    logger.debug(f"  Process ID: {os.getpid()}")
    logger.debug(f"  Environment variables count: {len(os.environ)}")


def _log_python_path():
    """Log Python path for debugging."""
    logger.debug("Python path:")
    for path in sys.path:
        logger.debug(f"  {path}")


def _log_proto_directory_status():
    """Log proto directory and generated files status."""
    proto_dir = Path(__file__).parent.absolute() / "proto"
    logger.debug(f"Proto directory: {proto_dir}")
    logger.debug(f"Proto directory exists: {proto_dir.exists()}")
    
def _enable_debug_logging():
    """Enable debug logging and log diagnostic information."""
    logger.setLevel(logging.DEBUG)
    logging.getLogger().setLevel(logging.DEBUG)
    logger.debug("Debug logging enabled")
    
    _log_system_information()
    _log_python_path()
    _log_proto_directory_status()


def set_logging_level(debug=False):
    """Set the logging level based on the debug flag."""
    if debug:
        _enable_debug_logging()


def _get_api_key_from_config():
    """Get API key from configuration."""
    from pii_detector.config import get_config
    try:
        config = get_config()
        return config.model.huggingface_api_key
    except ValueError:
        return None


def _log_api_key_status(env_api_key):
    """Log API key status for debugging."""
    if env_api_key:
        masked_key = env_api_key[:3] + "..." if len(env_api_key) > 3 else "***"
        logger.debug(f"Found HUGGING_FACE_API_KEY in configuration: {masked_key}")
    else:
        logger.debug("HUGGING_FACE_API_KEY not found in configuration")


def _log_windows_env_setup_instructions():
    """Log instructions for setting environment variables on Windows."""
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


def _log_environment_variables():
    """Log environment variables for debugging, masking sensitive values."""
    logger.debug("Environment variables visible to Python:")
    for key, value in os.environ.items():
        if any(term in key.lower() for term in ["key", "token", "secret", "password"]):
            masked_value = value[:3] + "..." if value else None
            logger.debug(f"  {key}: {masked_value}")
        else:
            logger.debug(f"  {key}: {value}")


def _validate_api_key(api_key, env_api_key):
    """Validate and set API key, exit if not available."""
    hugging_face_api_key = api_key or env_api_key
    
    if not hugging_face_api_key:
        logger.error("HUGGING_FACE_API_KEY environment variable is not set")
        logger.error("Please set it before running the server or provide it using --api-key")
        
        if sys.platform == "win32":
            _log_windows_env_setup_instructions()
        
        _log_environment_variables()
        sys.exit(1)
    
    if api_key:
        os.environ["HUGGING_FACE_API_KEY"] = api_key
        logger.info("Using API key provided via command line")


def _try_normal_import():
    """Try to import gRPC modules using normal import."""
    try:
        logger.debug("Attempting normal import...")
        from proto.generated import pii_detection_pb2  # noqa: F401
        from proto.generated import pii_detection_pb2_grpc  # noqa: F401
        logger.debug("Successfully imported gRPC code using normal import")
        return True
    except ImportError as e:
        logger.debug(f"Normal import failed: {str(e)}")
        return False


def _try_syspath_import(proto_dir):
    """Try to import gRPC modules using sys.path manipulation."""
    try:
        logger.debug("Attempting import using sys.path manipulation...")
        proto_dir_str = str(proto_dir)
        if proto_dir_str not in sys.path:
            sys.path.insert(0, proto_dir_str)
            logger.debug(f"Added {proto_dir_str} to sys.path")
        
        import pii_detection_pb2  # noqa: F401
        import pii_detection_pb2_grpc  # noqa: F401
        logger.debug("Successfully imported gRPC code using sys.path manipulation")
        return True
    except ImportError as e:
        logger.debug(f"Import using sys.path manipulation failed: {str(e)}")
        return False


def _try_importlib_import(pb2_file, pb2_grpc_file):
    """Try to import gRPC modules using importlib."""
    try:
        logger.debug("Attempting import using importlib...")
        import importlib.util
        
        pb2_name = "pii_detection_pb2"
        spec = importlib.util.spec_from_file_location(pb2_name, str(pb2_file))
        pii_detection_pb2 = importlib.util.module_from_spec(spec)
        sys.modules[pb2_name] = pii_detection_pb2
        spec.loader.exec_module(pii_detection_pb2)
        
        pb2_grpc_name = "pii_detection_pb2_grpc"
        spec = importlib.util.spec_from_file_location(pb2_grpc_name, str(pb2_grpc_file))
        pii_detection_pb2_grpc = importlib.util.module_from_spec(spec)
        sys.modules[pb2_grpc_name] = pii_detection_pb2_grpc
        spec.loader.exec_module(pii_detection_pb2_grpc)
        
        logger.debug("Successfully imported gRPC code using importlib")
        return True
    except Exception as e:
        logger.debug(f"Import using importlib failed: {str(e)}")
        return False


def _try_copy_import(pb2_file, pb2_grpc_file):
    """Try to import gRPC modules by copying files."""
    try:
        logger.debug("Attempting import by copying files...")
        import shutil
        
        temp_dir = Path(__file__).parent.absolute() / "temp_modules"
        temp_dir.mkdir(exist_ok=True)
        logger.debug(f"Created temporary directory: {temp_dir}")
        
        shutil.copy(str(pb2_file), str(temp_dir / PII_DETECTION_PROTO_FILE_NAME))
        shutil.copy(str(pb2_grpc_file), str(temp_dir / GRPC_PROTO_PII_DETECTION_FILE))
        logger.debug("Copied files to temporary directory")
        
        temp_dir_str = str(temp_dir)
        if temp_dir_str not in sys.path:
            sys.path.insert(0, temp_dir_str)
            logger.debug(f"Added {temp_dir_str} to sys.path")
        
        import pii_detection_pb2  # noqa: F401
        import pii_detection_pb2_grpc  # noqa: F401
        logger.debug("Successfully imported gRPC code by copying files")
        return True
    except Exception as e:
        logger.debug(f"Import by copying files failed: {str(e)}")
        return False


def _verify_grpc_generated_code():
    """Verify that gRPC generated code can be imported."""
    logger.debug("Attempting to import gRPC code...")
    
    proto_dir = Path(__file__).parent.absolute() / "proto"
    logger.debug(f"Proto directory: {proto_dir}")
    logger.debug(f"Proto directory exists: {proto_dir.exists()}")
    
    pb2_file = proto_dir / "generated" / PII_DETECTION_PROTO_FILE_NAME
    pb2_grpc_file = proto_dir / "generated" / GRPC_PROTO_PII_DETECTION_FILE

    import_success = (_try_normal_import() or 
                      _try_syspath_import(proto_dir) or 
                      (pb2_file.exists() and pb2_grpc_file.exists() and _try_importlib_import(pb2_file, pb2_grpc_file)) or
                      _try_copy_import(pb2_file, pb2_grpc_file))
    
    if not import_success:
        raise ImportError("All import approaches failed")


def check_environment(api_key=None):
    """Check if the environment is properly set up."""
    env_api_key = _get_api_key_from_config()
    _log_api_key_status(env_api_key)
    _validate_api_key(api_key, env_api_key)
    
    try:
        _verify_grpc_generated_code()
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
        from pii_detector.service.server.pii_service import serve

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

"""
PII Detection gRPC Server using betterproto.

This script starts the gRPC server for the PII detection service using
the modern betterproto implementation with async/await syntax.

Note: Before running this script, you need to:
1. Install the required dependencies: pip install -r requirements.txt
2. Set the HUGGING_FACE_API_KEY environment variable
"""

import os
import sys
import logging
import argparse
import asyncio
from pathlib import Path

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.absolute()))

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

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

        # Check if betterproto file exists
        betterproto_file = Path(__file__).parent.absolute() / "pii_detection.py"
        logger.debug(f"Betterproto file: {betterproto_file}")
        logger.debug(f"Betterproto file exists: {betterproto_file.exists()}")

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
            logger.info("     python server_betterproto.py --api-key your_api_key")
            logger.info("Note: After setting environment variables, you may need to restart your command prompt or IDE.")

        sys.exit(1)

    # Set the environment variable if it was provided via command line
    if api_key:
        os.environ["HUGGING_FACE_API_KEY"] = api_key
        logger.info("Using API key provided via command line")


def check_betterproto_files():
    """Check if the betterproto files are available."""
    try:
        # Check if pii_detection.py exists
        betterproto_file = Path(__file__).parent.absolute() / "pii_detection.py"
        
        if not betterproto_file.exists():
            logger.error("pii_detection.py not found")
            logger.error("Please generate it using:")
            logger.error("python -m grpc_tools.protoc --python_betterproto_out=. --proto_path=proto proto/pii_detection.proto")
            sys.exit(1)

        # Try to import the betterproto classes
        from pii_detection import PIIDetectionRequest, PIIDetectionResponse, PIIEntity
        logger.debug("Successfully imported betterproto classes")

    except ImportError as e:
        logger.error(f"Error importing betterproto classes: {str(e)}")
        logger.error("Please make sure betterproto is installed: pip install betterproto[compiler]")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Error checking betterproto files: {str(e)}")
        sys.exit(1)


async def main():
    """Start the betterproto gRPC server."""
    parser = argparse.ArgumentParser(description="PII Detection gRPC Server (betterproto)")
    parser.add_argument(
        "--port", type=int, default=50051,
        help="Port to listen on (default: 50051)"
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

    # Check if the environment is properly set up
    check_environment(api_key=args.api_key)

    # Check if betterproto files are available
    check_betterproto_files()

    # Import and start the betterproto server
    try:
        from service.server.pii_service_betterproto import serve_betterproto

        logger.info(f"Starting betterproto gRPC server on port {args.port}")
        logger.info("Press Ctrl+C to stop the server")

        # Start the async server
        await serve_betterproto(port=args.port)

    except ImportError as e:
        logger.error(f"Error importing betterproto server module: {str(e)}")
        logger.error("Please make sure all dependencies are installed")
        sys.exit(1)
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
    except Exception as e:
        logger.error(f"Error starting betterproto server: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())

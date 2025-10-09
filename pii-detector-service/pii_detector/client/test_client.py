"""
PII Detection gRPC Test Client.

This script provides a simple client for testing the PII detection gRPC service.

Note: Before running this script, you need to:
1. Install the required dependencies: pip install -r requirements.txt
2. Generate the gRPC code: python -m proto.generate_pb
3. Start the server: python server.py
"""

import sys
import logging
import argparse
from pathlib import Path

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.parent.absolute()))

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def run_test(host: str, port: int, text: str, threshold: float = 0.5):
    """
    Run a test against the PII detection service.
    
    Args:
        host: The server host.
        port: The server port.
        text: The text to analyze.
        threshold: The confidence threshold.
    """
    try:
        # Import the generated gRPC code
        try:
            import grpc
            from proto.generated import pii_detection_pb2
            from proto.generated import pii_detection_pb2_grpc
        except ImportError as e:
            logger.error(f"Error importing gRPC modules: {str(e)}")
            logger.error("Please run: python -m proto.generate_pb")
            sys.exit(1)
        
        # Create a gRPC channel
        channel = grpc.insecure_channel(f"{host}:{port}")
        
        # Create a stub (client)
        stub = pii_detection_pb2_grpc.PIIDetectionServiceStub(channel)
        
        # Create a request
        request = pii_detection_pb2.PIIDetectionRequest(
            content=text,
            threshold=threshold
        )
        
        logger.info(f"Sending request to {host}:{port}")
        logger.info(f"Text length: {len(text)} characters")
        logger.info(f"Threshold: {threshold}")
        
        # Call the service
        response = stub.DetectPII(request)
        
        # Display the results
        print("\n" + "="*60)
        print("PII DETECTION RESULTS")
        print("="*60)
        
        # Display detected entities
        if response.entities:
            print(f"\n📍 Detected {len(response.entities)} PII entities:")
            for entity in response.entities:
                print(f"  • '{entity.text}' → {entity.type_label} (confidence: {entity.score:.1%})")
        else:
            print("\n📍 No PII entities detected.")
        
        # Display masked content
        if response.masked_content:
            print(f"\n🔐 Masked content: {response.masked_content}")
        
        # Display summary
        if response.summary:
            summary_str = ", ".join([f"{k}: {v}" for k, v in response.summary.items()])
            print(f"\n📊 Summary: {summary_str}")
        
        print("\n" + "="*60)
        
    except grpc.RpcError as e:
        logger.error(f"gRPC error: {e.code()}: {e.details()}")
    except Exception as e:
        logger.error(f"Error: {str(e)}")


def main():
    """Run the test client."""
    parser = argparse.ArgumentParser(description="PII Detection gRPC Test Client")
    parser.add_argument(
        "--host", type=str, default="localhost",
        help="Server host (default: localhost)"
    )
    parser.add_argument(
        "--port", type=int, default=50051,
        help="Server port (default: 50051)"
    )
    parser.add_argument(
        "--threshold", type=float, default=0.5,
        help="Confidence threshold (default: 0.5)"
    )
    parser.add_argument(
        "--text", type=str,
        help="Text to analyze (if not provided, example texts will be used)"
    )
    args = parser.parse_args()
    
    # Use provided text or example texts
    if args.text:
        run_test(args.host, args.port, args.text, args.threshold)
    else:
        # Example texts
        examples = [
            "Hello, my name is John Smith. You can reach me at john.smith@company.com or call 555-123-4567. I live at 123 Main Street, New York, NY 10001.",
            "Bonjour, je suis Marie Dupont. Mon email est marie.dupont@entreprise.fr et j'habite au 15 rue de la Paix, 75001 Paris.",
            "My SSN is 123-45-6789 and my credit card number is 4111-1111-1111-1111"
        ]
        
        for i, example in enumerate(examples):
            print(f"\nExample {i+1}:")
            run_test(args.host, args.port, example, args.threshold)
            
        # Interactive mode
        print("\n💡 You can now test with your own texts!")
        print("Enter 'quit' to exit.\n")
        
        while True:
            user_text = input("Enter text to analyze: ")
            if user_text.lower() == 'quit':
                break
                
            if user_text.strip():
                run_test(args.host, args.port, user_text, args.threshold)
            print()


if __name__ == "__main__":
    main()

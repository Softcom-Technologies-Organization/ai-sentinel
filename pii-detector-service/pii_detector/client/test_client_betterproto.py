"""
Test client for PII Detection Service using betterproto.

This script demonstrates how to use the betterproto-based gRPC client
to interact with the PII detection service.
"""

import asyncio
import logging
import sys
from pathlib import Path

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.parent.absolute()))

# Import betterproto classes
from pii_detection import PIIDetectionRequest, PIIDetectionResponse, PIIDetectionServiceStub

# Import grpclib for async gRPC client
import grpclib.client

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


async def test_pii_detection(host: str = "localhost", port: int = 50051):
    """
    Test the PII detection service using betterproto client.
    
    Args:
        host: Server host
        port: Server port
    """
    try:
        # Create gRPC channel
        channel = grpclib.client.Channel(host, port)
        
        # Create service stub
        stub = PIIDetectionServiceStub(channel)
        
        # Test cases
        test_cases = [
            {
                "name": "Email and Phone Test",
                "content": "Contact John Doe at john.doe@example.com or call him at +1-555-123-4567.",
                "threshold": 0.8
            },
            {
                "name": "Personal Information Test",
                "content": "My name is Alice Smith, I live at 123 Main Street, New York, NY 10001. My SSN is 123-45-6789.",
                "threshold": 0.7
            },
            {
                "name": "Credit Card Test",
                "content": "Please charge my credit card 4532-1234-5678-9012 for the purchase.",
                "threshold": 0.9
            },
            {
                "name": "Empty Content Test",
                "content": "",
                "threshold": 0.5
            },
            {
                "name": "No PII Test",
                "content": "This is a simple text without any personal information.",
                "threshold": 0.5
            }
        ]
        
        logger.info(f"Testing PII Detection Service at {host}:{port}")
        logger.info("=" * 60)
        
        for i, test_case in enumerate(test_cases, 1):
            logger.info(f"\nTest {i}: {test_case['name']}")
            logger.info("-" * 40)
            
            try:
                # Make the request using betterproto
                response = await stub.detect_p_i_i(
                    content=test_case['content'],
                    threshold=test_case['threshold']
                )
                
                # Display results
                logger.info(f"Content: {test_case['content'][:100]}{'...' if len(test_case['content']) > 100 else ''}")
                logger.info(f"Threshold: {test_case['threshold']}")
                logger.info(f"Entities found: {len(response.entities)}")
                
                if response.entities:
                    logger.info("Detected PII entities:")
                    for j, entity in enumerate(response.entities[:5], 1):  # Show first 5 entities
                        logger.info(f"  {j}. {entity.type_label}: '{entity.text}' (score: {entity.score:.3f}, pos: {entity.start}-{entity.end})")
                    
                    if len(response.entities) > 5:
                        logger.info(f"  ... and {len(response.entities) - 5} more entities")
                
                if response.summary:
                    logger.info("Summary by type:")
                    for pii_type, count in response.summary.items():
                        logger.info(f"  {pii_type}: {count}")
                
                if response.masked_content:
                    logger.info(f"Masked content: {response.masked_content[:100]}{'...' if len(response.masked_content) > 100 else ''}")
                
                logger.info("✓ Test completed successfully")
                
            except Exception as e:
                logger.error(f"✗ Test failed: {str(e)}")
        
        # Close the channel
        channel.close()
        logger.info("\n" + "=" * 60)
        logger.info("All tests completed!")
        
    except Exception as e:
        logger.error(f"Error connecting to server: {str(e)}")
        logger.error("Make sure the server is running with: python server_betterproto.py")


async def benchmark_test(host: str = "localhost", port: int = 50051, num_requests: int = 10):
    """
    Benchmark the PII detection service.
    
    Args:
        host: Server host
        port: Server port
        num_requests: Number of requests to send
    """
    try:
        import time
        
        # Create gRPC channel
        channel = grpclib.client.Channel(host, port)
        
        # Create service stub
        stub = PIIDetectionServiceStub(channel)
        
        # Test content
        test_content = "Contact John Doe at john.doe@example.com or call him at +1-555-123-4567. " * 10
        
        logger.info(f"\nBenchmark Test: {num_requests} requests")
        logger.info("-" * 40)
        
        start_time = time.time()
        successful_requests = 0
        
        for i in range(num_requests):
            try:
                response = await stub.detect_p_i_i(
                    content=test_content,
                    threshold=0.8
                )
                successful_requests += 1
                if (i + 1) % 5 == 0:
                    logger.info(f"Completed {i + 1}/{num_requests} requests")
            except Exception as e:
                logger.error(f"Request {i + 1} failed: {str(e)}")
        
        end_time = time.time()
        total_time = end_time - start_time
        
        logger.info(f"\nBenchmark Results:")
        logger.info(f"Total requests: {num_requests}")
        logger.info(f"Successful requests: {successful_requests}")
        logger.info(f"Failed requests: {num_requests - successful_requests}")
        logger.info(f"Total time: {total_time:.3f} seconds")
        logger.info(f"Average time per request: {total_time / num_requests:.3f} seconds")
        logger.info(f"Requests per second: {successful_requests / total_time:.2f}")
        
        # Close the channel
        channel.close()
        
    except Exception as e:
        logger.error(f"Benchmark test failed: {str(e)}")


async def main():
    """Main function to run the test client."""
    import argparse
    
    parser = argparse.ArgumentParser(description="PII Detection Test Client (betterproto)")
    parser.add_argument(
        "--host", type=str, default="localhost",
        help="Server host (default: localhost)"
    )
    parser.add_argument(
        "--port", type=int, default=50051,
        help="Server port (default: 50051)"
    )
    parser.add_argument(
        "--benchmark", action="store_true",
        help="Run benchmark test"
    )
    parser.add_argument(
        "--requests", type=int, default=10,
        help="Number of requests for benchmark (default: 10)"
    )
    args = parser.parse_args()
    
    # Run basic tests
    await test_pii_detection(args.host, args.port)
    
    # Run benchmark if requested
    if args.benchmark:
        await benchmark_test(args.host, args.port, args.requests)


if __name__ == "__main__":
    asyncio.run(main())

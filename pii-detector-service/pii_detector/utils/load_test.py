"""
Load Testing Script for PII Detection Service.

This script performs load testing on the PII detection gRPC service to verify
memory management and performance under stress.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import grpc
import time
import threading
import argparse
import statistics
from concurrent import futures
from typing import List, Dict, Optional
import random
import string

from proto import pii_detection_pb2
from proto import pii_detection_pb2_grpc


class LoadTester:
    """Load tester for PII Detection Service."""
    
    def __init__(self, host: str = "localhost", port: int = 50051):
        """
        Initialize the load tester.
        
        Args:
            host: Service host
            port: Service port
        """
        self.host = host
        self.port = port
        self.results = []
        self.errors = []
        self.lock = threading.Lock()
        
    def generate_test_data(self, size: str = "medium") -> str:
        """
        Generate test data with PII.
        
        Args:
            size: Data size - 'small', 'medium', 'large', 'xlarge'
            
        Returns:
            Test text with PII
        """
        base_texts = [
            "Hello, my name is {} {}. You can reach me at {}@example.com or call {}. ",
            "I live at {} {}, {}, {} {}. ",
            "My SSN is {} and my credit card number is {}. ",
            "Born on {}, I work at {} as a {}. ",
            "Please send the invoice to {} at {}@company.com. ",
            "Contact {} {} for more information at {} or visit {} Street. "
        ]
        
        # Size configurations
        sizes = {
            'small': 10,      # ~1KB
            'medium': 100,    # ~10KB
            'large': 1000,    # ~100KB
            'xlarge': 5000    # ~500KB
        }
        
        repeat_count = sizes.get(size, 100)
        
        # Generate random data
        first_names = ["John", "Jane", "Marie", "Pierre", "Emma", "Lucas", "Sophie", "Thomas"]
        last_names = ["Smith", "Dupont", "Martin", "Bernard", "Johnson", "Wilson", "Brown", "Davis"]
        streets = ["Main", "Park", "Oak", "Maple", "Cedar", "Elm", "Washington", "Lincoln"]
        cities = ["New York", "Paris", "London", "Berlin", "Tokyo", "Sydney", "Toronto", "Madrid"]
        
        text_parts = []
        for _ in range(repeat_count):
            template = random.choice(base_texts)
            
            # Generate random values
            first_name = random.choice(first_names)
            last_name = random.choice(last_names)
            email_prefix = f"{first_name.lower()}.{last_name.lower()}"
            phone = f"{random.randint(100, 999)}-{random.randint(100, 999)}-{random.randint(1000, 9999)}"
            street_num = random.randint(1, 999)
            street_name = random.choice(streets)
            city = random.choice(cities)
            state = random.choice(["NY", "CA", "TX", "FL", "IL", "PA", "OH", "GA"])
            zip_code = f"{random.randint(10000, 99999)}"
            ssn = f"{random.randint(100, 999)}-{random.randint(10, 99)}-{random.randint(1000, 9999)}"
            cc = f"{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"
            birth_date = f"{random.randint(1, 28)}/{random.randint(1, 12)}/{random.randint(1960, 2005)}"
            company = random.choice(["TechCorp", "DataSoft", "CloudNet", "InfoSys", "WebTech"])
            job = random.choice(["Engineer", "Manager", "Analyst", "Developer", "Designer"])
            
            # Fill template based on its structure
            if "name is" in template:
                text = template.format(first_name, last_name, email_prefix, phone)
            elif "live at" in template:
                text = template.format(street_num, street_name, city, state, zip_code)
            elif "SSN is" in template:
                text = template.format(ssn, cc)
            elif "Born on" in template:
                text = template.format(birth_date, company, job)
            elif "invoice to" in template:
                text = template.format(first_name + " " + last_name, email_prefix)
            else:
                text = template.format(first_name, last_name, phone, street_name)
            
            text_parts.append(text)
        
        return ''.join(text_parts)
    
    def make_request(self, stub, text: str, threshold: float = 0.5) -> Dict:
        """
        Make a single request to the service.
        
        Args:
            stub: gRPC stub
            text: Text to analyze
            threshold: Detection threshold
            
        Returns:
            Result dictionary
        """
        start_time = time.time()
        
        try:
            request = pii_detection_pb2.PIIDetectionRequest(
                content=text,
                threshold=threshold
            )
            
            response = stub.DetectPII(request, timeout=30)
            
            duration = time.time() - start_time
            
            return {
                'success': True,
                'duration': duration,
                'entities_count': len(response.entities),
                'text_size': len(text),
                'error': None
            }
            
        except grpc.RpcError as e:
            duration = time.time() - start_time
            return {
                'success': False,
                'duration': duration,
                'entities_count': 0,
                'text_size': len(text),
                'error': f"{e.code()}: {e.details()}"
            }
        except Exception as e:
            duration = time.time() - start_time
            return {
                'success': False,
                'duration': duration,
                'entities_count': 0,
                'text_size': len(text),
                'error': str(e)
            }
    
    def worker_thread(self, worker_id: int, num_requests: int, data_size: str, 
                     delay_ms: int = 0):
        """
        Worker thread for load testing.
        
        Args:
            worker_id: Worker identifier
            num_requests: Number of requests to make
            data_size: Size of test data
            delay_ms: Delay between requests in milliseconds
        """
        channel = grpc.insecure_channel(f"{self.host}:{self.port}")
        stub = pii_detection_pb2_grpc.PIIDetectionServiceStub(channel)
        
        for i in range(num_requests):
            # Generate test data
            text = self.generate_test_data(data_size)
            
            # Make request
            result = self.make_request(stub, text)
            result['worker_id'] = worker_id
            result['request_id'] = i
            
            # Store result
            with self.lock:
                self.results.append(result)
                if not result['success']:
                    self.errors.append(result)
            
            # Delay between requests
            if delay_ms > 0:
                time.sleep(delay_ms / 1000.0)
        
        channel.close()
    
    def run_load_test(self, num_workers: int = 5, requests_per_worker: int = 20,
                     data_size: str = "medium", delay_ms: int = 100,
                     ramp_up_seconds: int = 0):
        """
        Run the load test.
        
        Args:
            num_workers: Number of concurrent workers
            requests_per_worker: Requests per worker
            data_size: Size of test data
            delay_ms: Delay between requests
            ramp_up_seconds: Time to ramp up workers
        """
        print(f"Starting load test:")
        print(f"  - Workers: {num_workers}")
        print(f"  - Requests per worker: {requests_per_worker}")
        print(f"  - Total requests: {num_workers * requests_per_worker}")
        print(f"  - Data size: {data_size}")
        print(f"  - Delay between requests: {delay_ms}ms")
        print(f"  - Ramp up time: {ramp_up_seconds}s")
        print()
        
        # Clear previous results
        self.results.clear()
        self.errors.clear()
        
        # Start time
        start_time = time.time()
        
        # Create and start worker threads
        threads = []
        for i in range(num_workers):
            thread = threading.Thread(
                target=self.worker_thread,
                args=(i, requests_per_worker, data_size, delay_ms)
            )
            threads.append(thread)
            thread.start()
            
            # Ramp up delay
            if ramp_up_seconds > 0 and i < num_workers - 1:
                time.sleep(ramp_up_seconds / num_workers)
        
        # Wait for all threads to complete
        print("Load test in progress...")
        for thread in threads:
            thread.join()
        
        # Calculate statistics
        total_time = time.time() - start_time
        self.print_results(total_time)
    
    def print_results(self, total_time: float):
        """Print test results and statistics."""
        if not self.results:
            print("No results collected")
            return
        
        # Calculate statistics
        successful = [r for r in self.results if r['success']]
        failed = [r for r in self.results if not r['success']]
        
        success_rate = len(successful) / len(self.results) * 100
        
        print("\n" + "=" * 60)
        print("LOAD TEST RESULTS")
        print("=" * 60)
        
        print(f"\nOverall Statistics:")
        print(f"  - Total requests: {len(self.results)}")
        print(f"  - Successful: {len(successful)}")
        print(f"  - Failed: {len(failed)}")
        print(f"  - Success rate: {success_rate:.1f}%")
        print(f"  - Total time: {total_time:.2f}s")
        print(f"  - Requests/second: {len(self.results) / total_time:.2f}")
        
        if successful:
            durations = [r['duration'] for r in successful]
            entities_counts = [r['entities_count'] for r in successful]
            
            print(f"\nResponse Time Statistics (successful requests):")
            print(f"  - Min: {min(durations) * 1000:.1f}ms")
            print(f"  - Max: {max(durations) * 1000:.1f}ms")
            print(f"  - Mean: {statistics.mean(durations) * 1000:.1f}ms")
            print(f"  - Median: {statistics.median(durations) * 1000:.1f}ms")
            if len(durations) > 1:
                print(f"  - Std Dev: {statistics.stdev(durations) * 1000:.1f}ms")
            
            print(f"\nPII Detection Statistics:")
            print(f"  - Avg entities per request: {statistics.mean(entities_counts):.1f}")
            print(f"  - Max entities: {max(entities_counts)}")
            print(f"  - Min entities: {min(entities_counts)}")
        
        if failed:
            print(f"\nError Summary:")
            error_counts = {}
            for error in self.errors:
                error_msg = error['error']
                error_counts[error_msg] = error_counts.get(error_msg, 0) + 1
            
            for error_msg, count in sorted(error_counts.items(), key=lambda x: x[1], reverse=True):
                print(f"  - {error_msg}: {count} occurrences")
        
        print("\n" + "=" * 60)


def main():
    """Main function."""
    parser = argparse.ArgumentParser(description="Load test the PII Detection Service")
    
    parser.add_argument("--host", type=str, default="localhost",
                       help="Service host (default: localhost)")
    parser.add_argument("--port", type=int, default=50051,
                       help="Service port (default: 50051)")
    
    parser.add_argument("--workers", type=int, default=5,
                       help="Number of concurrent workers (default: 5)")
    parser.add_argument("--requests", type=int, default=20,
                       help="Requests per worker (default: 20)")
    parser.add_argument("--size", type=str, default="medium",
                       choices=['small', 'medium', 'large', 'xlarge'],
                       help="Test data size (default: medium)")
    parser.add_argument("--delay", type=int, default=100,
                       help="Delay between requests in ms (default: 100)")
    parser.add_argument("--ramp-up", type=int, default=0,
                       help="Ramp up time in seconds (default: 0)")
    
    # Predefined test scenarios
    parser.add_argument("--scenario", type=str,
                       choices=['light', 'normal', 'heavy', 'stress'],
                       help="Predefined test scenario")
    
    args = parser.parse_args()
    
    # Apply scenario settings if specified
    if args.scenario:
        scenarios = {
            'light': {'workers': 2, 'requests': 10, 'size': 'small', 'delay': 500},
            'normal': {'workers': 5, 'requests': 20, 'size': 'medium', 'delay': 100},
            'heavy': {'workers': 10, 'requests': 50, 'size': 'large', 'delay': 50},
            'stress': {'workers': 20, 'requests': 100, 'size': 'xlarge', 'delay': 10}
        }
        
        scenario = scenarios[args.scenario]
        args.workers = scenario['workers']
        args.requests = scenario['requests']
        args.size = scenario['size']
        args.delay = scenario['delay']
        
        print(f"Using {args.scenario} scenario settings")
    
    # Create and run load tester
    tester = LoadTester(host=args.host, port=args.port)
    
    try:
        tester.run_load_test(
            num_workers=args.workers,
            requests_per_worker=args.requests,
            data_size=args.size,
            delay_ms=args.delay,
            ramp_up_seconds=args.ramp_up
        )
    except KeyboardInterrupt:
        print("\nLoad test interrupted by user")
    except Exception as e:
        print(f"\nError during load test: {e}")


if __name__ == "__main__":
    main()

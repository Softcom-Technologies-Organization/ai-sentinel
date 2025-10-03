# PII Detection gRPC Microservice

This microservice provides a gRPC API for detecting Personally Identifiable Information (PII) in text content using the Piiranha model with advanced memory management and performance optimizations.

## Features

- Detect PII in text content with configurable confidence threshold
- Return detailed analysis results including entity positions and confidence scores
- Provide masked version of the input text with PII replaced
- Generate summary of detected PII types and counts
- High-performance gRPC interface with memory optimization
- **Memory Management**: Advanced memory monitoring, cleanup, and resource optimization
- **Batch Processing**: Support for batch processing of multiple texts
- **Performance Optimization**: Chunked processing for large documents
- **Resource Management**: Singleton pattern and proper resource disposal
- **Enhanced Monitoring**: Memory usage tracking and automatic cleanup
- **Load Testing**: Built-in utilities for performance testing and stress testing

## Project Structure

```
pii-grpc-service/
├── proto/              # Protocol Buffers definitions
│   ├── pii_detection.proto    # Service definition
│   └── generate_pb.py         # Script to generate gRPC code
├── service/            # gRPC service implementation
│   ├── detector/       # PII detection implementation
│   │   ├── pii_detector.py           # Main detector with memory optimization
│   │   ├── pii_detector_optimized.py # Enhanced optimized version
│   │   └── pii_detector_original.py  # Original version (preserved)
│   └── server/         # gRPC server implementation
│       ├── pii_service.py            # Main service with memory management
│       ├── pii_service_betterproto.py # Modern betterproto implementation
│       ├── pii_service_optimized.py  # Enhanced optimized version
│       └── pii_service_original.py   # Original version (preserved)
├── client/             # Client implementation for testing
│   ├── test_client.py            # Traditional test client with interactive mode
│   └── test_client_betterproto.py # Modern betterproto test client
├── utils/              # Utility components for testing and monitoring
│   ├── load_test.py    # Performance testing and stress testing utilities
│   └── monitor_memory.py # Memory monitoring and analysis tools
├── server.py           # Traditional server script with enhanced configuration
├── server_betterproto.py # Modern betterproto server script
├── test_detector.py    # Direct detector testing script
├── pii_detection.py    # Generated betterproto classes (modern implementation)
├── requirements.txt    # Project dependencies (includes betterproto)
└── README.md           # Project documentation
```

## Installation

1. Create a virtual environment:
   ```
   python -m venv .venv
   .venv\Scripts\activate  # Windows
   ```

2. Install dependencies:
   ```
   pip install -r requirements.txt
   ```

3. Set up environment variables:
   ```
   set HUGGING_FACE_API_KEY=your_api_key  # Windows
   ```

4. Generate gRPC code:
   ```
   python -m proto.generate_pb
   ```

## Usage

### Traditional gRPC Implementation

#### Starting the Server

Start the traditional gRPC server:

```
python server.py --api-key=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
```

Server options:
- `--port PORT`: Port to listen on (default: 50051)
- `--workers N`: Maximum number of worker threads (default: 3, optimized for memory usage)
- `--memory-limit MB`: Memory limit in MB for automatic cleanup (default: 2048)
- `--max-text-size CHARS`: Maximum text size in characters (default: 100000)
- `--batch-size N`: Batch size for processing multiple texts (default: 10)

Example:
```
python server.py --port 50052 --workers 5 --memory-limit 4096 --max-text-size 200000
```

#### Using the Test Client

Run the traditional test client:

```
python -m client.test_client
```

Client options:
- `--host HOST`: Server host (default: localhost)
- `--port PORT`: Server port (default: 50051)
- `--threshold THRESHOLD`: Confidence threshold (default: 0.5)
- `--text TEXT`: Text to analyze (if not provided, example texts will be used)

Example:
```
python -m client.test_client --host localhost --port 50051 --threshold 0.7 --text "My name is John Smith and my email is john@example.com"
```

If you run the client without the `--text` parameter, it will run through example texts and then enter an interactive mode where you can type your own texts to analyze.

### Betterproto Implementation (Modern Async/Await)

The project now includes a modern betterproto-based implementation that uses async/await syntax and provides a more pythonic API.

#### Prerequisites for Betterproto

1. Generate the betterproto code:
   ```
   python -m grpc_tools.protoc --python_betterproto_out=. --proto_path=proto proto/pii_detection.proto
   ```

2. Install additional dependencies (already included in requirements.txt):
   ```
   pip install betterproto[compiler] grpclib
   ```

#### Starting the Betterproto Server

Start the modern async gRPC server:

```
python server_betterproto.py --api-key=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
```

Server options:
- `--port PORT`: Port to listen on (default: 50051)
- `--api-key KEY`: Hugging Face API key
- `--debug`: Enable debug logging

Example:
```
python server_betterproto.py --port 50052 --debug
```

#### Using the Betterproto Test Client

Run the modern async test client:

```
python -m client.test_client_betterproto
```

Client options:
- `--host HOST`: Server host (default: localhost)
- `--port PORT`: Server port (default: 50051)
- `--benchmark`: Run benchmark test
- `--requests N`: Number of requests for benchmark (default: 10)

Example:
```
python -m client.test_client_betterproto --host localhost --port 50051 --benchmark --requests 20
```

#### Advantages of Betterproto Implementation

- **Modern Python**: Uses dataclasses and type hints
- **Async/Await**: Native async support for better performance
- **Cleaner API**: More pythonic interface
- **Better IDE Support**: Enhanced autocomplete and type checking
- **Simplified Code**: Less boilerplate compared to traditional gRPC

### Utility Tools

#### Load Testing

Run performance and stress tests:

```
python -m utils.load_test
```

This utility provides:
- Concurrent request testing
- Performance benchmarking
- Memory usage analysis during load
- Stress testing with configurable parameters

#### Memory Monitoring

Monitor system memory usage:

```
python -m utils.monitor_memory
```

This utility provides:
- Real-time memory usage monitoring
- Memory leak detection
- Resource usage analysis
- System performance metrics

#### Direct Detector Testing

Test the PII detector directly without gRPC:

```
python test_detector.py
```

#### Standalone Detection Script

Run PII detection as a standalone script:

```
python pii_detection.py
```

## Performance and Memory Management

### Key Optimizations

The microservice includes several performance and memory optimizations:

- **Memory-Optimized Architecture**: Singleton pattern for PII detector instances reduces memory footprint
- **Chunked Processing**: Large documents are processed in chunks to prevent memory overflow
- **Batch Processing**: Multiple texts can be processed together for improved throughput
- **Automatic Memory Cleanup**: Configurable memory limits with automatic garbage collection
- **Resource Management**: Proper disposal of resources and CUDA memory cache management
- **Memory Monitoring**: Real-time memory usage tracking with alerts and automatic cleanup

### Memory Configuration

The service provides several memory-related configuration options:

- **Memory Limits**: Set maximum memory usage before automatic cleanup
- **Worker Limits**: Optimized default of 3 workers to balance performance and memory usage
- **Text Size Limits**: Configurable maximum text size to prevent memory issues
- **Batch Size**: Adjustable batch processing size for optimal performance

### Performance Features

- **GPU Acceleration**: Automatic CUDA support when available
- **Caching**: Intelligent caching of model components
- **Regex Fallback**: Fast regex-based email detection as fallback
- **Optimized Tokenization**: Efficient text processing and tokenization
- **Memory-Aware Processing**: Dynamic adjustment based on available memory

## API Reference

### Service: PIIDetectionService

#### Method: DetectPII

Analyzes text content for PII and returns detailed results.

**Request (PIIDetectionRequest):**
- `content` (string): The text content to analyze for PII
- `threshold` (float): Optional confidence threshold (0.0-1.0)

**Response (PIIDetectionResponse):**
- `entities` (repeated PIIEntity): List of detected PII entities
- `summary` (map<string, int32>): Summary of detected PII types and counts
- `masked_content` (string): Masked version of the input text with PII replaced

**PIIEntity:**
- `text` (string): The detected text
- `type` (string): The type of PII (e.g., EMAIL, PHONE, etc.)
- `type_label` (string): Human-readable label for the PII type
- `start` (int32): Start position in the original text
- `end` (int32): End position in the original text
- `score` (float): Confidence score (0.0-1.0)

## License

[MIT License](LICENSE)

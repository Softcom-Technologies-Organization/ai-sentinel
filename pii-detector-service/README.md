# PII Detector Service

[![Python Version](https://img.shields.io/badge/python-3.9%2B-blue)](https://www.python.org/downloads/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](../LICENSE.md)
[![gRPC](https://img.shields.io/badge/gRPC-1.60%2B-blue.svg)](https://grpc.io/)
[![Docker](https://img.shields.io/badge/docker-ready-blue.svg)](https://www.docker.com/)

> High-performance gRPC microservice for detecting Personally Identifiable Information (PII) using state-of-the-art machine learning models with advanced memory management and parallel processing capabilities.

## Table of Contents

- [About](#about)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Architecture](#architecture)
- [Detection Models](#detection-models)
- [Performance](#performance)
- [Testing](#testing)
- [Deployment](#deployment)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## About

The **PII Detector Service** is a production-ready gRPC microservice designed to identify and protect sensitive personal information in text content. It leverages multiple detection strategies including machine learning models, rule-based systems, and regex patterns to provide accurate and comprehensive PII detection.

**Context**: In today's data-driven world, protecting personal information is critical for compliance with regulations like GDPR, CCPA, and HIPAA. Organizations need reliable tools to identify and safeguard PII across their systems.

**Problem Solved**: Manual PII detection is time-consuming, error-prone, and doesn't scale. Traditional rule-based systems miss context-dependent PII, while pure ML approaches generate false positives.

**Solution**: This service combines the best of both worlds - advanced ML models (GLiNER) for context-aware detection, Microsoft's Presidio for production-grade rule-based detection, and regex patterns for structured data. The result is high accuracy with low false positives.

**Value Proposition**: 
- **Multilingual support**: Detects PII in 17+ entity types across multiple languages
- **Production-ready**: Built with memory optimization, error handling, and monitoring
- **Flexible**: Easily configurable detection strategies via TOML files
- **Scalable**: Parallel processing and streaming support for high-throughput scenarios

## Features

- ✅ **Multi-Model Detection**: Combines GLiNER, Presidio, and Regex detectors
- ✅ **17+ PII Entity Types**: Names, emails, phone numbers, addresses, financial data, and more
- ✅ **Multilingual Support**: Works with English, French, German, Spanish, and other languages
- ✅ **gRPC API**: High-performance protocol with synchronous and streaming modes
- ✅ **Smart Post-Processing**: Email expansion, entity merging, zipcode/city separation
- ✅ **Parallel Processing**: Multi-threaded text processing for optimal throughput
- ✅ **Memory Optimized**: Advanced memory management for CPU/GPU environments
- ✅ **Flexible Configuration**: TOML-based configuration for models and detection settings
- ✅ **Content Masking**: Automatic PII masking with configurable patterns
- ✅ **Provenance Tracking**: Logs which model detected each entity
- ✅ **Performance Metrics**: Built-in throughput and latency monitoring
- 📋 **Docker Ready**: Production-ready containerization with Infisical integration

## Prerequisites

Before starting, ensure you have:

- **Python**: Version 3.9 or higher
  ```bash
  python --version
  ```

- **pip**: Python package installer (included with Python)
  ```bash
  pip --version
  ```

- **Docker** (optional, for containerized deployment)
  ```bash
  docker --version
  ```

**Optional but Recommended**:
- **CUDA Toolkit**: For GPU acceleration (if using CUDA-enabled PyTorch)
- **Git**: For version control and repository management

## Installation

### Standard Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Softcom-Technologies-Organization/ai-sentinel.git
   cd ai-sentinel/pii-detector-service
   ```

2. **Create a virtual environment**
   ```bash
   python -m venv .venv
   
   # On Windows
   .venv\Scripts\activate
   
   # On Linux/Mac
   source .venv/bin/activate
   ```

3. **Install dependencies (CPU-only)**
   ```bash
   pip install -e . --extra-index-url https://download.pytorch.org/whl/cpu
   ```

   **Note**: The CPU-only PyTorch is recommended for production deployments to minimize memory footprint.

4. **Generate Protocol Buffer files**
   ```bash
   python -m pii_detector.proto.generate_pb
   ```

5. **Verify installation**
   ```bash
   python -m pii_detector.server --help
   ```

### Installation with GPU Support

If you have NVIDIA GPU and CUDA installed:

```bash
# Install with CUDA support
pip install -e .
pip install torch --index-url https://download.pytorch.org/whl/cu121
```

### Docker Installation

```bash
# Build the Docker image
docker build -t pii-detector-service -f pii-detector-service/Dockerfile .

# Run the container
docker run -p 50051:50051 pii-detector-service
```

### Troubleshooting

- **ImportError: No module named 'torch'**: Ensure you installed with the PyTorch extra index URL
- **gRPC connection refused**: Check that port 50051 is not in use
- **Out of memory errors**: Use CPU-only PyTorch or reduce `max_workers` in configuration

## Configuration

The service uses TOML configuration files located in the `config/` directory.

### Global Detection Settings

**File**: `config/detection-settings.toml`

| Setting | Description | Default | Required |
|---------|-------------|---------|----------|
| `default_threshold` | Confidence threshold (0.0-1.0) | `0.7` | No |
| `llm_detection_enabled` | Enable ML models | `true` | Yes |
| `regex_detection_enabled` | Enable regex patterns | `false` | Yes |
| `presidio_detection_enabled` | Enable Presidio | `true` | Yes |
| `log_provenance` | Log detection source | `true` | No |
| `log_throughput` | Log performance metrics | `true` | No |
| `parallel_processing.enabled` | Enable parallel processing | `true` | No |
| `parallel_processing.max_workers` | Worker threads | `10` | No |

### Model-Specific Configuration

Each model has its own configuration file in `config/models/`:

- **gliner-pii.toml**: GLiNER PII Large v1.0 configuration
- **presidio-detector.toml**: Microsoft Presidio configuration
- **regex-patterns.toml**: Regex-based detection patterns
- **multilang-pii-ner.toml**: Multilingual NER model

**Example**: Configuring GLiNER model

```toml
# config/models/gliner-pii.toml
enabled = true
model_id = "nvidia/gliner-pii"
device = "cpu"
max_length = 720
threshold = 0.7

[scoring]
GIVENNAME = 0.75
EMAIL = 0.80
TELEPHONENUM = 0.80
```

### Environment Variables

For production deployments, you can override settings using environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `PII_DETECTOR_PORT` | gRPC server port | `50051` |
| `PII_DETECTOR_WORKERS` | Max worker threads | `5` |
| `PII_DETECTOR_DEVICE` | Device (cpu/cuda/mps) | `cpu` |
| `INFISICAL_TOKEN` | Infisical authentication token | `<token>` |

## Usage

### Starting the Server

**Basic usage**:
```bash
python -m pii_detector.server
```

**With custom port**:
```bash
python -m pii_detector.server --port 50052
```

**With custom worker count**:
```bash
python -m pii_detector.server --workers 10
```

**With debug logging**:
```bash
python -m pii_detector.server --debug
```

### Expected Output

```
2025-11-08 11:30:00 - INFO - PII Detection gRPC Server starting...
2025-11-08 11:30:05 - INFO - Model downloaded successfully: knowledgator/gliner-pii-large-v1.0
2025-11-08 11:30:12 - INFO - Server started on port 50051
2025-11-08 11:30:12 - INFO - Ready to accept requests
```

### Client Examples

#### Python Client (gRPC)

```python
import grpc
from pii_detector.proto.generated import pii_detection_pb2
from pii_detector.proto.generated import pii_detection_pb2_grpc

# Connect to server
channel = grpc.insecure_channel('localhost:50051')
stub = pii_detection_pb2_grpc.PIIDetectionServiceStub(channel)

# Create request
request = pii_detection_pb2.PIIDetectionRequest(
    content="John Doe lives at 123 Main St, email: john.doe@example.com",
    threshold=0.7,
    mask_pii=True
)

# Send request
response = stub.DetectPII(request)

# Process results
print(f"Detected {len(response.entities)} PII entities:")
for entity in response.entities:
    print(f"  - {entity.type}: {entity.text} (score: {entity.score:.2f})")

print(f"\nMasked content: {response.masked_content}")
```

#### Streaming Detection

```python
# Streaming mode for large documents
request = pii_detection_pb2.PIIDetectionRequest(
    content=large_document_text,
    chunk_size=5000
)

# Process streaming updates
for update in stub.StreamDetectPII(request):
    if update.is_final:
        print("Detection complete!")
    else:
        print(f"Chunk processed: {len(update.entities)} entities found")
```

### Using Docker

```bash
# Run with docker-compose (from project root)
docker-compose -f docker-compose.dev.yml up pii-detector-service

# Run standalone container
docker run -p 50051:50051 \
  -v $(pwd)/config:/app/config:ro \
  pii-detector-service
```

## Architecture

### Project Structure

Following **Hexagonal Architecture** (Ports & Adapters Pattern) principles:

```
pii-detector-service/
├── pii_detector/                          # Main application package
│   ├── domain/                           # Domain Layer - Pure business logic
│   │   ├── entity/                       # Domain entities (PII, DetectionResult)
│   │   ├── exception/                    # Domain exceptions
│   │   ├── port/                         # Domain ports (interfaces)
│   │   └── service/                      # Domain services
│   ├── application/                      # Application Layer - Use cases & orchestration
│   │   ├── config/                       # Application configuration management
│   │   ├── factory/                      # Factory patterns for object creation
│   │   └── orchestration/                # Orchestration logic & workflows
│   ├── infrastructure/                   # Infrastructure Layer - External adapters
│   │   ├── adapter/                      # Inbound/outbound adapters
│   │   ├── detector/                     # Detection strategies (GLiNER, Presidio, Regex)
│   │   ├── model_management/             # Model loading and management
│   │   └── text_processing/              # Text processing and utilities
│   ├── proto/                            # Protocol Buffer definitions (gRPC)
│   └── utils/                            # Utility functions and helpers
├── config/                               # Configuration files
│   ├── detection-settings.toml           # Global detection settings
│   └── models/                           # Model-specific configurations
├── tests/                                # Test suite
│   ├── unit/                             # Unit tests
│   └── integration/                      # Integration tests
├── docs/                                 # Documentation and guides
├── Dockerfile                            # Container image definition
└── pyproject.toml                        # Python project configuration
```

**Hexagonal Architecture Layers**:
- **Domain Layer** (`domain/`): Core business logic with no external dependencies
- **Application Layer** (`application/`): Use cases and service orchestration
- **Infrastructure Layer** (`infrastructure/`): External system adapters (gRPC, ML models, text processing)

### Component Diagram

```
┌─────────────────────────────────────────────────┐
│           gRPC Server (PIIDetectionServicer)     │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌────────────────────────────────────────┐    │
│  │   CompositePIIDetector                  │    │
│  │   (Composite Pattern)                   │    │
│  ├────────────────────────────────────────┤    │
│  │                                          │    │
│  │  ┌──────────────┐  ┌─────────────────┐ │    │
│  │  │ GLiNERDetector│  │PresidioDetector│ │    │
│  │  └──────────────┘  └─────────────────┘ │    │
│  │  ┌──────────────┐                       │    │
│  │  │ RegexDetector │                       │    │
│  │  └──────────────┘                       │    │
│  └────────────────────────────────────────┘    │
│                                                  │
│  ┌────────────────────────────────────────┐    │
│  │   Post-Processing Pipeline              │    │
│  │   - Email expansion                     │    │
│  │   - Entity merging                      │    │
│  │   - Zipcode/city separation            │    │
│  │   - Deduplication                       │    │
│  └────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### Key Design Patterns

- **Composite Pattern**: `CompositePIIDetector` aggregates multiple detection strategies
- **Strategy Pattern**: Pluggable detectors (GLiNER, Presidio, Regex)
- **Factory Pattern**: Model loading and initialization
- **Template Method**: Common detection workflow with customizable steps
- **Observer Pattern**: Memory monitoring and metrics collection

### Technology Stack

- **Backend**: Python 3.9+
- **RPC Framework**: gRPC 1.60+, Protocol Buffers 4.25+
- **ML Framework**: PyTorch 2.0+, Transformers 4.35+
- **PII Detection**: GLiNER 0.2+, Presidio 2.2+
- **Configuration**: TOML
- **Testing**: pytest 8.0+, pytest-grpc
- **Containerization**: Docker, Docker Compose
- **Secrets Management**: Infisical

## Detection Models

### GLiNER PII Large v1.0

**Type**: Zero-shot Named Entity Recognition  
**Source**: [knowledgator/gliner-pii-large-v1.0](https://huggingface.co/knowledgator/gliner-pii-large-v1.0)  
**Languages**: Multilingual (17+ languages)  
**Entities**: 17 PII types including names, emails, addresses, financial data

**Advantages**:
- Context-aware detection
- High accuracy for informal text
- No training required for new entity types

**Best For**: User-generated content, multilingual documents, informal text

### Microsoft Presidio

**Type**: Rule-based + ML hybrid  
**Source**: [Microsoft Presidio Analyzer](https://microsoft.github.io/presidio/)  
**Languages**: English, Spanish, French, German, Italian, Portuguese  
**Entities**: 20+ PII types with production-grade patterns

**Advantages**:
- Production-tested reliability
- Low false positive rate
- Fast execution

**Best For**: Structured data, formal documents, compliance requirements

### Regex Detector

**Type**: Pattern matching  
**Source**: Custom regex patterns  
**Languages**: Language-agnostic  
**Entities**: Highly structured data (SSN, credit cards, phone numbers)

**Advantages**:
- Extremely fast
- Zero false negatives for known patterns
- No model loading overhead

**Best For**: Structured identifiers, standardized formats

### Supported PII Types

| Entity Type | Description | Example |
|-------------|-------------|---------|
| `GIVENNAME` | First name | John |
| `SURNAME` | Last name | Doe |
| `EMAIL` | Email address | john.doe@example.com |
| `TELEPHONENUM` | Phone number | +1-555-0123 |
| `STREET` | Street address | 123 Main Street |
| `CITY` | City name | New York |
| `ZIPCODE` | Postal code | 10001 |
| `DATEOFBIRTH` | Date of birth | 01/15/1990 |
| `SOCIALNUM` | Social Security Number | 123-45-6789 |
| `CREDITCARDNUMBER` | Credit card | 4111-1111-1111-1111 |
| `ACCOUNTNUM` | Bank account | ACC-123456 |
| `DRIVERLICENSENUM` | Driver's license | DL-AB12345 |
| `IDCARDNUM` | ID card number | ID-987654 |
| `TAXNUM` | Tax ID | TAX-112233 |
| `PASSWORD` | Password or secret | p@ssw0rd |
| `USERNAME` | Username | john_doe |
| `BUILDINGNUM` | Building number | 123A |

## Performance

### Benchmarks

Tested on Intel Core i7-11800H @ 2.30GHz with 32GB RAM:

| Scenario | Text Size | Entities | Processing Time | Throughput |
|----------|-----------|----------|-----------------|------------|
| Short text | 500 chars | 3 | 120 ms | 4,166 chars/s |
| Medium text | 5,000 chars | 15 | 450 ms | 11,111 chars/s |
| Long text | 50,000 chars | 89 | 2,800 ms | 17,857 chars/s |
| Parallel (5 texts) | 2,500 chars each | 8 avg | 980 ms | 12,755 chars/s |

**Configuration**: GLiNER + Presidio enabled, 10 workers, CPU-only

### Optimization Tips

1. **Enable Parallel Processing**: Set `parallel_processing.enabled = true` in config
2. **Adjust Worker Count**: Match to CPU cores (`parallel_processing.max_workers = <cores>`)
3. **Use CPU-only PyTorch**: Reduces memory footprint for CPU deployments
4. **Tune Confidence Thresholds**: Higher thresholds = fewer false positives = faster processing
5. **Disable Unused Detectors**: Turn off models you don't need in `detection-settings.toml`
6. **Streaming for Large Documents**: Use `StreamDetectPII` for documents > 100KB

### Memory Usage

- **GLiNER model**: ~500MB RAM
- **Presidio**: ~50MB RAM
- **Per request overhead**: ~10-20MB (temporary)
- **Recommended**: Minimum 2GB RAM, 4GB+ for production

## Testing

### Running Tests

**All tests**:
```bash
pytest
```

**Unit tests only**:
```bash
pytest tests/unit/
```

**Integration tests**:
```bash
pytest tests/integration/
```

**With coverage report**:
```bash
pytest --cov=pii_detector --cov-report=html
```

### Test Coverage

Current coverage: ![Coverage](https://img.shields.io/badge/coverage-82%25-brightgreen)

**Goal**: Maintain > 80% code coverage

### Writing Tests

Tests use **pytest** with **AssertJ-style assertions**:

```python
def test_email_detection():
    """Should_DetectEmail_When_ValidEmailProvided"""
    detector = PIIDetector()
    text = "Contact: john.doe@example.com"
    
    result = detector.detect_pii(text)
    
    assert len(result.entities) == 1
    assert result.entities[0].type == "EMAIL"
    assert result.entities[0].text == "john.doe@example.com"
```

## Deployment

### Production Deployment with Docker Compose

1. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env with your values
   ```

2. **Start services**:
   ```bash
   docker-compose up -d pii-detector-service
   ```

3. **Verify deployment**:
   ```bash
   docker-compose logs -f pii-detector-service
   ```

### Health Checks

The service implements gRPC health checking:

```bash
# Install grpc-health-probe
wget https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/v0.4.19/grpc_health_probe-linux-amd64
chmod +x grpc_health_probe-linux-amd64

# Check service health
./grpc_health_probe-linux-amd64 -addr=localhost:50051
```

### Kubernetes Deployment

See [PRODUCTION_SETUP.md](../docs/PRODUCTION_SETUP.md) for Kubernetes deployment instructions.

### Monitoring

**Metrics exposed**:
- Request count and latency
- Entity detection rates by type
- Memory usage (RSS)
- Throughput (chars/second)
- Model inference time

**Logging**:
- Structured JSON logs
- Request IDs for tracing
- Provenance tracking (which model detected each entity)

## Documentation

Additional documentation is available in the `docs/` directory:

- **[GRPC_SERVER_ARCHITECTURE.md](docs/GRPC_SERVER_ARCHITECTURE.md)**: Server architecture and request flow
- **[PRESIDIO_INTEGRATION.md](docs/PRESIDIO_INTEGRATION.md)**: Presidio detector integration details
- **[GLINER_INTEGRATION_STATUS.md](docs/GLINER_INTEGRATION_STATUS.md)**: GLiNER model integration
- **[PARALLEL_PROCESSING_CONFIG.md](docs/PARALLEL_PROCESSING_CONFIG.md)**: Parallel processing configuration
- **[CPU_GPU_OPTIMIZATION.md](docs/CPU_GPU_OPTIMIZATION.md)**: Performance optimization guide
- **[REFACTORING_SUMMARY.md](docs/REFACTORING_SUMMARY.md)**: Architecture refactoring history

## Contributing

Contributions are welcome! Here's how to participate:

### Contribution Process

1. **Fork** the project
2. **Create** a feature branch (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'Add: Amazing feature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. **Open** a Pull Request

### Coding Conventions

- **Style**: Follow [PEP 8](https://pep8.org/) - enforced by Black and Ruff
- **Type Hints**: Required for all functions
- **Docstrings**: Google-style docstrings for all public APIs
- **Tests**: All new features must include unit tests
- **Coverage**: Maintain > 80% test coverage

### Code Quality Tools

```bash
# Format code
black pii_detector/

# Lint code
ruff check pii_detector/

# Type checking
mypy pii_detector/

# Run all checks
pytest && black --check pii_detector/ && ruff check pii_detector/
```

### Bug Reporting

Use [GitHub Issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues) with the bug template.

Include:
- Python version
- OS and architecture
- Configuration files (sanitized)
- Error messages and stack traces
- Steps to reproduce

## License

This project is licensed under the **MIT License** - see the [LICENSE.md](../LICENSE.md) file for details.

Copyright © 2025 Softcom Technologies Organization

## Support

- 📧 **Email**: team@sentinelle.com
- 🐛 **Issues**: [GitHub Issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues)
- 📖 **Documentation**: [docs/](docs/)
- 🌐 **Website**: [AI Sentinel Project](https://github.com/Softcom-Technologies-Organization/ai-sentinel)

### Maintainers

- [@Softcom-Technologies-Organization](https://github.com/Softcom-Technologies-Organization) - Core Team

## Acknowledgments

- [Microsoft Presidio](https://microsoft.github.io/presidio/) - Production-grade PII detection framework
- [GLiNER](https://github.com/urchade/GLiNER) - Zero-shot NER model
- [Hugging Face](https://huggingface.co/) - Model hosting and Transformers library
- All [contributors](https://github.com/Softcom-Technologies-Organization/ai-sentinel/graphs/contributors) to the AI Sentinel project

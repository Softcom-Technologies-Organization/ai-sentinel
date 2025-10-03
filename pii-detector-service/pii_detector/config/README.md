# Configuration Module

Centralized configuration system for the PII Detector microservice, organized by responsibility domain.

## Architecture

The configuration system is split into **5 domain-specific modules**:

### 1. **server_config.py** - gRPC Server Configuration
Controls gRPC server behavior, connection settings, and resource limits.

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `GRPC_PORT` | 50051 | Server port (1024-65535) |
| `GRPC_MAX_WORKERS` | 10 | Maximum worker threads |
| `GRPC_MAX_CONCURRENT_RPCS` | None | Maximum concurrent RPCs |
| `GRPC_ENABLE_REFLECTION` | false | Enable gRPC reflection for debugging |
| `GRPC_KEEPALIVE_TIME_MS` | 300000 | Keepalive ping interval (milliseconds) |
| `GRPC_KEEPALIVE_TIMEOUT_MS` | 20000 | Keepalive timeout (milliseconds) |
| `GRPC_MAX_CONNECTION_IDLE_MS` | 300000 | Maximum connection idle time |
| `GRPC_GRACE_PERIOD_SECONDS` | 30 | Graceful shutdown period |

### 2. **model_config.py** - ML Model Configuration
Controls model loading, HuggingFace authentication, and ONNX optimization.

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `HUGGING_FACE_API_KEY` | **(required)** | HuggingFace Hub API key |
| `MODEL_NAME` | piiranha-ai/piiranha | Model name or path |
| `USE_ONNX` | false | Enable ONNX runtime optimization |
| `ONNX_MODEL_PATH` | ./model/piiranha.onnx | Path to ONNX model file |
| `DEVICE` | cpu | Computation device (cpu/cuda/mps/auto) |
| `MODEL_CACHE_DIR` | None | Custom model cache directory |
| `TRUST_REMOTE_CODE` | false | Allow remote code execution |
| `LOCAL_FILES_ONLY` | false | Use only local cached models |
| `MAX_MODEL_LOAD_RETRIES` | 3 | Maximum model load retries |
| `MODEL_LOAD_TIMEOUT_SECONDS` | 300 | Model load timeout |

### 3. **detection_config.py** - PII Detection Logic Configuration
Controls detection behavior, thresholds, and preprocessing options.

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `DETECTOR_FAST_PRECHECK` | true | Enable fast regex precheck |
| `CONFIDENCE_THRESHOLD` | 0.5 | Minimum confidence score (0.0-1.0) |
| `ENABLE_EMAIL_EXPANSION` | true | Enable email address expansion |
| `EMAIL_PATTERNS` | "" | Comma-separated additional email patterns |
| `MAX_TEXT_LENGTH` | 1000000 | Maximum text length (characters) |
| `CHUNK_SIZE` | 5000 | Text chunk size for large documents |
| `ENABLE_CONTEXT_WINDOW` | false | Use context window around entities |
| `CONTEXT_WINDOW_SIZE` | 50 | Context window size (characters) |
| `PII_TYPES` | "" | Comma-separated PII types (empty = all) |
| `ANONYMIZATION_CHAR` | * | Character for anonymization |

**Valid PII Types**: EMAIL, PHONE, SSN, CREDIT_CARD, IP_ADDRESS, PERSON, LOCATION, ORGANIZATION, DATE, URL

### 4. **performance_config.py** - Performance & Resource Management
Controls memory usage, caching, batch processing, and optimization.

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `MEMORY_LIMIT_MB` | 2048 | Maximum memory usage (MB) |
| `ENABLE_MODEL_CACHING` | true | Enable result caching |
| `CACHE_MAX_SIZE` | 1000 | Maximum cached results |
| `CACHE_TTL_SECONDS` | 3600 | Cache time-to-live |
| `BATCH_SIZE` | 8 | Batch processing size |
| `ENABLE_GPU` | false | Enable GPU acceleration |
| `NUM_THREADS` | 4 | Number of CPU threads |
| `ENABLE_MEMORY_MONITORING` | true | Enable memory monitoring |
| `MEMORY_WARNING_THRESHOLD_MB` | 1536 | Memory warning threshold |
| `ENABLE_GC_OPTIMIZATION` | true | Enable GC optimization |
| `GC_THRESHOLD_MULTIPLIER` | 2.0 | GC threshold multiplier |

### 5. **logging_config.py** - Logging Configuration
Controls logging levels, formats, handlers, and output destinations.

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `LOG_LEVEL` | INFO | Logging level (DEBUG/INFO/WARNING/ERROR/CRITICAL) |
| `LOG_FORMAT` | *(standard format)* | Custom log format string |
| `LOG_DATE_FORMAT` | %Y-%m-%d %H:%M:%S | Date format for timestamps |
| `ENABLE_FILE_LOGGING` | false | Enable logging to file |
| `LOG_FILE_PATH` | ./logs/pii-detector.log | Log file path |
| `LOG_FILE_MAX_BYTES` | 10485760 | Maximum log file size (10MB) |
| `LOG_FILE_BACKUP_COUNT` | 5 | Number of backup log files |
| `ENABLE_JSON_LOGGING` | false | Enable JSON structured logging |
| `ENABLE_CONSOLE_LOGGING` | true | Enable console logging |
| `LOG_GRPC_DETAILS` | false | Log detailed gRPC info |
| `LOG_SENSITIVE_DATA` | false | ⚠️ Log sensitive PII (dev only!) |

## Usage

### Basic Usage

```python
from pii_detector.config import config

# Access configuration by domain
print(f"Server running on port: {config.server.port}")
print(f"Model name: {config.model.model_name}")
print(f"Fast precheck enabled: {config.detection.enable_fast_precheck}")
print(f"Memory limit: {config.performance.memory_limit_mb}MB")
print(f"Log level: {config.logging.log_level}")
```

### Validation

Configuration is automatically validated on load:

```python
from pii_detector.config import get_config

try:
    config = get_config()
    print("✓ Configuration loaded and validated")
except ValueError as e:
    print(f"✗ Configuration error: {e}")
```

### Configuration Summary

```python
from pii_detector.config import config

# Print formatted configuration summary
print(config.print_summary())
```

Output:
```
============================================================
PII Detector Microservice Configuration
============================================================

SERVER CONFIGURATION:
  Port: 50051
  Max Workers: 10
  Reflection Enabled: False

MODEL CONFIGURATION:
  Model Name: piiranha-ai/piiranha
  Device: cpu
  ONNX Enabled: False

DETECTION CONFIGURATION:
  Fast Precheck: True
  Confidence Threshold: 0.5
  Email Expansion: True
  Max Text Length: 1000000

PERFORMANCE CONFIGURATION:
  Memory Limit: 2048MB
  Caching Enabled: True
  Batch Size: 8
  GPU Enabled: False

LOGGING CONFIGURATION:
  Log Level: INFO
  Console Logging: True
  File Logging: False
============================================================
```

### Reloading Configuration

For testing or dynamic updates:

```python
from pii_detector.config import reload_config
import os

# Change environment variable
os.environ['GRPC_PORT'] = '50052'

# Reload configuration
config = reload_config()
print(f"New port: {config.server.port}")  # 50052
```

### Domain-Specific Access

```python
from pii_detector.config import ServerConfig, ModelConfig

# Load specific domain
server_config = ServerConfig.from_env()
server_config.validate()

print(f"Port: {server_config.port}")
print(f"Workers: {server_config.max_workers}")
```

## Benefits

### ✅ Separation of Concerns
Each configuration domain is isolated in its own file, making it easy to find and modify specific settings.

### ✅ Type Safety
All configurations use Python dataclasses with type hints, enabling IDE autocomplete and type checking.

### ✅ Validation
Each domain validates its configuration values, catching errors early at startup rather than during runtime.

### ✅ Documentation
Every environment variable is documented with defaults and descriptions directly in the code.

### ✅ Testability
Configuration can be easily mocked or overridden for testing:

```python
from pii_detector.config import AppConfig, ServerConfig, ModelConfig

# Create test configuration
test_config = AppConfig(
    server=ServerConfig(port=50099, max_workers=1, ...),
    model=ModelConfig(model_name="test-model", ...),
    ...
)
```

### ✅ Singleton Pattern
Global configuration instance ensures consistent settings across the application.

## Migration Guide

### Before (Scattered Configuration)

```python
# Old code - environment variables read directly
import os

port = int(os.getenv("GRPC_PORT", "50051"))
model_name = os.getenv("MODEL_NAME", "piiranha-ai/piiranha")
enable_precheck = os.getenv("DETECTOR_FAST_PRECHECK", "1") == "1"
```

### After (Centralized Configuration)

```python
# New code - centralized configuration
from pii_detector.config import config

port = config.server.port
model_name = config.model.model_name
enable_precheck = config.detection.enable_fast_precheck
```

## Environment File Example

Create a `.env` file for local development:

```bash
# Server Configuration
GRPC_PORT=50051
GRPC_MAX_WORKERS=10
GRPC_ENABLE_REFLECTION=true

# Model Configuration
HUGGING_FACE_API_KEY=hf_your_key_here
MODEL_NAME=piiranha-ai/piiranha
DEVICE=cpu

# Detection Configuration
DETECTOR_FAST_PRECHECK=true
CONFIDENCE_THRESHOLD=0.5
ENABLE_EMAIL_EXPANSION=true

# Performance Configuration
MEMORY_LIMIT_MB=2048
ENABLE_MODEL_CACHING=true
BATCH_SIZE=8

# Logging Configuration
LOG_LEVEL=INFO
ENABLE_CONSOLE_LOGGING=true
ENABLE_FILE_LOGGING=false
```

Load with:
```bash
# Linux/Mac
export $(cat .env | xargs)

# Windows PowerShell
Get-Content .env | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
```

## Docker Integration

In `docker-compose.yml`:

```yaml
services:
  pii-detector:
    environment:
      # Server
      - GRPC_PORT=50051
      - GRPC_MAX_WORKERS=10
      
      # Model
      - HUGGING_FACE_API_KEY=${HUGGING_FACE_API_KEY}
      - MODEL_NAME=piiranha-ai/piiranha
      
      # Detection
      - DETECTOR_FAST_PRECHECK=true
      - CONFIDENCE_THRESHOLD=0.5
      
      # Performance
      - MEMORY_LIMIT_MB=2048
      - ENABLE_MODEL_CACHING=true
      
      # Logging
      - LOG_LEVEL=INFO
      - ENABLE_FILE_LOGGING=true
      - LOG_FILE_PATH=/app/logs/pii-detector.log
```

## Best Practices

1. **Always validate configuration at startup**:
   ```python
   config = get_config()  # Automatically validates
   ```

2. **Use environment-specific .env files**:
   - `.env.development`
   - `.env.production`
   - `.env.test`

3. **Never commit sensitive values** (e.g., API keys) to version control

4. **Document custom environment variables** in this README when adding new configuration

5. **Use the configuration summary** in logs during startup:
   ```python
   import logging
   logger = logging.getLogger(__name__)
   logger.info(config.print_summary())
   ```

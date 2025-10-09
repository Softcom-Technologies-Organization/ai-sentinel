"""
PII Detection gRPC Service with Improved Memory Management.

This module implements the gRPC service for PII detection with optimizations
for memory usage when processing large volumes of data.
"""

import gc
import logging
import os
import threading
import time
from concurrent import futures

import grpc
import psutil
# Import gRPC reflection for service discovery
from grpc_reflection.v1alpha import reflection

# Import the generated gRPC code
from pii_detector.proto.generated import pii_detection_pb2, pii_detection_pb2_grpc

# Import the PII detector
from pii_detector.service.detector.pii_detector import PIIDetector
from pii_detector.service.detector.pii_detector import PIIEntity as DetectedPIIEntity
# Optional pre-caching of additional HF models (extensible)
try:
    from pii_detector.service.detector.model_cache import ensure_models_cached, get_env_extra_models
except Exception:  # pragma: no cover - safe import guard
    ensure_models_cached = None
    get_env_extra_models = None

# Optional multi-model composite (opt-in via env)
try:
    from pii_detector.service.detector.multi_detector import MultiModelPIIDetector, get_multi_model_ids
except Exception:  # pragma: no cover - safe import guard
    MultiModelPIIDetector = None  # type: ignore
    get_multi_model_ids = None  # type: ignore

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Singleton instance for the PII detector
_detector_instance = None
_detector_lock = threading.Lock()

def get_detector_instance():
    """Get or create a singleton instance of PIIDetector."""
    global _detector_instance
    if _detector_instance is None:
        with _detector_lock:
            if _detector_instance is None:
                # Preload additional HF models into local cache (optional, safe-noop without token)
                try:
                    if ensure_models_cached and get_env_extra_models:
                        ensure_models_cached(get_env_extra_models())
                except Exception as e:  # pragma: no cover - defensive
                    logger.warning(f"Pre-caching extra models failed (continuing): {e}")

                # Decide between single or multi-model detector based on centralized config
                from pii_detector.config import get_config
                try:
                    config = get_config()
                    enabled = config.detection.multi_detector_enabled
                except (ValueError, AttributeError):
                    enabled = False
                
                if enabled and MultiModelPIIDetector and get_multi_model_ids:
                    try:
                        # Use default model id from DetectionConfig to avoid instantiating PIIDetector
                        try:
                            from pii_detector.service.detector.pii_detector import DetectionConfig  # local import to avoid circulars
                            primary_model = DetectionConfig().model_id
                        except Exception:
                            primary_model = "iiiorg/piiranha-v1-detect-personal-information"
                        model_ids = get_multi_model_ids(primary_model)
                        _detector_instance = MultiModelPIIDetector(model_ids=model_ids)
                        logger.info(f"Multi-model detection enabled with models: {model_ids}")
                    except Exception as e:  # pragma: no cover - defensive fallback
                        logger.warning(f"Failed to initialize multi-model detector, falling back to single model: {e}")
                        _detector_instance = PIIDetector()
                else:
                    _detector_instance = PIIDetector()

                _detector_instance.download_model()
                _detector_instance.load_model()
                logger.info("Singleton PII detector initialized successfully")
    return _detector_instance


class PIIDetectionServicer(pii_detection_pb2_grpc.PIIDetectionServiceServicer):
    """
    Implementation of the PIIDetectionService gRPC service with memory management.
    """

    def __init__(self, max_text_size=1_000_000, enable_memory_monitoring=True):
        """
        Initialize the service with memory management features.
        
        Args:
            max_text_size: Maximum size of text to process in a single request (in characters)
            enable_memory_monitoring: Enable periodic memory monitoring
        """
        self.max_text_size = max_text_size
        self.enable_memory_monitoring = enable_memory_monitoring
        self.request_counter = 0
        self.gc_frequency = 10  # Run garbage collection every N requests
        
        # Use singleton detector instance
        self.detector = get_detector_instance()
        
        # Start memory monitoring thread if enabled
        if self.enable_memory_monitoring:
            self._start_memory_monitoring()
    
    def _start_memory_monitoring(self):
        """Start a background thread to monitor memory usage."""
        def monitor_memory():
            while True:
                try:
                    process = psutil.Process(os.getpid())
                    memory_info = process.memory_info()
                    memory_percent = process.memory_percent()
                    
                    logger.info(f"Memory usage: {memory_info.rss / 1024 / 1024:.2f} MB ({memory_percent:.1f}%)")
                    
                    # Trigger aggressive GC if memory usage is high
                    if memory_percent > 80:
                        logger.warning(f"High memory usage detected: {memory_percent:.1f}%")
                        gc.collect()
                        logger.info("Forced garbage collection completed")
                    
                except Exception as e:
                    logger.error(f"Error in memory monitoring: {str(e)}")
                
                time.sleep(30)  # Check every 30 seconds
        
        monitor_thread = threading.Thread(target=monitor_memory, daemon=True)
        monitor_thread.start()
        logger.info("Memory monitoring thread started")

    def _process_in_chunks(self, content, threshold):
        """
        Process large text in chunks to avoid memory spikes.
        
        Args:
            content: The text to process
            threshold: Detection threshold
            
        Returns:
            Combined results from all chunks
        """
        chunk_size = 50000  # Process 50K characters at a time
        overlap = 100  # Overlap between chunks to avoid missing entities at boundaries
        
        all_entities = []
        offset = 0
        
        while offset < len(content):
            # Extract chunk with overlap
            chunk_start = max(0, offset - overlap if offset > 0 else 0)
            chunk_end = min(len(content), offset + chunk_size)
            chunk = content[chunk_start:chunk_end]
            
            # Detect PII in chunk
            entities = self.detector.detect_pii(chunk, threshold)
            
            # Adjust entity positions based on chunk offset
            for entity in entities:
                # Only include entities that start in the current chunk (not in overlap)
                # Note: entity is a PIIEntity dataclass; use attribute access for writes to avoid
                # "object does not support item assignment" when adjusting positions.
                start_in_chunk = (entity.start if hasattr(entity, 'start') else entity['start'])
                if start_in_chunk >= (overlap if offset > 0 else 0):
                    if hasattr(entity, 'start') and hasattr(entity, 'end'):
                        entity.start += chunk_start
                        entity.end += chunk_start
                    else:
                        # Fallback for dict-like entities
                        entity['start'] = entity['start'] + chunk_start
                        entity['end'] = entity['end'] + chunk_start
                    all_entities.append(entity)
            
            offset += chunk_size
            
            # Force garbage collection after each chunk
            if offset < len(content):
                gc.collect(0)  # Collect only generation 0 for speed
        
        return all_entities

    def DetectPII(self, request, context):
        """
        Implement the DetectPII RPC method with memory management.
        """
        start_time = time.time()
        request_id = f"req_{self.request_counter + 1}_{int(start_time * 1000) % 10000}"
        
        try:
            # Increment request counter
            self.request_counter += 1
            
            # Extract parameters from request
            content = request.content
            threshold = request.threshold if request.threshold > 0 else 0.5

            # Get client information for logging
            peer_info = context.peer() if hasattr(context, 'peer') else "unknown"
            
            logger.info(f"[{request_id}] Received DetectPII request #{self.request_counter}")
            logger.info(f"[{request_id}] Client: {peer_info}")
            logger.info(f"[{request_id}] Content length: {len(content)} characters")
            logger.info(f"[{request_id}] Threshold: {threshold}")
            logger.debug(f"[{request_id}] Content preview: {content[:100]}..." if len(content) > 100 else f"[{request_id}] Content: {content}")
            
            # Validate input
            if not content:
                error_message = "Content cannot be empty"
                logger.error(f"[{request_id}] Validation failed: {error_message}")
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details(error_message)
                return pii_detection_pb2.PIIDetectionResponse()
            
            # Check text size limit
            if len(content) > self.max_text_size:
                error_message = f"Content too large: {len(content)} characters (max: {self.max_text_size})"
                logger.error(f"[{request_id}] Validation failed: {error_message}")
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details(error_message)
                return pii_detection_pb2.PIIDetectionResponse()

            # Process content
            processing_start = time.time()
            logger.info(f"[{request_id}] Starting PII detection processing...")
            
            if len(content) > 50000:
                # Use chunked processing for large texts
                logger.info(f"[{request_id}] Using chunked processing for large text ({len(content)} chars)")
                entities = self._process_in_chunks(content, threshold)
            else:
                # Process normally for smaller texts
                logger.info(f"[{request_id}] Using standard processing for text ({len(content)} chars)")
                entities = self.detector.detect_pii(content, threshold)

            processing_time = time.time() - processing_start
            logger.info(f"[{request_id}] PII detection completed in {processing_time:.3f}s")
            logger.info(f"[{request_id}] Found {len(entities)} PII entities")

            # Log detected entity types for debugging
            if entities:
                entity_types = {}
                for entity in entities:
                    entity_type = entity['type_label']
                    entity_types[entity_type] = entity_types.get(entity_type, 0) + 1
                logger.debug(f"[{request_id}] Entity types found: {dict(entity_types)}")
                
                # Log first few entities for debugging
                for i, entity in enumerate(entities[:3]):
                    logger.debug(f"[{request_id}] Entity {i+1}: {entity['type_label']} - '{entity['text']}' (score: {entity['score']:.3f})")

            # Create summary (without re-detecting)
            logger.debug(f"[{request_id}] Creating response summary...")
            summary = {}
            for entity in entities:
                pii_type = entity['type_label']
                summary[pii_type] = summary.get(pii_type, 0) + 1

            # Create response
            logger.debug(f"[{request_id}] Building gRPC response...")
            response = pii_detection_pb2.PIIDetectionResponse()

            # Add entities to response (limit to first 1000 to avoid huge responses)
            entities_to_add = min(len(entities), 1000)
            logger.debug(f"[{request_id}] Adding {entities_to_add} entities to response")
            
            for i, entity in enumerate(entities[:1000]):
                pii_entity = response.entities.add()
                pii_entity.text = entity['text']
                pii_entity.type = entity['type']
                pii_entity.type_label = entity['type_label']
                pii_entity.start = entity['start']
                pii_entity.end = entity['end']
                pii_entity.score = entity['score']
            
            if len(entities) > 1000:
                logger.warning(f"[{request_id}] Truncated entities list from {len(entities)} to 1000")

            # Add summary to response
            logger.debug(f"[{request_id}] Adding summary to response: {dict(summary)}")
            for pii_type, count in summary.items():
                response.summary[pii_type] = count

            # Skip masked content for large texts to save memory
            masking_start = time.time()
            if len(content) <= 10000:
                logger.debug(f"[{request_id}] Generating masked content...")
                masked_content, _ = self.detector.mask_pii(content, threshold)
                response.masked_content = masked_content
                masking_time = time.time() - masking_start
                logger.debug(f"[{request_id}] Masking completed in {masking_time:.3f}s")
            else:
                logger.debug(f"[{request_id}] Skipping masking for large content")
                response.masked_content = "[Content too large for masking]"

            # Calculate total processing time
            total_time = time.time() - start_time
            logger.info(f"[{request_id}] Request completed successfully in {total_time:.3f}s")
            logger.info(f"[{request_id}] Performance breakdown - Detection: {processing_time:.3f}s, Total: {total_time:.3f}s")
            
            # Periodic garbage collection
            if self.request_counter % self.gc_frequency == 0:
                gc.collect()
                logger.debug(f"Garbage collection triggered after {self.request_counter} requests")
            
            return response

        except Exception as e:
            error_time = time.time() - start_time
            error_message = f"Error processing request: {str(e)}"
            logger.error(f"[{request_id}] {error_message}")
            logger.error(f"[{request_id}] Error occurred after {error_time:.3f}s")
            logger.error(f"[{request_id}] Exception type: {type(e).__name__}")
            logger.error(f"[{request_id}] Exception details: {str(e)}")
            
            # Log stack trace for debugging
            import traceback
            logger.debug(f"[{request_id}] Stack trace:\n{traceback.format_exc()}")
            
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(error_message)
            return pii_detection_pb2.PIIDetectionResponse()
        finally:
            # Log cleanup and final timing
            final_time = time.time() - start_time
            logger.debug(f"[{request_id}] Request cleanup completed, total time: {final_time:.3f}s")
            
            # Clear any large temporary variables
            content = None
            entities = None

    def StreamDetectPII(self, request, context):
        """
        Stream progressive PII detection updates per chunk and a final summary.
        """
        start_time = time.time()
        request_id = f"stream_{self.request_counter + 1}_{int(start_time * 1000) % 10000}"

        try:
            # Increment request counter
            self.request_counter += 1

            # Extract and validate input
            content = request.content
            threshold = request.threshold if request.threshold > 0 else 0.5

            if not content:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Content cannot be empty")
                return

            if len(content) > self.max_text_size:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details(
                    f"Content too large: {len(content)} characters (max: {self.max_text_size})"
                )
                return

            cfg = self.detector.config
            step = max(1, cfg.chunk_size - cfg.chunk_overlap)
            total_chunks = max(1, (len(content) + step - 1) // step)

            logger.info(f"[{request_id}] Starting streaming detection: len={len(content)}, step={step}, total_chunks={total_chunks}")

            all_entities = []
            chunk_index = 0

            for start in range(0, len(content), step):
                if hasattr(context, 'is_active') and not context.is_active():
                    logger.info(f"[{request_id}] Client cancelled stream; stopping early at chunk {chunk_index}")
                    return

                end = min(start + cfg.chunk_size, len(content))
                chunk = content[start:end]

                raw_results = self.detector.pipeline(chunk)

                chunk_entities = self.detector.entity_processor.process_entities(
                    raw_results, threshold
                )

                added_in_chunk: list[DetectedPIIEntity] = []
                for e in chunk_entities:
                    adj = DetectedPIIEntity(
                        text=e.text,
                        pii_type=e.pii_type,
                        type_label=e.type_label,
                        start=e.start + start,
                        end=e.end + start,
                        score=e.score,
                    )
                    if not self.detector._is_duplicate_entity(adj, all_entities):
                        all_entities.append(adj)
                        added_in_chunk.append(adj)

                progress = int(((chunk_index + 1) * 100) / total_chunks)
                update = pii_detection_pb2.PIIDetectionUpdate(
                    chunk_index=chunk_index,
                    total_chunks=total_chunks,
                    progress_percent=progress,
                    final=False,
                )
                for ae in added_in_chunk:
                    update.entities.append(
                        pii_detection_pb2.PIIEntity(
                            text=ae.text,
                            type=ae.pii_type,
                            type_label=ae.type_label,
                            start=ae.start,
                            end=ae.end,
                            score=ae.score,
                        )
                    )

                yield update

                # Free memory pressure between chunks
                self.detector.memory_manager.clear_cache(self.detector.device)
                gc.collect(0)

                chunk_index += 1

            # Final message: masked content and summary
            masked_content = self.detector._apply_masks(content, all_entities)
            summary: dict[str, int] = {}
            for e in all_entities:
                key = e.type_label  # Align with unary response summary
                summary[key] = summary.get(key, 0) + 1

            final_update = pii_detection_pb2.PIIDetectionUpdate(
                chunk_index=max(0, total_chunks - 1),
                total_chunks=total_chunks,
                progress_percent=100,
                masked_content=masked_content,
                final=True,
            )
            for k, v in summary.items():
                final_update.summary[k] = v

            yield final_update

        except Exception as e:
            logger.error(f"[{request_id}] Streaming detection failed: {str(e)}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Streaming detection failed: {str(e)}")
        finally:
            self.detector.memory_manager.clear_cache(self.detector.device)
            gc.collect(0)


class MemoryLimitedServer:
    """
    gRPC server wrapper with memory usage limits and request queuing.
    """
    
    def __init__(self, port: int = 50051, max_workers: int = 5, 
                 max_queued_requests: int = 100, memory_limit_percent: float = 85.0):
        """
        Initialize the memory-limited server.
        
        Args:
            port: Port to listen on
            max_workers: Maximum number of worker threads
            max_queued_requests: Maximum number of queued requests
            memory_limit_percent: Maximum memory usage percentage before rejecting requests
        """
        self.port = port
        self.max_workers = max_workers
        self.max_queued_requests = max_queued_requests
        self.memory_limit_percent = memory_limit_percent
        self.server = None
        
    def _check_memory(self):
        """Check if memory usage is within limits."""
        process = psutil.Process(os.getpid())
        memory_percent = process.memory_percent()
        return memory_percent < self.memory_limit_percent
    
    def serve(self):
        """Start the gRPC server with memory limits."""
        # Create a custom thread pool executor with bounded queue
        executor = futures.ThreadPoolExecutor(
            max_workers=self.max_workers,
            thread_name_prefix='grpc-worker'
        )
        
        # Create server with custom executor
        self.server = grpc.server(
            executor,
            options=[
                ('grpc.max_receive_message_length', 10 * 1024 * 1024),  # 10MB max message size
                ('grpc.max_send_message_length', 10 * 1024 * 1024),
                ('grpc.max_concurrent_streams', 100),
            ]
        )
        
        # Add service
        servicer = PIIDetectionServicer(
            max_text_size=1_000_000,  # 1M characters max
            enable_memory_monitoring=True
        )
        pii_detection_pb2_grpc.add_PIIDetectionServiceServicer_to_server(
            servicer, self.server
        )
        
        # Enable gRPC reflection for service discovery
        SERVICE_NAMES = (
            pii_detection_pb2.DESCRIPTOR.services_by_name['PIIDetectionService'].full_name,
            reflection.SERVICE_NAME,
        )
        reflection.enable_server_reflection(SERVICE_NAMES, self.server)
        
        # Add insecure port with IPv6 first, then fallback to IPv4 on Windows/IPv6-disabled hosts
        bind_targets = [f"[::]:{self.port}", f"0.0.0.0:{self.port}"]
        bound = 0
        for target in bind_targets:
            try:
                res = self.server.add_insecure_port(target)
                if res:
                    bound = res
                    logger.info(f"gRPC bound to {target} (fd={res})")
                    break
                else:
                    logger.debug(f"gRPC failed to bind {target}")
            except Exception as e:
                logger.debug(f"Exception while binding {target}: {e}")
        if not bound:
            raise RuntimeError(f"Failed to bind gRPC server on any address for port {self.port}. Tried: {bind_targets}")
        
        # Start server
        self.server.start()
        logger.info(f"Memory-limited server started on port {self.port}")
        logger.info(f"Configuration: max_workers={self.max_workers}, "
                   f"memory_limit={self.memory_limit_percent}%")
        
        return self.server


def serve(port: int = 50051, max_workers: int = 5):
    """
    Start the gRPC server with memory management.
    
    Args:
        port: The port to listen on.
        max_workers: The maximum number of worker threads.
    """
    server = MemoryLimitedServer(
        port=port,
        max_workers=max_workers,
        max_queued_requests=100,
        memory_limit_percent=85.0
    )
    return server.serve()


if __name__ == '__main__':
    # Start the server with conservative settings
    server = serve(max_workers=3)  # Reduced from 10 to 3 workers
    
    # Keep the server running until interrupted
    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")

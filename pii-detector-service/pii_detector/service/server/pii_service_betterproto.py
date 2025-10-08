"""
PII Detection Service using betterproto implementation.

This module provides a gRPC service for detecting Personally Identifiable Information (PII)
in text content using the betterproto library for modern Python gRPC implementation.
"""

import logging
import gc
import psutil
import os
import time
import asyncio
from typing import Optional
import threading
from queue import Queue, Full

# Import betterproto classes
from pii_detector.pii_detection import PIIDetectionRequest, PIIDetectionResponse, PIIEntity
import betterproto

# Import grpclib for async gRPC server
import grpclib.server
import grpclib.const
from grpclib.utils import graceful_exit

# Import the PII detector
from pii_detector.service.detector.pii_detector import PIIDetector

# Configure logging
logger = logging.getLogger(__name__)

# Global detector instance for memory efficiency
_detector_instance = None
_detector_lock = threading.Lock()

def get_detector_instance():
    """Get or create a singleton detector instance."""
    global _detector_instance
    if _detector_instance is None:
        with _detector_lock:
            if _detector_instance is None:
                logger.info("Creating new PII detector instance...")
                _detector_instance = PIIDetector()
                logger.info("PII detector instance created successfully")
    return _detector_instance


class PIIDetectionService:
    """
    Betterproto-based PII Detection Service.
    
    This service provides PII detection capabilities using modern async/await syntax
    and betterproto message classes. This implements the PIIDetectionService gRPC service.
    """
    
    def __mapping__(self):
        """Return the gRPC method mapping for this service."""
        return {
            '/pii_detection.PIIDetectionService/DetectPII': grpclib.const.Handler(
                self.detect_pii,
                grpclib.const.Cardinality.UNARY_UNARY,
                PIIDetectionRequest,
                PIIDetectionResponse,
            )
        }

    def __init__(self, max_text_size: int = 1_000_000, enable_memory_monitoring: bool = True):
        """
        Initialize the PII Detection Service.
        
        Args:
            max_text_size: Maximum allowed text size in characters
            enable_memory_monitoring: Whether to enable memory monitoring
        """
        self.detector = get_detector_instance()
        self.max_text_size = max_text_size
        self.request_counter = 0
        self.enable_memory_monitoring = enable_memory_monitoring
        
        if self.enable_memory_monitoring:
            self._start_memory_monitoring()
        
        logger.info(f"PIIDetectionService initialized with max_text_size={max_text_size}")

    def _start_memory_monitoring(self):
        """Start background memory monitoring."""
        def monitor_memory():
            while True:
                try:
                    process = psutil.Process()
                    memory_info = process.memory_info()
                    memory_percent = process.memory_percent()
                    
                    if memory_percent > 80:
                        logger.warning(f"High memory usage: {memory_percent:.1f}% ({memory_info.rss / 1024 / 1024:.1f} MB)")
                        gc.collect()
                    
                    time.sleep(30)  # Check every 30 seconds
                except Exception as e:
                    logger.error(f"Memory monitoring error: {e}")
                    time.sleep(60)  # Wait longer on error

        monitor_thread = threading.Thread(target=monitor_memory, daemon=True)
        monitor_thread.start()
        logger.info("Memory monitoring started")

    def _process_in_chunks(self, content: str, threshold: float) -> list:
        """
        Process large text content in chunks to manage memory usage.
        
        Args:
            content: Text content to process
            threshold: Confidence threshold for PII detection
            
        Returns:
            List of detected PII entities
        """
        chunk_size = 10000  # Process 10k characters at a time
        overlap = 200  # Overlap to catch entities at chunk boundaries
        all_entities = []
        offset = 0
        
        logger.debug(f"Processing {len(content)} characters in chunks of {chunk_size}")
        
        while offset < len(content):
            # Calculate chunk boundaries
            end = min(offset + chunk_size, len(content))
            chunk = content[offset:end]
            
            # Process chunk
            logger.debug(f"Processing chunk {offset}-{end}")
            entities = self.detector.detect_pii(chunk, threshold)
            
            # Adjust entity positions to global coordinates
            for entity in entities:
                # Skip entities that are too close to chunk boundaries (likely incomplete)
                if offset > 0 and entity['start'] < overlap // 2:
                    continue
                if end < len(content) and entity['end'] > len(chunk) - overlap // 2:
                    continue
                    
                entity['start'] += offset
                entity['end'] += offset
                all_entities.append(entity)
            
            offset += chunk_size
            
            # Force garbage collection after each chunk
            if offset < len(content):
                gc.collect(0)  # Collect only generation 0 for speed
        
        return all_entities

    async def detect_pii(self, request: PIIDetectionRequest) -> PIIDetectionResponse:
        """
        Detect PII in the provided text content.
        
        Args:
            request: PIIDetectionRequest containing content and threshold
            
        Returns:
            PIIDetectionResponse with detected entities and summary
        """
        start_time = time.time()
        request_id = f"req_{self.request_counter + 1}_{int(start_time * 1000) % 10000}"
        
        try:
            # Increment request counter
            self.request_counter += 1
            
            # Extract parameters from request
            content = request.content
            threshold = request.threshold if request.threshold > 0 else 0.5

            logger.info(f"[{request_id}] Received DetectPII request #{self.request_counter}")
            logger.info(f"[{request_id}] Content length: {len(content)} characters")
            logger.info(f"[{request_id}] Threshold: {threshold}")
            logger.debug(f"[{request_id}] Content preview: {content[:100]}..." if len(content) > 100 else f"[{request_id}] Content: {content}")
            
            # Validate input
            if not content:
                error_message = "Content cannot be empty"
                logger.error(f"[{request_id}] Validation failed: {error_message}")
                return PIIDetectionResponse()
            
            # Check text size limit
            if len(content) > self.max_text_size:
                error_message = f"Content too large: {len(content)} characters (max: {self.max_text_size})"
                logger.error(f"[{request_id}] Validation failed: {error_message}")
                return PIIDetectionResponse()

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

            # Create summary
            summary_start = time.time()
            logger.debug(f"[{request_id}] Creating response summary...")
            summary = {}
            for entity in entities:
                pii_type = entity['type_label']
                summary[pii_type] = summary.get(pii_type, 0) + 1

            # Create betterproto entities
            response_start = time.time()
            logger.debug(f"[{request_id}] Building betterproto response...")
            
            # Convert entities to betterproto PIIEntity objects (limit to first 1000)
            entities_to_add = min(len(entities), 1000)
            logger.debug(f"[{request_id}] Adding {entities_to_add} entities to response")
            
            pii_entities = []
            for i, entity in enumerate(entities[:1000]):
                pii_entity = PIIEntity(
                    text=entity['text'],
                    type=entity['type'],
                    type_label=entity['type_label'],
                    start=entity['start'],
                    end=entity['end'],
                    score=entity['score']
                )
                pii_entities.append(pii_entity)
            
            if len(entities) > 1000:
                logger.warning(f"[{request_id}] Truncated entities list from {len(entities)} to 1000")

            # Generate masked content for smaller texts
            masked_content = ""
            masking_start = time.time()
            if len(content) <= 10000:
                logger.debug(f"[{request_id}] Generating masked content...")
                masked_content, _ = self.detector.mask_pii(content, threshold)
                masking_time = time.time() - masking_start
                logger.debug(f"[{request_id}] Masking completed in {masking_time:.3f}s")
            else:
                logger.debug(f"[{request_id}] Skipping masked content for large text")

            # Create response using betterproto
            response = PIIDetectionResponse(
                entities=pii_entities,
                summary=summary,
                masked_content=masked_content
            )

            # Log response statistics
            response_time = time.time() - response_start
            total_time = time.time() - start_time
            logger.info(f"[{request_id}] Response built in {response_time:.3f}s")
            logger.info(f"[{request_id}] Total request time: {total_time:.3f}s")
            logger.info(f"[{request_id}] Response summary: {len(pii_entities)} entities, {len(summary)} types")

            # Force garbage collection for large requests
            if len(content) > 10000:
                gc.collect()

            return response

        except Exception as e:
            error_msg = f"Error processing request: {str(e)}"
            logger.error(f"[{request_id}] {error_msg}", exc_info=True)
            
            # Log additional context for debugging
            logger.error(f"[{request_id}] Request details - Content length: {len(content) if 'content' in locals() else 'unknown'}, Threshold: {threshold if 'threshold' in locals() else 'unknown'}")
            logger.error(f"[{request_id}] Exception type: {type(e).__name__}")
            
            # Return response with error information
            from grpclib.exceptions import GRPCError
            from grpclib.const import Status
            raise GRPCError(Status.INTERNAL, error_msg)


async def serve_betterproto(port: int = 50051):
    """
    Start the betterproto-based gRPC server.
    
    Args:
        port: Port to listen on
    """
    # Create service instance
    service = PIIDetectionService()

    # Create server using grpclib
    from grpclib.server import Server

    # Try to enable gRPC Server Reflection for grpclib if available
    # This allows tools like grpcurl to discover services without providing .proto files.
    from config import get_config
    
    services = [service]
    reflection_enabled = False
    
    try:
        config = get_config()
        enable_reflection = config.server.enable_reflection
    except (ValueError, AttributeError):
        enable_reflection = True  # Default to enabled
    
    if enable_reflection:
        try:
            # Try popular module paths for grpclib reflection implementation
            try:
                from grpclib_reflection.service import ServerReflection  # type: ignore
            except Exception:
                from grpclib.reflection.service import ServerReflection  # type: ignore
            # Register reflection with our service name
            service_names = ["pii_detection.PIIDetectionService"]
            services.append(ServerReflection(service_names))
            reflection_enabled = True
        except Exception:
            logger.warning(
                "gRPC reflection is not enabled for the BetterProto server. "
                "Install 'grpclib-reflection' to enable grpcurl discovery, or use grpcurl with -proto."
            )
    else:
        logger.info("gRPC Server Reflection explicitly disabled via ENABLE_REFLECTION=0")

    # Create server with the service(s)
    server = Server(services)

    # Start server
    logger.info(f"Starting betterproto gRPC server on port {port}...")
    if reflection_enabled:
        logger.info("gRPC Server Reflection: ENABLED (grpclib)")
    else:
        logger.info("gRPC Server Reflection: DISABLED")

    try:
        await server.start('0.0.0.0', port)
        logger.info(f"Betterproto gRPC server started successfully on port {port}")
        await server.wait_closed()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
    except Exception as e:
        logger.error(f"Error starting server: {e}")
        raise
    finally:
        try:
            server.close()
            await server.wait_closed()
        except RuntimeError:
            # Server was never started, ignore
            pass


if __name__ == "__main__":
    # Run the server
    asyncio.run(serve_betterproto())

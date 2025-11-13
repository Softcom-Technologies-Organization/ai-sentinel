"""
Memory management for PII detection.

This module provides the MemoryManager class that handles memory
optimization and cleanup operations for the PII detector, including
device-specific optimizations and cache clearing.
"""

import gc
import os
import warnings

import torch


class MemoryManager:
    """Handles memory optimization and cleanup operations."""

    @staticmethod
    def setup_memory_optimization() -> None:
        """Configure environment variables for memory optimization."""
        os.environ['TOKENIZERS_PARALLELISM'] = 'false'
        os.environ['OMP_NUM_THREADS'] = '1'
        warnings.filterwarnings("ignore")

    @staticmethod
    def optimize_for_device(device: str) -> None:
        """Apply device-specific optimizations."""
        if device == 'cpu':
            torch.set_num_threads(1)

    @staticmethod
    def clear_cache(device: str) -> None:
        """Clear memory caches."""
        gc.collect()
        if device == 'cuda' and torch.cuda.is_available():
            torch.cuda.empty_cache()

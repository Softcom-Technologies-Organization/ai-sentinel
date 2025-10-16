"""
Test suite for memory_manager module.

This module contains comprehensive tests for the MemoryManager class,
covering memory optimization, device-specific optimizations, and cache clearing.
"""

import os
import gc
import pytest
from unittest.mock import Mock, patch, call

from pii_detector.service.detector.memory_manager import MemoryManager


class TestSetupMemoryOptimization:
    """Test cases for setup_memory_optimization method."""
    
    @patch('os.environ', {})
    @patch('warnings.filterwarnings')
    def test_should_set_environment_variables(self, mock_filterwarnings):
        """Test that environment variables are set correctly."""
        MemoryManager.setup_memory_optimization()
        
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
        assert os.environ.get('OMP_NUM_THREADS') == '1'
    
    @patch('os.environ', {})
    @patch('warnings.filterwarnings')
    def test_should_configure_warnings_filter(self, mock_filterwarnings):
        """Test that warnings are filtered."""
        MemoryManager.setup_memory_optimization()
        
        mock_filterwarnings.assert_called_once_with("ignore")
    
    @patch('os.environ', {'EXISTING_VAR': 'value'})
    @patch('warnings.filterwarnings')
    def test_should_preserve_existing_environment_variables(self, mock_filterwarnings):
        """Test that existing environment variables are not affected."""
        MemoryManager.setup_memory_optimization()
        
        assert os.environ.get('EXISTING_VAR') == 'value'
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
        assert os.environ.get('OMP_NUM_THREADS') == '1'
    
    @patch('os.environ', {})
    @patch('warnings.filterwarnings')
    def test_should_overwrite_existing_tokenizers_parallelism(self, mock_filterwarnings):
        """Test that TOKENIZERS_PARALLELISM is overwritten if it exists."""
        os.environ['TOKENIZERS_PARALLELISM'] = 'true'
        
        MemoryManager.setup_memory_optimization()
        
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
    
    @patch('os.environ', {})
    @patch('warnings.filterwarnings')
    def test_should_be_callable_multiple_times(self, mock_filterwarnings):
        """Test that method can be called multiple times without errors."""
        MemoryManager.setup_memory_optimization()
        MemoryManager.setup_memory_optimization()
        
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
        assert os.environ.get('OMP_NUM_THREADS') == '1'
        assert mock_filterwarnings.call_count == 2


class TestOptimizeForDevice:
    """Test cases for optimize_for_device method."""
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_set_single_thread_for_cpu(self, mock_torch):
        """Test that torch is configured for single thread on CPU."""
        MemoryManager.optimize_for_device('cpu')
        
        mock_torch.set_num_threads.assert_called_once_with(1)
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_not_set_threads_for_cuda(self, mock_torch):
        """Test that torch threads are not set for CUDA device."""
        MemoryManager.optimize_for_device('cuda')
        
        mock_torch.set_num_threads.assert_not_called()
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_not_set_threads_for_other_devices(self, mock_torch):
        """Test that torch threads are not set for other devices."""
        MemoryManager.optimize_for_device('mps')
        MemoryManager.optimize_for_device('unknown')
        
        mock_torch.set_num_threads.assert_not_called()
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_handle_case_sensitive_device_name(self, mock_torch):
        """Test that device name is case-sensitive."""
        # Only 'cpu' should trigger optimization, not 'CPU'
        MemoryManager.optimize_for_device('CPU')
        
        mock_torch.set_num_threads.assert_not_called()
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_be_callable_multiple_times(self, mock_torch):
        """Test that method can be called multiple times."""
        MemoryManager.optimize_for_device('cpu')
        MemoryManager.optimize_for_device('cpu')
        
        assert mock_torch.set_num_threads.call_count == 2


class TestClearCache:
    """Test cases for clear_cache method."""
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=False)
    @patch('torch.cuda.empty_cache')
    def test_should_call_gc_collect_for_cpu(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that garbage collection is triggered for CPU."""
        MemoryManager.clear_cache('cpu')
        
        mock_gc_collect.assert_called_once()
        mock_empty_cache.assert_not_called()
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=True)
    @patch('torch.cuda.empty_cache')
    def test_should_clear_cuda_cache_when_available(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that CUDA cache is cleared when CUDA is available."""
        MemoryManager.clear_cache('cuda')
        
        mock_gc_collect.assert_called_once()
        mock_empty_cache.assert_called_once()
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=False)
    @patch('torch.cuda.empty_cache')
    def test_should_not_clear_cuda_cache_when_unavailable(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that CUDA cache is not cleared when CUDA is unavailable."""
        MemoryManager.clear_cache('cuda')
        
        mock_gc_collect.assert_called_once()
        mock_empty_cache.assert_not_called()
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=True)
    @patch('torch.cuda.empty_cache')
    def test_should_only_clear_cuda_cache_for_cuda_device(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that CUDA cache is only cleared for 'cuda' device."""
        MemoryManager.clear_cache('cpu')
        
        mock_gc_collect.assert_called_once()
        mock_empty_cache.assert_not_called()
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=False)
    @patch('torch.cuda.empty_cache')
    def test_should_handle_other_device_names(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that other device names trigger gc.collect only."""
        MemoryManager.clear_cache('mps')
        MemoryManager.clear_cache('unknown')
        
        assert mock_gc_collect.call_count == 2
        mock_empty_cache.assert_not_called()
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=True)
    @patch('torch.cuda.empty_cache')
    def test_should_be_callable_multiple_times(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that method can be called multiple times."""
        MemoryManager.clear_cache('cuda')
        MemoryManager.clear_cache('cuda')
        
        assert mock_gc_collect.call_count == 2
        assert mock_empty_cache.call_count == 2


class TestEdgeCases:
    """Test cases for edge cases and integration scenarios."""
    
    @patch('os.environ', {})
    @patch('warnings.filterwarnings')
    @patch('pii_detector.service.detector.memory_manager.torch')
    @patch('gc.collect')
    def test_should_handle_complete_workflow(self, mock_gc, mock_torch, mock_filterwarnings):
        """Test complete workflow of memory optimization."""
        # Setup
        MemoryManager.setup_memory_optimization()
        
        # Optimize
        MemoryManager.optimize_for_device('cpu')
        
        # Clear
        MemoryManager.clear_cache('cpu')
        
        # Verify
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
        mock_torch.set_num_threads.assert_called_once_with(1)
        mock_gc.assert_called_once()
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_handle_empty_device_string(self, mock_torch):
        """Test handling of empty device string."""
        # Should not raise exception
        MemoryManager.optimize_for_device('')
        MemoryManager.clear_cache('')
    
    @patch('gc.collect')
    @patch('torch.cuda.is_available', return_value=True)
    @patch('torch.cuda.empty_cache')
    def test_should_handle_case_sensitive_cuda_device(self, mock_empty_cache, mock_is_available, mock_gc_collect):
        """Test that 'CUDA' (uppercase) does not trigger CUDA cache clearing."""
        MemoryManager.clear_cache('CUDA')
        
        mock_gc_collect.assert_called_once()
        mock_empty_cache.assert_not_called()


class TestStaticMethods:
    """Test cases to verify static method behavior."""
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_be_static_methods(self, mock_torch):
        """Test that all methods are static and don't require instance."""
        # Should not raise TypeError
        MemoryManager.setup_memory_optimization()
        MemoryManager.optimize_for_device('cpu')
        MemoryManager.clear_cache('cpu')
    
    @patch('pii_detector.service.detector.memory_manager.torch')
    def test_should_not_maintain_state(self, mock_torch):
        """Test that MemoryManager does not maintain state between calls."""
        MemoryManager.optimize_for_device('cpu')
        first_call_count = mock_torch.set_num_threads.call_count
        
        MemoryManager.optimize_for_device('cpu')
        second_call_count = mock_torch.set_num_threads.call_count
        
        # Each call should be independent
        assert second_call_count == first_call_count + 1

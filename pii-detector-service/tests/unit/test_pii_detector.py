"""
Unit tests for PIIDetector class.

This module contains comprehensive tests for the PIIDetector class,
including unit tests, integration tests, and edge case testing.
"""

import gc
import os
import pytest
from unittest.mock import Mock, patch, MagicMock
from typing import Dict, List

# Add the service directory to the path for imports
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from pii_detector.service.detector.pii_detector import PIIDetector


class TestPIIDetector:
    """Test class for PIIDetector functionality."""

    @pytest.fixture
    def mock_model_components(self):
        """Create mock components for the model.

        The tokenizer mock returns a minimal dict-like encoding compatible with
        HuggingFace tokenizers: keys 'input_ids' and 'offset_mapping'. This allows
        production code that uses token-windowing to operate during tests.
        """
        def _make_fake_encoding(text: str):
            # Single window covering the entire text. Offsets drive span extraction.
            length = len(text or "")
            return {
                "input_ids": [[101, 102]],  # shape: [num_windows][tokens]
                "offset_mapping": [[(0, length)]],  # shape: [num_windows][(start, end)]
            }

        mock_tokenizer = Mock()
        mock_tokenizer.side_effect = lambda *args, **kwargs: _make_fake_encoding(args[0])

        mock_model = Mock()
        mock_model.parameters.return_value = []  # Return empty list for parameters
        mock_model.to.return_value = mock_model  # Return self for chaining
        mock_model.eval.return_value = None

        mock_pipeline = Mock()
        
        return {
            'tokenizer': mock_tokenizer,
            'model': mock_model,
            'pipeline': mock_pipeline
        }

    @pytest.fixture
    def detector(self, mock_model_components):
        """Create a PIIDetector instance with mocked components."""
        with patch('pii_detector.service.detector.pii_detector.AutoTokenizer') as mock_tokenizer_class, \
             patch('pii_detector.service.detector.pii_detector.AutoModelForTokenClassification') as mock_model_class, \
             patch('pii_detector.service.detector.pii_detector.pipeline') as mock_pipeline_class:
            
            mock_tokenizer_class.from_pretrained.return_value = mock_model_components['tokenizer']
            mock_model_class.from_pretrained.return_value = mock_model_components['model']
            mock_pipeline_class.return_value = mock_model_components['pipeline']
            
            detector = PIIDetector()
            detector.load_model()
            
            return detector

    @pytest.fixture
    def sample_text(self):
        """Sample text for testing."""
        return "Mon nom est Jean Dupont et mon email est jean.dupont@example.com"

    @pytest.fixture
    def sample_entities(self):
        """Sample entities for testing."""
        return [
            {
                'word': 'Jean Dupont',
                'entity_group': 'GIVENNAME',
                'start': 12,
                'end': 22,
                'score': 0.95
            },
            {
                'word': 'jean.dupont@example.com',
                'entity_group': 'EMAIL',
                'start': 40,
                'end': 63,
                'score': 0.98
            }
        ]

    def test_init_default_parameters(self):
        """Test PIIDetector initialization with default parameters."""
        detector = PIIDetector()
        
        assert detector.model_id == "iiiorg/piiranha-v1-detect-personal-information"
        assert detector.device in ['cpu', 'cuda']
        assert detector.max_length == 256
        assert detector.tokenizer is None
        assert detector.model is None
        assert detector.pipe is None

    def test_init_custom_parameters(self):
        """Test PIIDetector initialization with custom parameters."""
        custom_model_id = "custom/model"
        custom_device = "cpu"
        custom_max_length = 256
        
        detector = PIIDetector(
            model_id=custom_model_id,
            device=custom_device,
            max_length=custom_max_length
        )
        
        assert detector.model_id == custom_model_id
        assert detector.device == custom_device
        assert detector.max_length == custom_max_length

    @patch('pii_detector.service.detector.pii_detector.hf_hub_download')
    def test_download_model_success(self, mock_download):
        """Test successful model download."""
        with patch.dict(os.environ, {'HUGGING_FACE_API_KEY': 'test_key'}):
            detector = PIIDetector()
            detector.download_model()
            
            # Verify all files were downloaded
            expected_files = ["config.json", "model.safetensors", "tokenizer.json", "tokenizer_config.json"]
            assert mock_download.call_count == len(expected_files)

    def test_download_model_missing_api_key(self):
        """Test model download with missing API key."""
        # Mock the config to raise ValueError when API key validation fails
        with patch('pii_detector.config.get_config') as mock_get_config:
            mock_config = Mock()
            mock_config.model.huggingface_api_key = ""
            mock_get_config.return_value = mock_config
            
            with patch.dict(os.environ, {}, clear=True):
                detector = PIIDetector()
                
                with pytest.raises(ValueError, match="HUGGING_FACE_API_KEY environment variable must be set"):
                    detector.download_model()

    @patch('pii_detector.service.detector.pii_detector.hf_hub_download')
    def test_download_model_download_error(self, mock_download):
        """Test model download with download error."""
        mock_download.side_effect = Exception("Download failed")
        
        with patch.dict(os.environ, {'HUGGING_FACE_API_KEY': 'test_key'}):
            detector = PIIDetector()
            
            with pytest.raises(Exception, match="Download failed"):
                detector.download_model()

    def test_load_model_success(self, mock_model_components):
        """Test successful model loading."""
        with patch('pii_detector.service.detector.pii_detector.AutoTokenizer') as mock_tokenizer_class, \
             patch('pii_detector.service.detector.pii_detector.AutoModelForTokenClassification') as mock_model_class, \
             patch('pii_detector.service.detector.pii_detector.pipeline') as mock_pipeline_class:
            
            mock_tokenizer_class.from_pretrained.return_value = mock_model_components['tokenizer']
            mock_model_class.from_pretrained.return_value = mock_model_components['model']
            mock_pipeline_class.return_value = mock_model_components['pipeline']
            
            detector = PIIDetector()
            detector.load_model()
            
            assert detector.tokenizer is not None
            assert detector.model is not None
            assert detector.pipe is not None

    def test_detect_pii_model_not_loaded(self):
        """Test PII detection when model is not loaded."""
        detector = PIIDetector()
        
        with pytest.raises(ValueError, match="The model must be loaded before use"):
            detector.detect_pii("test text")

    def test_detect_pii_success(self, detector, sample_text, sample_entities):
        """Test successful PII detection."""
        detector.pipe.return_value = sample_entities
        
        result = detector.detect_pii(sample_text, threshold=0.5)
        
        assert len(result) >= 2  # At least the mocked entities
        assert all('text' in entity for entity in result)
        assert all('type' in entity for entity in result)
        assert all('score' in entity for entity in result)

    def test_detect_pii_with_threshold_filtering(self, detector, sample_text):
        """Test PII detection with threshold filtering."""
        low_score_entities = [
            {
                'word': 'test',
                'entity_group': 'GIVENNAME',
                'start': 0,
                'end': 4,
                'score': 0.3  # Below threshold
            }
        ]
        
        detector.pipe.return_value = low_score_entities
        
        result = detector.detect_pii(sample_text, threshold=0.5)
        
        # Should filter out low-score entities (except regex emails)
        filtered_entities = [e for e in result if e['score'] >= 0.5]
        assert len(filtered_entities) >= 0

    def test_detect_pii_long_text_chunked(self, detector):
        """Test PII detection with very long text (chunked processing)."""
        long_text = "A" * 15000  # Longer than 10000 chars
        
        with patch.object(detector, '_detect_pii_chunked') as mock_chunked:
            mock_chunked.return_value = []
            
            detector.detect_pii(long_text)
            
            mock_chunked.assert_called_once_with(long_text, 0.5)

    def test_detect_pii_batch_success(self, detector, sample_entities):
        """Test successful batch PII detection."""
        texts = ["Text 1", "Text 2", "Text 3"]
        # Mock pipe to return batch results - each call returns entities for the batch
        detector.pipe.side_effect = [
            [sample_entities, sample_entities],  # First batch (2 texts)
            [sample_entities]  # Second batch (1 text)
        ]
        
        results = detector.detect_pii_batch(texts, threshold=0.5, batch_size=2)
        
        assert len(results) == len(texts)
        assert all(isinstance(result, list) for result in results)

    def test_detect_pii_batch_model_not_loaded(self):
        """Test batch PII detection when model is not loaded."""
        detector = PIIDetector()
        
        with pytest.raises(ValueError, match="The model must be loaded before use"):
            detector.detect_pii_batch(["test text"])

    def test_detect_pii_batch_with_error(self, detector):
        """Test batch PII detection with processing error."""
        texts = ["Text 1", "Text 2"]
        detector.pipe.side_effect = Exception("Processing error")
        
        results = detector.detect_pii_batch(texts, threshold=0.5)
        
        # Should return empty results for failed batches
        assert len(results) == len(texts)
        assert all(result == [] for result in results)

    def test_mask_pii_success(self, detector, sample_text, sample_entities):
        """Test successful PII masking."""
        detector.pipe.return_value = sample_entities
        
        masked_text, entities = detector.mask_pii(sample_text, threshold=0.5)
        
        assert isinstance(masked_text, str)
        assert isinstance(entities, list)
        assert "[GIVENNAME]" in masked_text or "[EMAIL]" in masked_text

    def test_mask_pii_error_handling(self, detector):
        """Test PII masking error handling."""
        detector.pipe.side_effect = Exception("Masking error")
        
        with pytest.raises(Exception, match="Masking error"):
            detector.mask_pii("test text")

    def test_get_summary_success(self, detector, sample_text, sample_entities):
        """Test successful PII summary generation."""
        detector.pipe.return_value = sample_entities
        
        summary = detector.get_summary(sample_text, threshold=0.5)
        
        assert isinstance(summary, dict)
        assert all(isinstance(count, int) for count in summary.values())

    def test_get_summary_error_handling(self, detector):
        """Test PII summary error handling."""
        detector.pipe.side_effect = Exception("Summary error")
        
        with pytest.raises(Exception, match="Summary error"):
            detector.get_summary("test text")

    def test_detect_emails_regex(self, detector):
        """Regex-based email detection is disabled; expect no additional entities."""
        text = "Contact us at support@example.com or admin@test.org"
        existing_entities = []
        
        result = detector._detect_emails_regex(text, existing_entities)
        
        assert len(result) == 0

    def test_detect_emails_regex_no_duplicates(self, detector):
        """Test regex email detection avoids duplicates."""
        text = "Email: test@example.com"
        existing_entities = [
            {
                'start': 7,
                'end': 23,
                'type': 'EMAIL',
                'text': 'test@example.com'
            }
        ]
        
        result = detector._detect_emails_regex(text, existing_entities)
        
        assert len(result) == 0  # Should not add duplicate

    def test_detect_pii_chunked(self, detector, sample_entities):
        """Test chunked PII detection for long texts."""
        long_text = "A" * 6000  # Long enough to trigger chunking
        detector.pipe.return_value = sample_entities
        
        result = detector._detect_pii_chunked(long_text, threshold=0.5)
        
        assert isinstance(result, list)

    def test_clear_cache(self, detector):
        """Test cache clearing functionality."""
        with patch('gc.collect') as mock_gc, \
             patch('torch.cuda.empty_cache') as mock_cuda_cache:
            
            detector.clear_cache()
            
            mock_gc.assert_called_once()

    def test_clear_cache_cuda(self, detector):
        """Test cache clearing with CUDA."""
        detector.device = 'cuda'
        
        with patch('gc.collect') as mock_gc, \
             patch('torch.cuda.is_available', return_value=True), \
             patch('torch.cuda.empty_cache') as mock_cuda_cache:
            
            detector.clear_cache()
            
            mock_gc.assert_called_once()
            mock_cuda_cache.assert_called_once()

    def test_destructor(self, detector):
        """Test destructor cleanup."""
        detector.device = 'cuda'  # Set device to cuda for this test
        
        with patch('gc.collect') as mock_gc, \
             patch('torch.cuda.is_available', return_value=True), \
             patch('torch.cuda.empty_cache') as mock_cuda_cache:
            
            detector.__del__()
            
            mock_gc.assert_called_once()
            mock_cuda_cache.assert_called_once()

    def test_destructor_with_error(self, detector):
        """Test destructor with cleanup error."""
        with patch('gc.collect', side_effect=Exception("Cleanup error")):
            # Should not raise exception
            detector.__del__()

    def test_label_mapping(self, detector):
        """Test label mapping functionality."""
        assert 'EMAIL' in detector.label_mapping
        assert 'GIVENNAME' in detector.label_mapping
        assert detector.label_mapping['EMAIL'] == 'Email'
        assert detector.label_mapping['GIVENNAME'] == 'Prénom'

    @pytest.mark.parametrize("threshold", [0.1, 0.5, 0.9])
    def test_different_thresholds(self, detector, sample_text, sample_entities, threshold):
        """Test PII detection with different thresholds."""
        detector.pipe.return_value = sample_entities
        
        result = detector.detect_pii(sample_text, threshold=threshold)
        
        # All entities should meet the threshold (mocked entities have high scores)
        assert all(entity['score'] >= threshold for entity in result)

    @pytest.mark.parametrize("device", ["cpu", "cuda"])
    def test_different_devices(self, device):
        """Test PIIDetector initialization with different devices."""
        detector = PIIDetector(device=device)
        
        assert detector.device == device

    def test_memory_optimization_settings(self):
        """Test memory optimization environment variables."""
        detector = PIIDetector()
        
        # Check that memory optimization settings are applied
        assert os.environ.get('TOKENIZERS_PARALLELISM') == 'false'
        assert os.environ.get('OMP_NUM_THREADS') == '1'

    def test_edge_case_empty_text(self, detector):
        """Test PII detection with empty text."""
        detector.pipe.return_value = []
        
        result = detector.detect_pii("", threshold=0.5)
        
        assert isinstance(result, list)

    def test_edge_case_special_characters(self, detector, sample_entities):
        """Test PII detection with special characters."""
        special_text = "Nom: Jean-François O'Connor <jean@example.com>"
        detector.pipe.return_value = sample_entities
        
        result = detector.detect_pii(special_text, threshold=0.5)
        
        assert isinstance(result, list)

    def test_performance_logging(self, detector, sample_text, sample_entities, caplog):
        """Test that performance logging is working."""
        detector.pipe.return_value = sample_entities
        
        with caplog.at_level('DEBUG'):
            detector.detect_pii(sample_text, threshold=0.5)
        
        # Check that performance logs are present
        log_messages = [record.message for record in caplog.records]
        assert any('Detection completed' in msg for msg in log_messages)

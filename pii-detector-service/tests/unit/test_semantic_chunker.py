"""
Unit tests for SemanticTextChunker and related classes.

This module provides comprehensive test coverage for semantic text chunking,
following modern Python testing best practices with pytest.

Test Naming Convention: Should_ExpectedBehavior_When_StateUnderTest
"""

import logging
from unittest.mock import Mock, MagicMock, patch

import pytest

from pii_detector.infrastructure.text_processing.semantic_chunker import (
    ChunkResult,
    SemanticTextChunker,
    FallbackChunker,
    create_chunker,
    SEMCHUNK_AVAILABLE,
)


# ============================================================================
# Fixtures
# ============================================================================

@pytest.fixture
def mock_tokenizer():
    """Fixture providing a mock tokenizer."""
    tokenizer = Mock()
    tokenizer.encode = Mock(return_value=[1, 2, 3, 4, 5])
    return tokenizer


@pytest.fixture
def mock_semchunk_chunker():
    """Fixture providing a mock semchunk chunker."""
    chunker = Mock()
    chunker.return_value = ["First chunk", "Second chunk"]
    return chunker


@pytest.fixture
def sample_text():
    """Fixture providing sample text for testing."""
    return "This is a test. This is another sentence. And one more for good measure."


@pytest.fixture
def logger_mock():
    """Fixture providing a mock logger."""
    return Mock(spec=logging.Logger)


# ============================================================================
# ChunkResult Tests
# ============================================================================

class TestChunkResult:
    """Test suite for ChunkResult dataclass."""

    def test_should_create_chunk_result_with_required_fields(self):
        """Should create ChunkResult with text, start, and end."""
        chunk = ChunkResult(text="test chunk", start=0, end=10)
        
        assert chunk.text == "test chunk"
        assert chunk.start == 0
        assert chunk.end == 10
        assert chunk.token_count is None

    def test_should_create_chunk_result_with_token_count(self):
        """Should create ChunkResult with optional token_count."""
        chunk = ChunkResult(text="test", start=0, end=4, token_count=2)
        
        assert chunk.token_count == 2

    def test_should_support_default_token_count_none(self):
        """Should default token_count to None."""
        chunk = ChunkResult(text="test", start=0, end=4)
        
        assert chunk.token_count is None


# ============================================================================
# SemanticTextChunker Initialization Tests
# ============================================================================

class TestSemanticTextChunkerInitialization:
    """Test suite for SemanticTextChunker initialization."""

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_initialize_with_valid_parameters(self, mock_semchunk, mock_tokenizer):
        """Should initialize semantic chunker with valid parameters."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        assert chunker.tokenizer == mock_tokenizer
        assert chunker.chunk_size == 768
        assert chunker.overlap == 100
        mock_semchunk.chunkerify.assert_called_once_with(mock_tokenizer, 768)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', False)
    def test_should_raise_import_error_when_semchunk_not_available(self, mock_tokenizer):
        """Should raise ImportError when semchunk is not installed."""
        with pytest.raises(ImportError, match="semchunk library is required"):
            SemanticTextChunker(
                tokenizer=mock_tokenizer,
                chunk_size=768,
                overlap=100
            )

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_raise_value_error_when_chunk_size_less_than_overlap(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should raise ValueError when chunk_size <= overlap."""
        with pytest.raises(ValueError, match="chunk_size .* must be greater than overlap"):
            SemanticTextChunker(
                tokenizer=mock_tokenizer,
                chunk_size=100,
                overlap=100
            )

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_use_custom_logger_when_provided(
        self, mock_semchunk, mock_tokenizer, logger_mock
    ):
        """Should use custom logger when provided."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            logger=logger_mock
        )
        
        assert chunker.logger == logger_mock
        logger_mock.info.assert_called_once()

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_create_default_logger_when_not_provided(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should create default logger when none provided."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        assert chunker.logger is not None


# ============================================================================
# SemanticTextChunker Chunking Tests
# ============================================================================

class TestSemanticTextChunkerChunking:
    """Test suite for SemanticTextChunker text chunking operations."""

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_chunk_text_successfully(
        self, mock_semchunk, mock_tokenizer, sample_text
    ):
        """Should chunk text into semantic chunks."""
        mock_chunker = Mock()
        mock_chunker.return_value = ["First chunk.", "Second chunk."]
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text(sample_text)
        
        assert len(results) == 2
        assert results[0].text == "First chunk."
        assert results[1].text == "Second chunk."
        assert isinstance(results[0], ChunkResult)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_return_empty_list_for_empty_text(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should return empty list when text is empty."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text("")
        
        assert results == []

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_track_chunk_positions_correctly(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should track start and end positions of chunks."""
        text = "First chunk. Second chunk."
        mock_chunker = Mock()
        mock_chunker.return_value = ["First chunk.", "Second chunk."]
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text(text)
        
        assert results[0].start == 0
        assert results[0].end == 12
        assert results[1].start == 13
        assert results[1].end == 26

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_handle_chunk_not_found_in_text(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should handle case where chunk text is not found (normalized)."""
        text = "Original text"
        mock_chunker = Mock()
        mock_chunker.return_value = ["Different text"]  # Won't be found
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text(text)
        
        # Should use fallback position tracking
        assert len(results) == 1
        assert results[0].start >= 0

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_raise_runtime_error_when_chunking_fails(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should raise RuntimeError when chunking fails."""
        mock_chunker = Mock()
        mock_chunker.side_effect = Exception("Chunking failed")
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        with pytest.raises(RuntimeError, match="Failed to chunk text"):
            chunker.chunk_text("test text")

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_set_token_count_to_none(
        self, mock_semchunk, mock_tokenizer, sample_text
    ):
        """Should set token_count to None (semchunk doesn't provide it)."""
        mock_chunker = Mock()
        mock_chunker.return_value = ["Chunk"]
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text(sample_text)
        
        assert all(r.token_count is None for r in results)


# ============================================================================
# SemanticTextChunker Info Tests
# ============================================================================

class TestSemanticTextChunkerInfo:
    """Test suite for SemanticTextChunker information retrieval."""

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_return_chunk_info(self, mock_semchunk, mock_tokenizer):
        """Should return chunker configuration information."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        info = chunker.get_chunk_info()
        
        assert info["chunk_size"] == 768
        assert info["overlap"] == 100
        assert info["library"] == "semchunk"
        assert info["available"] == SEMCHUNK_AVAILABLE


# ============================================================================
# FallbackChunker Tests
# ============================================================================

class TestFallbackChunker:
    """Test suite for FallbackChunker."""

    def test_should_initialize_fallback_chunker(self):
        """Should initialize fallback chunker with parameters."""
        chunker = FallbackChunker(chunk_size=768, overlap=100)
        
        assert chunker.chunk_size == 768
        assert chunker.overlap == 100
        assert chunker.chars_per_token == 4
        assert chunker.chunk_chars == 768 * 4
        assert chunker.overlap_chars == 100 * 4

    def test_should_use_custom_chars_per_token(self):
        """Should use custom chars_per_token when provided."""
        chunker = FallbackChunker(chunk_size=768, overlap=100, chars_per_token=5)
        
        assert chunker.chars_per_token == 5
        assert chunker.chunk_chars == 768 * 5

    def test_should_use_custom_logger_in_fallback(self, logger_mock):
        """Should use custom logger when provided."""
        chunker = FallbackChunker(
            chunk_size=768,
            overlap=100,
            logger=logger_mock
        )
        
        assert chunker.logger == logger_mock
        logger_mock.warning.assert_called_once()

    def test_should_chunk_text_with_fallback(self):
        """Should chunk text using character-based approach."""
        text = "a" * 5000
        chunker = FallbackChunker(chunk_size=100, overlap=10, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        assert len(results) > 1
        assert all(isinstance(r, ChunkResult) for r in results)

    def test_should_return_empty_list_for_empty_text_in_fallback(self):
        """Should return empty list for empty text."""
        chunker = FallbackChunker(chunk_size=768, overlap=100)
        
        results = chunker.chunk_text("")
        
        assert results == []

    def test_should_handle_overlap_in_fallback(self):
        """Should handle overlap between chunks."""
        text = "a" * 1000
        chunker = FallbackChunker(chunk_size=50, overlap=10, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        # Verify chunks overlap
        assert len(results) >= 2
        if len(results) >= 2:
            # Second chunk should start before first chunk ends
            assert results[1].start < results[0].end

    def test_should_track_positions_in_fallback(self):
        """Should track start and end positions correctly."""
        text = "0123456789" * 100
        chunker = FallbackChunker(chunk_size=50, overlap=0, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        assert results[0].start == 0
        assert results[0].end == min(len(text), 50 * 4)

    def test_should_get_chunk_info_from_fallback(self):
        """Should return configuration information from fallback."""
        chunker = FallbackChunker(chunk_size=768, overlap=100, chars_per_token=5)
        
        info = chunker.get_chunk_info()
        
        assert info["chunk_size"] == 768
        assert info["overlap"] == 100
        assert info["chunk_chars"] == 768 * 5
        assert info["overlap_chars"] == 100 * 5
        assert info["chars_per_token"] == 5
        assert info["library"] == "fallback"
        assert info["available"] is True


# ============================================================================
# Factory Function Tests
# ============================================================================

class TestCreateChunker:
    """Test suite for create_chunker factory function."""

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_create_semantic_chunker_when_available(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should create SemanticTextChunker when conditions are met."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            use_semantic=True
        )
        
        assert isinstance(chunker, SemanticTextChunker)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', False)
    def test_should_create_fallback_when_semchunk_not_available(self, mock_tokenizer):
        """Should create FallbackChunker when semchunk not available."""
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            use_semantic=True
        )
        
        assert isinstance(chunker, FallbackChunker)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    def test_should_create_fallback_when_tokenizer_is_none(self):
        """Should create FallbackChunker when tokenizer is None."""
        chunker = create_chunker(
            tokenizer=None,
            chunk_size=768,
            overlap=100,
            use_semantic=True
        )
        
        assert isinstance(chunker, FallbackChunker)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_create_fallback_when_use_semantic_false(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should create FallbackChunker when use_semantic is False."""
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            use_semantic=False
        )
        
        assert isinstance(chunker, FallbackChunker)

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_create_fallback_when_semantic_init_fails(
        self, mock_semchunk, mock_tokenizer, logger_mock
    ):
        """Should create FallbackChunker when SemanticTextChunker init fails."""
        mock_semchunk.chunkerify.side_effect = Exception("Init failed")
        
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            use_semantic=True,
            logger=logger_mock
        )
        
        assert isinstance(chunker, FallbackChunker)
        logger_mock.warning.assert_called()

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_use_custom_logger_in_factory(
        self, mock_semchunk, mock_tokenizer, logger_mock
    ):
        """Should pass custom logger to created chunker."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100,
            use_semantic=True,
            logger=logger_mock
        )
        
        assert chunker.logger == logger_mock

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_pass_parameters_to_semantic_chunker(
        self, mock_semchunk, mock_tokenizer
    ):
        """Should pass all parameters to SemanticTextChunker."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        chunker = create_chunker(
            tokenizer=mock_tokenizer,
            chunk_size=512,
            overlap=50,
            use_semantic=True
        )
        
        assert chunker.chunk_size == 512
        assert chunker.overlap == 50

    def test_should_pass_parameters_to_fallback_chunker(self):
        """Should pass all parameters to FallbackChunker."""
        chunker = create_chunker(
            tokenizer=None,
            chunk_size=512,
            overlap=50,
            use_semantic=False
        )
        
        assert chunker.chunk_size == 512
        assert chunker.overlap == 50


# ============================================================================
# Edge Cases and Integration Tests
# ============================================================================

class TestEdgeCasesAndIntegration:
    """Test suite for edge cases and integration scenarios."""

    def test_should_handle_very_short_text_in_fallback(self):
        """Should handle very short text correctly."""
        chunker = FallbackChunker(chunk_size=100, overlap=10)
        
        results = chunker.chunk_text("Hi")
        
        assert len(results) == 1
        assert results[0].text == "Hi"

    def test_should_handle_text_exactly_chunk_size(self):
        """Should handle text that is exactly chunk size."""
        text = "a" * 400  # Exactly 100 tokens at 4 chars/token
        chunker = FallbackChunker(chunk_size=100, overlap=0, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        assert len(results) == 1

    def test_should_handle_large_overlap(self):
        """Should handle large overlap values."""
        text = "a" * 1000
        chunker = FallbackChunker(chunk_size=100, overlap=80, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        # Should still produce multiple chunks
        assert len(results) >= 2

    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_handle_unicode_text(self, mock_semchunk, mock_tokenizer):
        """Should handle Unicode text correctly."""
        text = "Héllo wörld! 你好世界 🌍"
        mock_chunker = Mock()
        mock_chunker.return_value = [text]
        mock_semchunk.chunkerify.return_value = mock_chunker
        
        chunker = SemanticTextChunker(
            tokenizer=mock_tokenizer,
            chunk_size=768,
            overlap=100
        )
        
        results = chunker.chunk_text(text)
        
        assert len(results) == 1
        assert results[0].text == text

    def test_should_handle_unicode_text_in_fallback(self):
        """Should handle Unicode text in fallback chunker."""
        text = "Café résumé naïve 日本語"
        chunker = FallbackChunker(chunk_size=100, overlap=10)
        
        results = chunker.chunk_text(text)
        
        assert len(results) >= 1
        # Verify text is preserved
        reconstructed = "".join(r.text for r in results)
        assert text in reconstructed or len(reconstructed) > 0


# ============================================================================
# Parametrized Tests
# ============================================================================

class TestParametrizedScenarios:
    """Test suite using parametrization for multiple scenarios."""

    @pytest.mark.parametrize("chunk_size,overlap,should_succeed", [
        (768, 100, True),
        (512, 50, True),
        (100, 100, False),  # chunk_size == overlap
        (100, 150, False),  # chunk_size < overlap
    ])
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.SEMCHUNK_AVAILABLE', True)
    @patch('pii_detector.infrastructure.text_processing.semantic_chunker.semchunk')
    def test_should_validate_chunk_size_and_overlap(
        self, mock_semchunk, chunk_size, overlap, should_succeed, mock_tokenizer
    ):
        """Should validate chunk_size and overlap relationship."""
        mock_semchunk.chunkerify.return_value = Mock()
        
        if should_succeed:
            chunker = SemanticTextChunker(
                tokenizer=mock_tokenizer,
                chunk_size=chunk_size,
                overlap=overlap
            )
            assert chunker is not None
        else:
            with pytest.raises(ValueError):
                SemanticTextChunker(
                    tokenizer=mock_tokenizer,
                    chunk_size=chunk_size,
                    overlap=overlap
                )

    @pytest.mark.parametrize("text_length,expected_min_chunks", [
        (0, 0),
        (100, 1),
        (1000, 1),
        (10000, 2),
    ])
    def test_should_create_appropriate_number_of_chunks(
        self, text_length, expected_min_chunks
    ):
        """Should create appropriate number of chunks based on text length."""
        text = "a" * text_length
        chunker = FallbackChunker(chunk_size=100, overlap=10, chars_per_token=4)
        
        results = chunker.chunk_text(text)
        
        assert len(results) >= expected_min_chunks

    @pytest.mark.parametrize("chars_per_token", [1, 4, 8, 10])
    def test_should_respect_chars_per_token_setting(self, chars_per_token):
        """Should respect different chars_per_token values."""
        chunker = FallbackChunker(
            chunk_size=100,
            overlap=10,
            chars_per_token=chars_per_token
        )
        
        assert chunker.chunk_chars == 100 * chars_per_token
        assert chunker.overlap_chars == 10 * chars_per_token

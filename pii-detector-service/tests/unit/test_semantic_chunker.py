"""
Unit tests for ChunkResult, FallbackChunker and MinistralTokenChunker.

This module provides test coverage for text chunking,
following modern Python testing best practices with pytest.

Test Naming Convention: Should_ExpectedBehavior_When_StateUnderTest
"""

import logging
import re
from unittest.mock import Mock

import pytest

from pii_detector.infrastructure.text_processing.semantic_chunker import (
    ChunkResult,
    FallbackChunker,
    MinistralTokenChunker,
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




class TestFallbackChunker:
    """Test suite for FallbackChunker."""

    def test_should_initialize_fallback_chunker(self):
        """Should initialize fallback chunker with parameters."""
        chunker = FallbackChunker(chunk_size=768, overlap=100)
        
        assert chunker.chunk_size == 768
        assert chunker.overlap == 100
        assert chunker.chars_per_token == 3
        assert chunker.chunk_chars == 768 * 3
        assert chunker.overlap_chars == 100 * 3

    def test_should_use_custom_chars_per_token(self):
        """Should use custom chars_per_token when provided."""
        chunker = FallbackChunker(chunk_size=768, overlap=100, chars_per_token=5)

        assert chunker.chars_per_token == 5
        assert chunker.chunk_chars == 768 * 5

    def test_should_raise_when_overlap_greater_or_equal_chunk_size(self):
        """Should reject a pathological overlap >= chunk_size configuration."""
        with pytest.raises(ValueError):
            FallbackChunker(chunk_size=35, overlap=128)

    def test_should_raise_when_chunk_size_not_positive(self):
        """Should reject a non-positive chunk_size."""
        with pytest.raises(ValueError):
            FallbackChunker(chunk_size=0, overlap=0)

    def test_should_raise_when_overlap_negative(self):
        """Should reject a negative overlap."""
        with pytest.raises(ValueError):
            FallbackChunker(chunk_size=100, overlap=-1)

    def test_should_use_custom_logger_in_fallback(self, logger_mock):
        """Should use custom logger when provided."""
        chunker = FallbackChunker(
            chunk_size=768,
            overlap=100,
            logger=logger_mock
        )
        
        assert chunker.logger == logger_mock
        logger_mock.info.assert_called_once()

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
# MinistralTokenChunker Tests
# ============================================================================

class _FakeEncoding:
    """Minimal stand-in for ``tokenizers.Encoding`` (only ``offsets`` is read)."""

    def __init__(self, offsets):
        self.offsets = offsets


class _WhitespaceFakeTokenizer:
    """Whitespace tokenizer exposing the ``tokenizers.Tokenizer`` surface used.

    Each maximal run of non-space characters is one token; ``offsets`` are the
    exact char spans into the input — letting tests assert the char-offset
    invariant against a tokenizer whose boundaries are fully deterministic. The
    ``add_special_tokens`` kwarg is recorded so a test can assert the chunker
    asks for content tokens only (faithful token budget).
    """

    def __init__(self):
        self.last_add_special_tokens = None

    def encode(self, text, add_special_tokens=True):
        self.last_add_special_tokens = add_special_tokens
        offsets = [(m.start(), m.end()) for m in re.finditer(r"\S+", text)]
        return _FakeEncoding(offsets)


class TestMinistralTokenChunker:
    """Token-window chunker driven by the Ministral HF tokenizer offsets."""

    def test_Should_RaiseValueError_When_ChunkSizeIsZero(self):
        tokenizer = _WhitespaceFakeTokenizer()
        with pytest.raises(ValueError, match="chunk_size"):
            MinistralTokenChunker(tokenizer=tokenizer, chunk_size=0, overlap=0)

    def test_Should_RaiseValueError_When_OverlapNegative(self):
        tokenizer = _WhitespaceFakeTokenizer()
        with pytest.raises(ValueError, match="overlap"):
            MinistralTokenChunker(tokenizer=tokenizer, chunk_size=2048, overlap=-1)

    def test_Should_RaiseValueError_When_OverlapGreaterOrEqualChunkSize(self):
        tokenizer = _WhitespaceFakeTokenizer()
        with pytest.raises(ValueError, match="overlap"):
            MinistralTokenChunker(tokenizer=tokenizer, chunk_size=100, overlap=100)

    def test_Should_ReturnEmptyList_When_TextIsEmpty(self):
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=8, overlap=2
        )
        assert chunker.chunk_text("") == []

    def test_Should_ReturnSingleChunkCoveringWholeText_When_TokensWithinChunkSize(self):
        text = "Jean habite a Geneve"  # 4 whitespace tokens
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=8, overlap=2
        )
        results = chunker.chunk_text(text)
        assert len(results) == 1
        assert results[0].start == 0
        assert results[0].end == len(text)
        assert results[0].text == text
        assert results[0].token_count == 4

    def test_Should_PreserveCharOffsetInvariant_When_MultipleChunks(self):
        # The contract the detector relies on: text[chunk.start:chunk.end] is
        # EXACTLY chunk.text for every chunk, so chunk-local find() + chunk.start
        # rebases entities to correct global coordinates.
        text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda"
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=3, overlap=1
        )
        results = chunker.chunk_text(text)
        assert len(results) > 1
        for chunk in results:
            assert text[chunk.start:chunk.end] == chunk.text

    def test_Should_SplitOnTokenBoundaries_When_TextExceedsChunkSize(self):
        text = "one two three four five six seven"  # 7 tokens
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=3, overlap=0
        )
        results = chunker.chunk_text(text)
        # stride 3, 7 tokens -> windows [0:3], [3:6], [6:7]
        assert len(results) == 3
        assert [c.token_count for c in results] == [3, 3, 1]

    def test_Should_OverlapTokens_When_OverlapPositive(self):
        text = "one two three four five six seven eight"  # 8 tokens
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=4, overlap=2
        )
        results = chunker.chunk_text(text)
        assert len(results) >= 2
        # Consecutive windows share tokens -> the next chunk starts before the
        # previous one ends (char space), guaranteeing no boundary-split entity.
        assert results[1].start < results[0].end

    def test_Should_CoverEntireText_When_Chunking(self):
        text = "  alpha beta gamma delta epsilon zeta  "  # leading/trailing space
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=2, overlap=0
        )
        results = chunker.chunk_text(text)
        # First chunk anchored at 0 and last chunk reaches len(text): no content
        # at the document edges can be silently dropped.
        assert results[0].start == 0
        assert results[-1].end == len(text)

    def test_Should_EncodeWithoutSpecialTokens_When_ComputingOffsets(self):
        # Token budgets must be measured on content tokens only (no BOS/EOS),
        # otherwise the configured size drifts from what the model consumes.
        tokenizer = _WhitespaceFakeTokenizer()
        chunker = MinistralTokenChunker(tokenizer=tokenizer, chunk_size=4, overlap=0)
        chunker.chunk_text("one two three four five")
        assert tokenizer.last_add_special_tokens is False

    def test_Should_ReportConfiguration_When_GetChunkInfoCalled(self):
        chunker = MinistralTokenChunker(
            tokenizer=_WhitespaceFakeTokenizer(), chunk_size=2048, overlap=410
        )
        info = chunker.get_chunk_info()
        assert info["chunk_size"] == 2048
        assert info["overlap"] == 410
        assert info["library"] == "tokenizers"
        assert info["available"] is True
        assert "tokenizer" in info


class _CharacterTokenizer:
    def encode(self, text, add_special_tokens=False):
        return _FakeEncoding([(index, index + 1) for index in range(len(text))])


def _assert_complete_coverage(text, chunks):
    assert chunks[0].start == 0
    assert chunks[-1].end == len(text)
    for chunk in chunks:
        assert chunk.text == text[chunk.start:chunk.end]
        assert chunk.start < chunk.end
    for previous, following in zip(chunks, chunks[1:]):
        assert previous.start < following.start <= previous.end
        assert following.end > previous.end


class TestStructuredRowChunking:
    @pytest.mark.parametrize("token_based", [False, True])
    def test_Should_KeepCompleteRowsAndHeaders_When_TableSpansManyChunks(self, token_based):
        rows = [
            f"Nom : Élise {index} | Service : SMTP | Port : 587 | Code PIN : 1234\n"
            for index in range(30)
        ]
        text = "".join(rows)
        tokenizer = _WhitespaceFakeTokenizer()
        chunker = (
            MinistralTokenChunker(tokenizer, chunk_size=40, overlap=12)
            if token_based else FallbackChunker(145, overlap=50, chars_per_token=1)
        )
        chunks = chunker.chunk_text(text)

        assert len(chunks) > 5
        _assert_complete_coverage(text, chunks)
        for row in rows:
            assert any(row in chunk.text for chunk in chunks)
        assert all(chunk.start == 0 or text[chunk.start - 1] == "\n" for chunk in chunks)
        assert all(chunk.text.endswith("\n") for chunk in chunks)
        if token_based:
            assert all(chunk.token_count == len(tokenizer.encode(chunk.text).offsets) <= 40
                       for chunk in chunks)
        else:
            assert all(len(chunk.text) <= 145 for chunk in chunks)

    @pytest.mark.parametrize("token_based", [False, True])
    def test_Should_KeepHeaderWithEachCell_When_RowExceedsBudget(self, token_based):
        cells = [f"Champ {index} : élise@example.test" for index in range(24)]
        text = " | ".join(cells)
        chunker = (
            MinistralTokenChunker(_WhitespaceFakeTokenizer(), chunk_size=13, overlap=4)
            if token_based else FallbackChunker(78, overlap=20, chars_per_token=1)
        )
        chunks = chunker.chunk_text(text)

        _assert_complete_coverage(text, chunks)
        for cell in cells:
            assert any(cell in chunk.text for chunk in chunks)
        assert all(chunk.start == 0 or text[chunk.start - 3:chunk.start] == " | "
                   for chunk in chunks)
        assert all(chunk.end == len(text) or chunk.text.endswith(" | ") for chunk in chunks)

    @pytest.mark.parametrize("token_based", [False, True])
    @pytest.mark.parametrize("overlap", [0, 6, 23])
    def test_Should_CoverOversizedCellWithinBudget_When_UnicodeValueCannotFit(
        self, token_based, overlap
    ):
        text = "Secret : " + "🔐é漢abc" * 80 + " | Code : 1234\nNom : Élise\n"
        chunker = (
            MinistralTokenChunker(_CharacterTokenizer(), chunk_size=24, overlap=overlap)
            if token_based else FallbackChunker(24, overlap=overlap, chars_per_token=1)
        )
        chunks = chunker.chunk_text(text)

        _assert_complete_coverage(text, chunks)
        assert all(len(chunk.text) <= 24 for chunk in chunks)
        assert any("Code : 1234" in chunk.text for chunk in chunks)
        assert any("Nom : Élise" in chunk.text for chunk in chunks)

    def test_Should_CoverWhitespaceBetweenTokens_When_OverlapIsZero(self):
        text = "  one \t two   three\r\n four five   six "
        chunks = MinistralTokenChunker(_WhitespaceFakeTokenizer(), chunk_size=2).chunk_text(text)

        _assert_complete_coverage(text, chunks)
        assert "".join(chunk.text for chunk in chunks) == text

    @pytest.mark.parametrize("chars_per_token", [0, -1])
    def test_Should_RejectInvalidCharacterBudget_When_Configured(self, chars_per_token):
        with pytest.raises(ValueError, match="chars_per_token"):
            FallbackChunker(20, chars_per_token=chars_per_token)

    def test_Should_EnforceBudgetAfterRetokenizing_When_SlicedTokensChange(self):
        class BoundarySensitiveTokenizer:
            def encode(self, text, add_special_tokens=False):
                width = 3 if len(text) > 20 else 1
                return _FakeEncoding([
                    (index, min(len(text), index + width))
                    for index in range(0, len(text), width)
                ])

        tokenizer = BoundarySensitiveTokenizer()
        text = "Champ : " + "123456789" * 15
        chunks = MinistralTokenChunker(tokenizer, chunk_size=8, overlap=2).chunk_text(text)

        _assert_complete_coverage(text, chunks)
        assert all(chunk.token_count == len(tokenizer.encode(chunk.text).offsets) <= 8
                   for chunk in chunks)

    def test_Should_RejectImpossibleBudget_When_OneCharacterNeedsMultipleTokens(self):
        class ByteFallbackTokenizer:
            def encode(self, text, add_special_tokens=False):
                return _FakeEncoding([
                    (index, index + 1)
                    for index, character in enumerate(text)
                    for _ in character.encode("utf-8")
                ])

        chunker = MinistralTokenChunker(ByteFallbackTokenizer(), chunk_size=1)
        with pytest.raises(ValueError, match="single character"):
            chunker.chunk_text("🔐")

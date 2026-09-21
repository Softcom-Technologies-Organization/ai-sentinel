"""
Text chunking with token-aware splitting.

This module provides text chunking for the Ministral-PII LLM extractor:
- MinistralTokenChunker: exact token-window chunking via the model's HuggingFace
  tokenizer offsets.
- FallbackChunker: char-ratio approximation used only when a tokenizer cannot be
  loaded.
"""

import logging
import re
from bisect import bisect_left, bisect_right
from dataclasses import dataclass
from typing import Any, List, Optional, Tuple


@dataclass
class ChunkResult:
    """Result of text chunking operation."""
    text: str
    start: int
    end: int
    token_count: Optional[int] = None


class _ChunkBoundaries:
    def __init__(self, text: str):
        self.rows = [match.end() for match in re.finditer(r"\n", text)] + [len(text)]
        self.cells = sorted(set(self.rows + [
            match.end() for match in re.finditer(r" \| ", text)
        ]))

    def end(self, start: int, limit: int) -> int:
        for boundaries in (self.rows, self.cells):
            index = bisect_right(boundaries, limit) - 1
            if index >= 0 and boundaries[index] > start:
                return boundaries[index]
        return limit

    def next_start(self, start: int, end: int, target: int) -> int:
        for boundaries in (self.rows, self.cells):
            index = bisect_left(boundaries, end)
            if index >= len(boundaries) or boundaries[index] != end:
                continue
            lower = bisect_right(boundaries, start)
            nearest = min(index, max(lower, bisect_left(boundaries, target)))
            candidates = [boundaries[nearest]]
            if nearest > lower:
                candidates.append(boundaries[nearest - 1])
            return min(candidates, key=lambda position: abs(position - target))
        return min(end, max(start + 1, target))


class FallbackChunker:
    """
    Simple character-based chunker with overlap support.

    Used as the degraded fallback when a real tokenizer is unavailable. Splits on
    character boundaries with approximate token estimation, using a conservative
    3 chars/token ratio for safety with multilingual text.
    """

    def __init__(
        self,
        chunk_size: int,
        overlap: int = 0,
        chars_per_token: int = 3,
        logger: Optional[logging.Logger] = None
    ):
        """
        Initialize character-based chunker.

        Args:
            chunk_size: Maximum tokens per chunk
            overlap: Number of tokens to overlap
            chars_per_token: Approximate characters per token (default: 3 for safety)
            logger: Optional logger instance
        """
        # Guard against a silently pathological configuration (overlap >= chunk
        # would make windows re-scan more than they advance) so a bad value fails
        # loudly instead of producing degenerate, infinitely-overlapping chunks.
        if chunk_size <= 0:
            raise ValueError(f"chunk_size ({chunk_size}) must be > 0")
        if overlap < 0:
            raise ValueError(f"overlap ({overlap}) must be >= 0")
        if overlap >= chunk_size:
            raise ValueError(
                f"chunk_size ({chunk_size}) must be greater than overlap ({overlap})"
            )
        if chars_per_token <= 0:
            raise ValueError(f"chars_per_token ({chars_per_token}) must be > 0")

        self.chunk_size = chunk_size
        self.overlap = overlap
        self.chars_per_token = chars_per_token
        self.logger = logger or logging.getLogger(__name__)

        # Convert token limits to character limits
        self.chunk_chars = chunk_size * chars_per_token
        self.overlap_chars = overlap * chars_per_token

        self.logger.info(
            f"Character-based chunker initialized: "
            f"chunk_chars={self.chunk_chars}, overlap_chars={self.overlap_chars}"
        )

    def chunk_text(self, text: str) -> List[ChunkResult]:
        """
        Split text into character-based chunks.

        Args:
            text: Text to chunk

        Returns:
            List of ChunkResult objects
        """
        if not text:
            return []

        boundaries = _ChunkBoundaries(text)
        results = []
        start = covered_end = 0
        while start < len(text):
            end = boundaries.end(start, min(len(text), start + self.chunk_chars))
            if end <= covered_end:
                start = covered_end
                continue
            results.append(ChunkResult(text=text[start:end], start=start, end=end))
            if end == len(text):
                break
            covered_end = end
            start = boundaries.next_start(start, end, end - self.overlap_chars)

        self.logger.debug(
            f"Fallback chunked {len(text)} chars into {len(results)} chunks"
        )

        return results

    def get_chunk_info(self) -> dict:
        """Get information about chunker configuration."""
        return {
            "chunk_size": self.chunk_size,
            "overlap": self.overlap,
            "chunk_chars": self.chunk_chars,
            "overlap_chars": self.overlap_chars,
            "chars_per_token": self.chars_per_token,
            "library": "fallback",
            "available": True
        }


class MinistralTokenChunker:
    """Token-window chunker for the Ministral-PII LLM extractor.

    Splits text into windows of at most ``chunk_size`` *real* tokens (Ministral's
    HuggingFace tokenizer) with ``overlap`` tokens shared between consecutive
    windows where complete rows allow it. Whole lines are preferred, followed
    by serialized table cells separated with `` | ``. Oversized cells fall back
    to token windows. Window boundaries are taken from the tokenizer's per-token char
    offsets (``tokenizers.Encoding.offsets``), so each chunk's ``start``/``end``
    are exact character positions into the ORIGINAL text and
    ``text[chunk.start:chunk.end] == chunk.text`` holds for every chunk. That
    invariant is what keeps the detector's downstream offset rebasing
    (chunk-local ``str.find`` + ``chunk.start``) correct.

    Why tokens, not the char×N proxy of :class:`FallbackChunker`: the 3B model's
    recall degrades with the number of tokens in context, and chars→tokens drifts
    with content (≈3.3-3.7 chars/token for French, ≈2-3 for IDs/base64, ≈1 for
    CJK). A char window can therefore silently overrun the token sweet-spot on
    dense / non-English input. Measuring the size axis in the unit the model is
    actually bounded by makes the configured budget honest (2048 means 2048
    tokens) and reproducible against the chunking benchmark, which uses this same
    tokenizer.

    Overlap is intentionally NOT de-duplicated here: an entity sitting in the
    shared region is emitted by both windows with identical GLOBAL offsets and is
    collapsed downstream by ``DetectionMerger`` (key = start/end/type/text); a
    boundary-straddling entity is recovered whole by the overlapping window and
    wins ``DetectionMerger``'s longest-span resolution.
    """

    def __init__(
        self,
        tokenizer: Any,
        chunk_size: int,
        overlap: int = 0,
        logger: Optional[logging.Logger] = None,
    ) -> None:
        if chunk_size <= 0:
            raise ValueError(f"chunk_size ({chunk_size}) must be > 0")
        if overlap < 0:
            raise ValueError(f"overlap ({overlap}) must be >= 0")
        if overlap >= chunk_size:
            raise ValueError(
                f"overlap ({overlap}) must be < chunk_size ({chunk_size})"
            )

        self._tokenizer = tokenizer
        self._chunk_size = chunk_size
        self._overlap = overlap
        self._logger = logger or logging.getLogger(__name__)

    def chunk_text(self, text: str) -> List[ChunkResult]:
        """Split ``text`` into windows bounded by ``chunk_size`` real tokens."""
        if not text:
            return []

        offsets = self._token_offsets(text)
        if not offsets:
            # No content tokens (e.g. whitespace-only input): emit the whole text
            # as one chunk so callers never silently drop input.
            return [ChunkResult(text=text, start=0, end=len(text), token_count=0)]

        token_ends = [end for _, end in offsets]
        boundaries = _ChunkBoundaries(text)
        results: List[ChunkResult] = []
        start = covered_end = 0
        while start < len(text):
            index = bisect_right(token_ends, start) + self._chunk_size
            limit = len(text) if index >= len(offsets) else max(start + 1, offsets[index][0])
            end = boundaries.end(start, limit)
            end, chunk_offsets = self._fit_budget(text, (start, end), boundaries)
            if end <= covered_end:
                start = covered_end
                continue
            results.append(ChunkResult(
                text=text[start:end], start=start, end=end,
                token_count=len(chunk_offsets),
            ))
            if end == len(text):
                break
            covered_end = end
            target = end
            if self._overlap and chunk_offsets:
                target = start + chunk_offsets[max(0, len(chunk_offsets) - self._overlap)][0]
            start = boundaries.next_start(start, end, target)

        self._logger.debug(
            "MinistralTokenChunker: %d chars -> %d chunks (chunk_size=%d, overlap=%d)",
            len(text), len(results), self._chunk_size, self._overlap,
        )
        return results

    def _fit_budget(
        self, text: str, span: Tuple[int, int], boundaries: _ChunkBoundaries
    ) -> Tuple[int, List[Tuple[int, int]]]:
        start, end = span
        offsets = self._token_offsets(text[start:end])
        while len(offsets) > self._chunk_size:
            end = min(end - 1, start + offsets[self._chunk_size][0])
            if end <= start:
                raise ValueError("chunk_size cannot fit a single character with this tokenizer")
            end = boundaries.end(start, end)
            offsets = self._token_offsets(text[start:end])
        return end, offsets

    def _token_offsets(self, text: str) -> List[Tuple[int, int]]:
        """Per-token ``(char_start, char_end)`` spans into ``text``.

        Encodes without special tokens so the offsets map 1:1 to content and the
        token budget matches what the model consumes. Mirrors the chunking
        benchmark's ``tokenizers.Encoding.offsets`` usage.
        """
        encoding = self._tokenizer.encode(text, add_special_tokens=False)
        offsets = getattr(encoding, "offsets", None)
        return list(offsets) if offsets else []

    def get_chunk_info(self) -> dict:
        return {
            "chunk_size": self._chunk_size,
            "overlap": self._overlap,
            "library": "tokenizers",
            "available": True,
            "tokenizer": type(self._tokenizer).__name__,
        }

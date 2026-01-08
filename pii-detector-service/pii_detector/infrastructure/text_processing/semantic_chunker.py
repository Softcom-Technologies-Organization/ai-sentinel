"""
Semantic text chunking with token-aware splitting.

This module provides intelligent text chunking that respects semantic boundaries
(sentences, paragraphs) while ensuring no chunk exceeds token limits.
Critical for models like GLiNER that have internal sentence-level token limits.
"""

import time
import logging
from dataclasses import dataclass
from typing import Any, List, Optional

try:
    import semchunk
    SEMCHUNK_AVAILABLE = True
except ImportError:
    SEMCHUNK_AVAILABLE = False


@dataclass
class ChunkResult:
    """Result of text chunking operation."""
    text: str
    start: int
    end: int
    token_count: Optional[int] = None


class SemanticTextChunker:
    """
    Token-aware semantic text chunker.
    
    Uses semchunk library to intelligently split text into chunks that:
    1. Respect semantic boundaries (sentences, paragraphs)
    2. Never exceed specified token limits
    3. Can overlap for context preservation at boundaries
    
    Critical for GLiNER which has a 768-token limit per sentence internally.
    Beneficial for other transformer models to optimize processing.
    """

    def __init__(
        self,
        tokenizer: Any,
        chunk_size: int,
        overlap: int = 0,
        logger: Optional[logging.Logger] = None
    ):
        """
        Initialize semantic chunker.
        
        Args:
            tokenizer: HuggingFace tokenizer or compatible tokenizer
            chunk_size: Maximum tokens per chunk (hard limit)
            overlap: Number of tokens to overlap between chunks (default: 0)
            logger: Optional logger instance
            
        Raises:
            ImportError: If semchunk is not installed
            ValueError: If chunk_size <= overlap
        """
        if not SEMCHUNK_AVAILABLE:
            raise ImportError(
                "semchunk library is required for semantic chunking. "
                "Install it with: pip install semchunk"
            )
        
        if chunk_size <= overlap:
            raise ValueError(f"chunk_size ({chunk_size}) must be greater than overlap ({overlap})")

        self.tokenizer = tokenizer
        self.chunk_size = chunk_size
        self.overlap = overlap
        self.logger = logger or logging.getLogger(__name__)
        
        # Initialize semchunk chunker
        # semchunk API: chunkerify(tokenizer, chunk_size, memoize=True)
        # overlap is handled differently - not a direct parameter
        self.chunker = semchunk.chunkerify(
            tokenizer, 
            chunk_size
        )

        self.logger.info(
            f"SemanticTextChunker initialized: chunk_size={chunk_size}, overlap={overlap}"
        )

    def chunk_text(self, text: str) -> List[ChunkResult]:
        """
        Split text into semantic chunks respecting token limits.
        
        Args:
            text: Text to chunk
            
        Returns:
            List of ChunkResult objects with text and position info
            
        Raises:
            RuntimeError: If chunking fails
        """
        start_time = time.time()

        if not text:
            return []
        
        try:
            # Use semchunk to get intelligent chunks
            chunks = self.chunker(text)
            
            # Convert to ChunkResult objects with position tracking
            results = []
            current_pos = 0
            
            for chunk_text in chunks:
                # Find chunk position in original text
                # Account for possible whitespace normalization
                start = text.find(chunk_text, current_pos)
                if start == -1:
                    # Fallback: chunk might be normalized, use current position
                    start = current_pos
                
                end = start + len(chunk_text)
                
                result = ChunkResult(
                    text=chunk_text,
                    start=start,
                    end=end,
                    token_count=None  # semchunk doesn't provide this
                )
                results.append(result)
                
                # Move position forward for next search
                current_pos = end
            
            self.logger.debug(
                f"Chunked {len(text)} chars into {len(results)} semantic chunks"
            )
            elapsed_time = time.time() - start_time
            print(f"[chunk_text] Chunking text completed in {elapsed_time:.2f}s")
            return results
            
        except Exception as e:
            self.logger.error(f"Semantic chunking failed: {str(e)}")
            raise RuntimeError(f"Failed to chunk text: {str(e)}") from e

    def get_chunk_info(self) -> dict:
        """
        Get information about chunker configuration.
        
        Returns:
            Dictionary with chunker settings
        """
        return {
            "chunk_size": self.chunk_size,
            "overlap": self.overlap,
            "library": "semchunk",
            "available": SEMCHUNK_AVAILABLE
        }


class FallbackChunker:
    """
    Simple character-based chunker with overlap support.

    This implementation splits on character boundaries with approximate token
    estimation. Works well for GLiNER PII detection as proven by testing.
    Uses conservative 3 chars/token ratio for safety with multilingual text.
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
        
        results = []
        offset = 0
        
        while offset < len(text):
            # Calculate chunk boundaries
            chunk_start = max(0, offset - self.overlap_chars if offset > 0 else 0)
            chunk_end = min(len(text), offset + self.chunk_chars)
            
            chunk_text = text[chunk_start:chunk_end]
            
            result = ChunkResult(
                text=chunk_text,
                start=chunk_start,
                end=chunk_end,
                token_count=None
            )
            results.append(result)
            
            offset += self.chunk_chars
        
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


def create_chunker(
    tokenizer: Optional[Any] = None,
    chunk_size: int = 378,
    overlap: int = 50,
    use_semantic: bool = False,
    logger: Optional[logging.Logger] = None
) -> Any:
    """
    Factory function to create appropriate chunker.

    Args:
        tokenizer: HuggingFace tokenizer (required for semantic chunking)
        chunk_size: Maximum tokens per chunk (default: 378 for GLiNER)
        overlap: Number of tokens to overlap (default: 50)
        use_semantic: Use semantic chunking (default: False, semchunk doesn't support overlap)
        logger: Optional logger instance

    Returns:
        SemanticTextChunker or FallbackChunker instance

    Note:
        Character-based chunking is preferred because:
        1. It properly supports overlap for context continuity
        2. It has been tested and works well with GLiNER PII detection
        3. semchunk library doesn't support overlap parameter
    """
    logger = logger or logging.getLogger(__name__)

    # Use semantic chunking only if explicitly requested AND overlap is 0
    # (semchunk doesn't support overlap)
    if use_semantic and SEMCHUNK_AVAILABLE and tokenizer is not None and overlap == 0:
        try:
            return SemanticTextChunker(
                tokenizer=tokenizer,
                chunk_size=chunk_size,
                overlap=overlap,
                logger=logger
            )
        except Exception as e:
            logger.warning(f"Failed to create semantic chunker: {e}, using character-based")

    return FallbackChunker(
        chunk_size=chunk_size,
        overlap=overlap,
        logger=logger
    )

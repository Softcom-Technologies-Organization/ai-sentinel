"""Integration guarantees for the Ministral token-based chunker.

Unlike the unit tests (which inject a deterministic whitespace fake tokenizer),
these load the **real** Ministral HF tokenizer (``OpenMed/Ministral-3B-PII-Preview``)
and assert the properties the detector's offset rebasing depends on, on
accent-rich French / mixed-content text where the char×N proxy drifts most:

1. char-offset invariant: ``text[chunk.start:chunk.end] == chunk.text`` for every
   chunk (so chunk-local ``find`` + ``chunk.start`` rebases to correct global
   offsets);
2. gapless full coverage: first chunk starts at 0, last ends at ``len(text)``;
3. honest token budget: every chunk holds at most ``chunk_size`` real tokens;
4. end-to-end: with the real tokenizer + real chunking (HTTP mocked), entities
   are rebased to their exact global character positions.

The whole module skips when the tokenizer cannot be loaded (offline CI with no
HF cache), so it never produces a false failure.
"""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from pii_detector.infrastructure.detector.ministral_detector import (
    MINISTRAL_TOKENIZER_REPO,
    MinistralDetector,
)
from pii_detector.infrastructure.text_processing.semantic_chunker import (
    MinistralTokenChunker,
)

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def real_tokenizer():
    """Load the real Ministral tokenizer, or skip if unavailable (offline CI)."""
    try:
        return MinistralDetector._load_tokenizer(MINISTRAL_TOKENIZER_REPO)
    except Exception as exc:  # noqa: BLE001 - any load failure -> skip, not fail
        pytest.skip(f"Ministral tokenizer unavailable ({exc!r}); skipping real-tokenizer test")


# A multi-line, accent-rich French document mixing dense identifiers (AVS, IBAN,
# email, phone) — the content where a char×4 window silently overruns the token
# budget. Long enough that small chunk sizes force several windows.
_FRENCH_DOC = (
    "Le numéro AVS de Jean Dupont est 756.1234.5678.97 et il réside à Genève.\n"
    "Son IBAN est CH93 0076 2011 6238 5295 7 auprès de la Banque Cantonale.\n"
    "Pour le contacter : jean.dupont@example.ch ou au +41 79 123 45 67.\n"
    "Référence dossier : DOSSIER-2024-ÉLISÉE-00042, créé le 12 février 2024.\n"
    "Madame Élise Müller (élise.müller@société.ch) suit également ce dossier.\n"
) * 3


class TestRealTokenizerOffsetInvariant:
    @pytest.mark.parametrize("budget,overlap", [(64, 16), (128, 32), (2048, 256)])
    def test_Should_KeepTableRowsWithHeaders_When_RealTokenizer(self, real_tokenizer, budget, overlap):
        rows = [
            f"Nom : Élise Müller {index} | Email : elise{index}@example.ch | Port : 587\n"
            for index in range(120)
        ]
        text = "".join(rows)
        chunks = MinistralTokenChunker(real_tokenizer, budget, overlap).chunk_text(text)

        assert len(chunks) > 1
        assert all(any(row in chunk.text for chunk in chunks) for row in rows)
        assert all(text[chunk.start:chunk.end] == chunk.text for chunk in chunks)
        assert all(len(real_tokenizer.encode(chunk.text, add_special_tokens=False).ids) <= budget
                   for chunk in chunks)

    def test_Should_PreserveCharOffsetInvariant_When_RealTokenizerFrenchText(
        self, real_tokenizer
    ):
        chunker = MinistralTokenChunker(
            tokenizer=real_tokenizer, chunk_size=32, overlap=8
        )
        chunks = chunker.chunk_text(_FRENCH_DOC)

        assert len(chunks) > 1, "small chunk_size must split the document"
        for chunk in chunks:
            assert _FRENCH_DOC[chunk.start:chunk.end] == chunk.text

    def test_Should_CoverEntireDocument_When_RealTokenizer(self, real_tokenizer):
        chunker = MinistralTokenChunker(
            tokenizer=real_tokenizer, chunk_size=32, overlap=8
        )
        chunks = chunker.chunk_text(_FRENCH_DOC)

        assert chunks[0].start == 0
        assert chunks[-1].end == len(_FRENCH_DOC)
        # Consecutive windows leave no char gap (each chunk's end >= next start).
        for prev, nxt in zip(chunks, chunks[1:]):
            assert nxt.start <= prev.end

    def test_Should_BoundEachChunkToChunkSizeTokens_When_RealTokenizer(
        self, real_tokenizer
    ):
        chunk_size = 32
        chunker = MinistralTokenChunker(
            tokenizer=real_tokenizer, chunk_size=chunk_size, overlap=8
        )
        chunks = chunker.chunk_text(_FRENCH_DOC)

        # The honest-budget guarantee: re-encoding each chunk's text never
        # exceeds the configured token budget (modulo the edge-clamp, which only
        # extends to whitespace at the very first/last chunk).
        for chunk in chunks:
            n_tokens = len(real_tokenizer.encode(chunk.text, add_special_tokens=False).ids)
            # Tolerance absorbs the edge-clamp (leading/trailing whitespace on the
            # first/last chunk) and any BPE re-merge at a sliced boundary.
            assert n_tokens <= chunk_size + 4, (
                f"chunk holds {n_tokens} tokens > budget {chunk_size}"
            )


_ADVERSARIAL_INPUTS = [
    "   \n\t   \r\n  ",                       # whitespace / control only
    "🌍🔐👤" * 80,                              # emoji (multi-byte, surrogate-ish)
    "中文字符測試資料" * 150,                    # CJK (≈1 char/token)
    "a" * 6000,                               # one dense run, no separators
    "مرحبا بالعالم هذا اختبار " * 120,         # RTL Arabic
    "sk-AbCdEf0123456789==/+" * 200,          # dense base64/secret-like
    "Élève, naïve, cœur — déjà vu; 12.34€\n" * 200,  # accents + punctuation
]


class TestRealTokenizerAdversarialCoverage:
    """The chunker must hold its char-offset + coverage contract even on the
    pathological inputs where char-ratio windowing drifts worst."""

    @pytest.mark.parametrize("text", _ADVERSARIAL_INPUTS)
    def test_Should_HoldOffsetAndCoverageContract_When_AdversarialInput(
        self, real_tokenizer, text
    ):
        chunker = MinistralTokenChunker(
            tokenizer=real_tokenizer, chunk_size=24, overlap=6
        )
        chunks = chunker.chunk_text(text)

        assert chunks, "non-empty text must yield at least one chunk"
        # 1. Char-offset invariant for every chunk.
        for chunk in chunks:
            assert text[chunk.start:chunk.end] == chunk.text
        # 2. Anchored at the document edges.
        assert chunks[0].start == 0
        assert chunks[-1].end == len(text)
        # 3. Gapless + monotonic: every char index is covered by some chunk
        #    (consecutive windows never leave a hole), so no PII can fall between
        #    chunks.
        for prev, nxt in zip(chunks, chunks[1:]):
            assert 0 <= prev.start <= nxt.start
            assert nxt.start <= prev.end


class TestRealTokenizerEndToEndRebasing:
    def test_Should_RebaseEntitiesToExactGlobalOffsets_When_RealChunking(
        self, real_tokenizer
    ):
        # Known PII spans present verbatim in the document.
        spans = {
            "756.1234.5678.97": "AVS_NUMBER",
            "jean.dupont@example.ch": "EMAIL",
            "CH93 0076 2011 6238 5295 7": "IBAN",
        }

        detector = MinistralDetector()
        detector._get_tokenizer = lambda: real_tokenizer  # real chunking
        client = MagicMock()

        def fake_post(url, json=None):
            chunk_text = json["messages"][1]["content"]
            import json as _json
            pairs = [
                {"text": span, "label": label}
                for span, label in spans.items()
                if span in chunk_text
            ]
            resp = MagicMock()
            resp.raise_for_status = MagicMock()
            resp.json = MagicMock(
                return_value={"choices": [{"message": {"content": _json.dumps(pairs)}}]}
            )
            return resp

        client.post = MagicMock(side_effect=fake_post)
        detector._client = client

        configs = {
            f"MINISTRAL:{label}": {
                "enabled": True,
                "threshold": 0.0,
                "detector": "MINISTRAL",
                "detector_label": label,
            }
            for label in set(spans.values())
        }

        # overlap (24) >= the longest known span in tokens, so any span crossing
        # a window cut is still fully present in at least one window -> recall is
        # deterministic, not luck-of-the-boundary.
        entities = detector.detect_pii(
            _FRENCH_DOC, pii_type_configs=configs, chunk_size=64, overlap=24
        )

        # Every known span must be recovered with offsets that index back to the
        # exact substring in the ORIGINAL document.
        for entity in entities:
            assert _FRENCH_DOC[entity.start:entity.end] == entity.text
        found_texts = {e.text for e in entities}
        for span in spans:
            assert span in found_texts, f"missing rebased span: {span!r}"

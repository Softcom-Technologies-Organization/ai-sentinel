"""Deterministic precision post-filter applied after detection.

This module exposes :class:`FormatPrefilterValidator`, an implementation of
the :class:`~pii_detector.domain.port.pii_post_filter_protocol.PIIPostFilterProtocol`
that runs as the **final precision stage** of the detection pipeline. It
applies minimum-length heuristics before two existing validation passes:

1. a **cross-label technical-artifact denylist**
   (:mod:`technical_artifact_denylist`) that drops spans whose text is a
   recognisable machine artefact (UUID, ObjectId, digests, traceparent,
   version strings, base64 images), whatever passthrough label Ministral
   attached to them;
2. the **per-``pii_type`` strategies** in :mod:`registry` that drop
   mechanically-impossible findings (positive checksum / parse failures).

Key design points:

- **Fail-open**: an unmapped ``pii_type`` or an exception raised by a strategy
  keeps the entity. Values below a configured length are rejected even when
  a format marker or a non-canonical shape would otherwise preserve them.
- **Shared discard channel**: :meth:`filter_with_verdicts` returns a
  ``(kept, rejections)`` shape that ``_add_discarded_entities_to_response``
  already knows how to serialise (the proto verdict fields keep their
  historical ``judge_*`` names but carry the post-filter verdict). Each
  rejection carries a :class:`_PostfilterVerdict` (``.verdict`` /
  ``.confidence`` / ``.reason``).
- **No HTTP / no model**: the filter is purely deterministic, so there is no
  network call, no thread-pool, and no lifecycle to shut down.

References: ``my-files/prefilter-work/PLAN.md`` sections 1.5-1.6 and
``my-files/fable5-postfilter-task.md``.
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import List, Optional, Tuple

from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.port.pii_post_filter_protocol import (
    PIIPostFilterProtocol,
)
from pii_detector.infrastructure.postfilter import minimum_length, technical_artifact_denylist
from pii_detector.infrastructure.postfilter.registry import STRATEGIES

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Verdict duck-type
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _PostfilterVerdict:
    """Duck-typed verdict matching :class:`PiiVerdict` (.verdict/.confidence/.reason).

    ``_add_discarded_entities_to_response`` reads these three attributes via
    ``getattr`` only, so this lightweight dataclass plugs into the shared
    ``discarded_entities`` channel without importing pydantic.
    """

    verdict: str
    confidence: float
    reason: str


# ---------------------------------------------------------------------------
# pii_type normalisation (local 3-liner -- avoids importing the gRPC module)
# ---------------------------------------------------------------------------


def _normalize_pii_type(pii_type) -> str:
    """Normalise a ``pii_type`` to the UPPER_SNAKE key used by the registry.

    Reimplements the gRPC ``_normalize_pii_type_for_grpc`` rule locally so the
    infrastructure pre-filter never imports the inbound gRPC adapter. Free-text
    Ministral passthrough labels (``card number``) become ``CARD_NUMBER``.
    """
    if pii_type is None or pii_type == "":
        return "UNKNOWN"
    return str(pii_type).upper().replace(" ", "_").replace("-", "_")


# ---------------------------------------------------------------------------
# Singleton
# ---------------------------------------------------------------------------


_INSTANCE: Optional["FormatPostfilterValidator"] = None
_INSTANCE_LOCK = threading.Lock()


def get_instance() -> FormatPostfilterValidator | None:
    """Return the process-wide :class:`FormatPrefilterValidator` singleton.

    Built lazily (no I/O at import time) so the no-prefilter path never
    allocates anything.
    """
    global _INSTANCE
    if _INSTANCE is None:
        with _INSTANCE_LOCK:
            if _INSTANCE is None:
                _INSTANCE = FormatPostfilterValidator()
    return _INSTANCE


def _reset_singleton_for_tests() -> None:
    """Reset the module-level singleton (test-only helper)."""
    global _INSTANCE
    with _INSTANCE_LOCK:
        _INSTANCE = None


# ---------------------------------------------------------------------------
# Validator
# ---------------------------------------------------------------------------


class FormatPostfilterValidator(PIIPostFilterProtocol):
    """Deterministic precision post-filter (final stage after detection).

    Behaviour summary:

    - Each entity first goes through its minimum-length check, then the label-agnostic
      :func:`technical_artifact_denylist.evaluate` pass: spans whose text is
      a recognisable technical artefact are discarded whatever their label.
    - Then the ``pii_type`` is normalised and looked up in
      :data:`registry.STRATEGIES`. Unmapped types pass through unchanged.
    - The matched strategy's :meth:`evaluate` decides keep / reject. A
      ``keep=False`` verdict (positive checksum / parse failure) discards the
      entity with a synthetic ``FALSE_POSITIVE`` verdict (confidence 1.0).
    - Any exception raised by the denylist or a strategy keeps the entity
      (defensive fail-open).
    """

    SOURCE_NAME = "format-postfilter"

    # -- PIIPostFilterProtocol -----------------------------------------------

    @property
    def name(self) -> str:
        """Stable identifier for the filter, used in logs and metrics."""
        return self.SOURCE_NAME

    def filter(
        self, text: str, entities: List[PIIEntity]
    ) -> List[PIIEntity]:
        """Return the subset of entities to keep after the format pre-filter."""
        return self.filter_with_verdicts(text, entities)[0]

    def filter_with_verdicts(
        self, text: str, entities: List[PIIEntity]
    ) -> Tuple[List[PIIEntity], List[Tuple[PIIEntity, _PostfilterVerdict]]]:
        """Filter entities and also return the rejected ones with verdicts.

        Returns a ``(kept, rejections)`` pair: ``kept`` preserves the original
        ordering and ``rejections`` lists each discarded entity with the
        ``FALSE_POSITIVE`` verdict that motivated the rejection. Entities kept
        by fail-open never appear in ``rejections``.
        """
        kept: List[PIIEntity] = []
        rejections: List[Tuple[PIIEntity, _PostfilterVerdict]] = []
        for entity in entities:
            pii_type = _normalize_pii_type(entity.pii_type)
            try:
                deny = minimum_length.evaluate(entity.text, pii_type)
                if deny is None:
                    deny = technical_artifact_denylist.evaluate(
                        entity.text, pii_type
                    )
            except Exception:  # defensive fail-open
                deny = None
            if deny is not None and not deny.keep:
                rejections.append(
                    (
                        entity,
                        _PostfilterVerdict(
                            verdict="FALSE_POSITIVE",
                            confidence=1.0,
                            reason=deny.reason,
                        ),
                    )
                )
                continue
            strategy = STRATEGIES.get(pii_type)
            if strategy is None:  # type not in scope -> keep
                kept.append(entity)
                continue
            try:
                verdict = strategy.evaluate(str(entity.text))
            except Exception:  # defensive fail-open
                kept.append(entity)
                continue
            if verdict.keep:
                kept.append(entity)
            else:
                rejections.append(
                    (
                        entity,
                        _PostfilterVerdict(
                            verdict="FALSE_POSITIVE",
                            confidence=1.0,
                            reason=verdict.reason,
                        ),
                    )
                )
        return kept, rejections


__all__ = [
    "FormatPostfilterValidator",
    "get_instance",
]

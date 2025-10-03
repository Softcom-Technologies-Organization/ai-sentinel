"""
Utility to pre-download and cache additional Hugging Face models at startup.

- Reads extra model repo IDs from env var PII_EXTRA_MODELS (semicolon-separated).
- Defaults to preloading "Ar86Bat/multilang-pii-ner" for cross-analysis.
- Honors HUGGING_FACE_API_KEY if present; otherwise safely no-ops (logs at INFO).
- Uses huggingface_hub.snapshot_download to leverage local HF cache.

This module is intentionally lightweight so it can be used from both the
service startup (pii_service) and potential CLI/bootstrap scripts.
"""
from __future__ import annotations

import logging
import os
from typing import Iterable, List


logger = logging.getLogger(__name__)

# Default extra model(s) to warm the cache with
DEFAULT_EXTRA_MODELS: List[str] = [
    "Ar86Bat/multilang-pii-ner",
]


def get_env_extra_models() -> List[str]:
    """Read extra models from centralized configuration; fallback to defaults.

    Returns:
        A list of Hugging Face repo IDs to cache locally.
    """
    from pii_detector.config import get_config
    
    try:
        config = get_config()
        models = config.detection.pii_extra_models
        if models:
            return models
    except (ValueError, AttributeError):
        # Config not available or validation failed
        pass
    
    return DEFAULT_EXTRA_MODELS.copy()


def ensure_models_cached(model_ids: Iterable[str]) -> None:
    """Ensure the provided model repos are available in the local HF cache.

    Behavior:
    - If HUGGING_FACE_API_KEY is missing, logs and returns without error.
    - On download errors, logs a warning but does not raise to avoid breaking startup.

    Args:
        model_ids: Hugging Face repository IDs (e.g., "org/model").
    """
    from pii_detector.config import get_config
    
    try:
        config = get_config()
        token = config.model.huggingface_api_key
    except (ValueError, AttributeError):
        # Config not available or validation failed
        token = None
    
    if not token:
        logger.info(
            "HUGGING_FACE_API_KEY not set; skipping pre-download of extra models."
        )
        return

    try:
        from huggingface_hub import snapshot_download  # Lazy import to avoid hard dep at import time
    except Exception as e:
        logger.warning(f"huggingface_hub not available; skipping model pre-download: {e}")
        return

    for repo_id in model_ids:
        try:
            logger.info(f"Preloading Hugging Face model into cache: {repo_id}")
            # snapshot_download reuses HF_HOME cache; idempotent if already cached
            snapshot_download(repo_id=repo_id, token=token)
            logger.info(f"Model cached: {repo_id}")
        except Exception as e:
            # Do not fail startup; just log the warning
            logger.warning(f"Failed to cache model {repo_id}: {e}")

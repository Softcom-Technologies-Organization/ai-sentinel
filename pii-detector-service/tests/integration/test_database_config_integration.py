import importlib
import math
from typing import Dict

import pytest

from pii_detector.infrastructure.adapter.out.database_config_adapter import (
    DatabaseConfigAdapter,
)
# Reuse the PostgreSQL Testcontainer fixture to populate DB using data.sql
from .fixtures.database_fixtures import postgres_container  # noqa: F401


@pytest.mark.integration
def test_global_config_is_applied_from_database(postgres_container):
    adapter = DatabaseConfigAdapter()
    cfg = adapter.fetch_config()

    assert cfg is not None, "Global config should be fetched from DB"
    assert cfg["gliner_enabled"] is True
    assert cfg["presidio_enabled"] is True
    assert cfg["regex_enabled"] is True

    # default_threshold can be Decimal depending on driver; compare as float
    assert math.isclose(float(cfg["default_threshold"]), 0.80, abs_tol=1e-6)


@pytest.mark.integration
def test_all_pii_type_configs_are_loaded(postgres_container):
    adapter = DatabaseConfigAdapter()
    configs = adapter.fetch_pii_type_configs()

    assert configs is not None, "PII type configs should be fetched from DB"
    # Ensure we have at least 82 configurations (actual count from data.sql)
    assert len(configs) >= 82, f"Expected >= 82 configs, got {len(configs)}"

    # Validate required fields on a sample
    sample_key = next(iter(configs.keys()))
    sample = configs[sample_key]
    for key in ("enabled", "threshold", "detector", "display_name", "detector_label"):
        assert key in sample, f"Missing '{key}' in sample config for {sample_key}"


@pytest.mark.integration
def test_all_pii_types_enable_disable_and_threshold(postgres_container):
    """
    For each PII type found in DB config, verify:
    - When enabled and score >= threshold, entity is kept
    - When enabled and score < threshold, entity is filtered out
    - When disabled, entity is filtered out regardless of score
    """
    adapter = DatabaseConfigAdapter()
    db_configs: Dict[str, dict] = adapter.fetch_pii_type_configs() or {}
    assert len(db_configs) >= 82

    # Import the service module via importlib because package path contains
    # a Python keyword component ("in"), which cannot be used in a from-import.
    service_module = importlib.import_module(
        "pii_detector.infrastructure.adapter.in.grpc.pii_service"
    )
    # Use unbound method to avoid initializing heavy detector in the service
    filter_fn = service_module.PIIDetectionServicer._filter_entities_by_type_config

    tested = 0
    for pii_type, conf in db_configs.items():
        tested += 1

        # 1) Enabled + score >= threshold => kept
        base_cfg = {pii_type: {**conf, "enabled": True}}
        entities = [{"type": pii_type, "text": "X", "score": max(0.99, float(conf["threshold"]))}]
        kept = filter_fn(None, entities, base_cfg, request_id="t")
        # The filter returns original entities unchanged; only check length
        assert len(kept) == 1, (
            f"Expected entity kept for {pii_type} when enabled and above threshold"
        )

        # 2) Enabled + score < threshold => filtered out
        below_score = max(0.0, float(conf["threshold"]) - 0.05)
        entities = [{"type": pii_type, "text": "X", "score": below_score}]
        kept = filter_fn(None, entities, base_cfg, request_id="t")
        assert len(kept) == 0, f"Expected entity filtered for {pii_type} when below threshold"

        # 3) Disabled, high score => filtered out
        disabled_cfg = {pii_type: {**conf, "enabled": False}}
        entities = [{"type": pii_type, "text": "X", "score": 0.99}]
        kept = filter_fn(None, entities, disabled_cfg, request_id="t")
        assert len(kept) == 0, f"Expected entity filtered for {pii_type} when disabled"

    assert tested == len(db_configs)

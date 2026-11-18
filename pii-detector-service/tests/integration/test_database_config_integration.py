import importlib

import pytest

from pii_detector.infrastructure.adapter.out.database_config_adapter import (
    DatabaseConfigAdapter,
)

# Reuse the PostgreSQL Testcontainer fixture to populate DB using data.sql
from .fixtures.database_fixtures import postgres_container  # noqa: F401

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("postgres_container")]


def test_global_config_is_applied_from_database():
    adapter = DatabaseConfigAdapter()
    cfg = adapter.fetch_config()

    assert cfg is not None, "Global config should be fetched from DB"
    assert cfg["presidio_enabled"] is True
    assert cfg["regex_enabled"] is True

    assert float(cfg["default_threshold"]) == pytest.approx(0.30)
    assert cfg["trio_enabled"] is False
    assert all(cfg[f"{model}_enabled"] for model in ("bards", "cbx", "ettin"))


def test_all_pii_type_configs_are_loaded():
    adapter = DatabaseConfigAdapter()
    configs = adapter.fetch_pii_type_configs()

    assert configs is not None, "PII type configs should be fetched from DB"
    assert {config["detector"] for config in configs.values()} == {
        "PRESIDIO", "REGEX", "MINISTRAL", "BARDS", "CBX", "ETTIN",
    }
    for model, count in (("BARDS", 37), ("CBX", 12), ("ETTIN", 55)):
        assert sum(key.startswith(f"{model}:") for key in configs) == count

    # Validate required fields on a sample
    sample_key = next(iter(configs.keys()))
    sample = configs[sample_key]
    for key in ("enabled", "threshold", "detector", "category", "detector_label"):
        assert key in sample, f"Missing '{key}' in sample config for {sample_key}"


def test_all_pii_types_enable_disable_and_threshold():
    """
    For each PII type found in DB config, verify:
    - When enabled and score >= threshold, entity is kept
    - When enabled and score < threshold, entity is filtered out
    - When disabled, entity is filtered out regardless of score
    """
    adapter = DatabaseConfigAdapter()
    db_configs = adapter.fetch_pii_type_configs() or {}
    per_detector = {key: config for key, config in db_configs.items() if ":" in key}
    assert per_detector

    # Import the service module via importlib because package path contains
    # a Python keyword component ("in"), which cannot be used in a from-import.
    service_module = importlib.import_module(
        "pii_detector.infrastructure.adapter.in.grpc.pii_service"
    )
    servicer = service_module.PIIDetectionServicer.__new__(service_module.PIIDetectionServicer)
    filter_fn = servicer._filter_entities_by_type_config

    for config_key, conf in per_detector.items():
        detector, pii_type = config_key.split(":", 1)
        threshold = float(conf["threshold"])
        base_cfg = {config_key: {**conf, "enabled": True}}
        entity = {"type": pii_type, "source": detector, "text": "X", "score": threshold}
        assert filter_fn([entity], base_cfg, request_id="t") == [entity], (
            f"Expected entity kept for {pii_type} when enabled and above threshold"
        )

        positive_threshold = max(0.05, threshold)
        base_cfg[config_key]["threshold"] = positive_threshold
        entity["score"] = positive_threshold - 0.05
        assert filter_fn([entity], base_cfg, request_id="t") == [], (
            f"Expected entity filtered for {pii_type} when below threshold"
        )

        disabled_cfg = {config_key: {**conf, "enabled": False}}
        entity["score"] = 1.0
        assert filter_fn([entity], disabled_cfg, request_id="t") == [], (
            f"Expected entity filtered for {pii_type} when disabled"
        )

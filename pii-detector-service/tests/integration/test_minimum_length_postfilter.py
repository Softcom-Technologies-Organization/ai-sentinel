import importlib
from unittest.mock import Mock

import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.proto.generated import pii_detection_pb2

pii_service = importlib.import_module("pii_detector.infrastructure.adapter.in.grpc.pii_service")


@pytest.mark.integration
@pytest.mark.parametrize("enabled", [True, False])
@pytest.mark.parametrize(
    "source", [DetectorSource.MINISTRAL, DetectorSource.REGEX, DetectorSource.PRESIDIO]
)
def test_Should_PreserveSecretsAndSerializeStructuredRejections_When_PostfilterEnabled(
    monkeypatch, enabled, source
):
    candidates = [
        ("Ab1!x", "PASSWORD"),
        ("1234", "PASSWORD"),
        ("secret", "PASSWORD"),
        ("the moon is blue", "PASSPHRASE"),
        ("a", "API_KEY"),
        ("ghp_abc", "GITHUB_TOKEN"),
        ("JSESSIONID=abc; Secure", "HTTP_COOKIE"),
        ("1234", "SOCIAL_SECURITY_NUMBER"),
        ("4321", "ACCOUNT_NUMBER"),
    ]
    content = "\n".join(f"{kind}: {value}" for value, kind in candidates)
    entities = [
        PIIEntity(
            text=value,
            pii_type=kind,
            type_label=kind,
            start=content.index(f"{kind}: {value}") + len(kind) + 2,
            end=content.index(f"{kind}: {value}") + len(kind) + 2 + len(value),
            score=1.0,
            source=source,
        )
        for value, kind in candidates
    ]
    detector = Mock()
    detector._apply_masks.return_value = content
    monkeypatch.setattr(pii_service, "get_detector_instance", lambda: detector)
    for method in ("_run_startup_concurrency_autotune", "_init_worker_pool", "_start_bench_poller"):
        monkeypatch.setattr(pii_service.PIIDetectionServicer, method, lambda self: None)
    servicer = pii_service.PIIDetectionServicer(enable_memory_monitoring=False)
    configs = {
        kind: {"enabled": True, "threshold": 0.5, "detector": "ALL"}
        for _, kind in candidates
    }
    monkeypatch.setattr(
        servicer,
        "_fetch_and_apply_config",
        lambda *args: (0.5, configs, {"postfilter_enabled": enabled}),
    )
    monkeypatch.setattr(servicer, "_execute_detection", lambda *args: (entities, []))
    request = pii_detection_pb2.PIIDetectionRequest(
        content=content,
        threshold=0.5,
        fetch_config_from_db=True,
    )
    context = Mock()

    response = servicer.DetectPII(request, context)
    response = pii_detection_pb2.PIIDetectionResponse.FromString(response.SerializeToString())

    context.set_code.assert_not_called()
    assert [entity.text for entity in response.entities] == (
        [value for value, _ in candidates[:-1]]
        if enabled else [value for value, _ in candidates]
    )
    assert len(response.discarded_entities) == int(enabled)
    if enabled:
        rejected = response.discarded_entities[0]
        assert (rejected.entity.text, rejected.entity.start, rejected.entity.end) == (
            "4321", entities[-1].start, entities[-1].end
        )
        assert rejected.judge_verdict == "FALSE_POSITIVE"
        assert rejected.judge_reason == "minimum length: ACCOUNT_NUMBER measured 4, required 5"
        assert response.detector_stats[0].entities_discarded == 1

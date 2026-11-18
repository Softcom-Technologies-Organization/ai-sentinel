import multiprocessing as mp
import threading
import time
from functools import partial
from unittest.mock import Mock, patch

import pytest

from pii_detector.infrastructure.detector.trio_detector import TrioDetector, enabled_trio_models


def config(source, enabled=True, threshold=0.0):
    return {f"{source}:PASSWORD": {
        "detector": source, "detector_label": "password",
        "enabled": enabled, "threshold": threshold,
    }}


def prediction(start=0, end=6, score=0.9):
    return {"start": start, "end": end, "label": "password", "score": score}


def test_Should_ConjoinParentAndChild_When_ResolvingModels():
    assert enabled_trio_models({"trio_enabled": False}) == ()
    assert enabled_trio_models({"trio_enabled": True, "cbx_enabled": False}) == ("bards", "ettin")


def test_Should_DispatchAllModelsBeforeWaiting_When_Detecting():
    sent = []
    context = Mock()
    for name in ("bards", "cbx", "ettin"):
        pipe = Mock()
        pipe.send.side_effect = lambda payload, name=name: sent.append(name)
        pipe.poll.side_effect = lambda timeout: len(sent) == 3
        pipe.recv.return_value = {"predictions": [prediction()], "duration_ms": 5, "error": ""}
        context.attach_mock(pipe, name)
    context.Pipe.side_effect = [(getattr(context, name), Mock()) for name in ("bards", "cbx", "ettin")]
    with patch("pii_detector.infrastructure.detector.trio_detector.mp.get_context", return_value=context):
        detector = TrioDetector()
    configs = config("BARDS") | config("CBX") | config("ETTIN")
    entities, stats = detector.detect("secret", ("bards", "cbx", "ettin"), configs)
    assert sorted(sent) == ["bards", "cbx", "ettin"]
    assert {entity.source.name for entity in entities} == {"BARDS", "CBX", "ETTIN"}
    assert len(stats) == 3
    assert all(not stat["error"] for stat in stats)
    detector.close()


@pytest.mark.parametrize("enabled,score,threshold,expected", [(False, .9, 0, 0), (True, .4, .5, 0), (True, .5, .5, 1)])
def test_Should_ApplyTypeSettings_When_ConvertingPredictions(enabled, score, threshold, expected):
    entities = TrioDetector._entities("secret", "cbx", [prediction(score=score)], config("CBX", enabled, threshold))
    assert len(entities) == expected


def test_Should_UseOriginalUnicodeOffsets_When_ConvertingPredictions():
    entities = TrioDetector._entities("🔒 sécret", "cbx", [prediction(2, 8)], config("CBX"))
    assert entities[0].text == "sécret"
    assert (entities[0].start, entities[0].end) == (2, 8)


def test_Should_RejectInvalidOffsets_When_ModelReturnsMalformedSpan():
    spans, configs = [prediction(-1, 6)], config("CBX")
    with pytest.raises(ValueError, match="offsets"):
        TrioDetector._entities("secret", "cbx", spans, configs)


def test_Should_ExcludeUnconfiguredTypes_When_ConvertingPredictions():
    assert TrioDetector._entities("secret", "cbx", [prediction()], config("BARDS")) == []


def concurrent_worker(name, connection, barrier):
    while (text := connection.recv()) is not None:
        barrier.wait(timeout=10)
        connection.send({"predictions": [prediction(end=len(text))], "duration_ms": 1, "error": ""})


def test_Should_RunSeparateProcessesConcurrently_When_AllModelsEnabled():
    barrier = mp.get_context("spawn").Barrier(3)
    with patch("pii_detector.infrastructure.detector.trio_detector._model_worker", partial(concurrent_worker, barrier=barrier)):
        detector = TrioDetector(timeout=15)
        try:
            configs = config("BARDS") | config("CBX") | config("ETTIN")
            entities, stats = detector.detect("secret", ("bards", "cbx", "ettin"), configs)
            assert len(entities) == 3
            assert all(not stat["error"] for stat in stats)
            assert len({worker[0].pid for worker in detector._workers.values()}) == 3
        finally:
            detector.close()


def test_Should_ReportTimeoutAndRecreateWorker_When_ModelStopsResponding():
    detector = TrioDetector(timeout=.01)
    process, connection = Mock(), Mock()
    connection.poll.return_value = False
    detector._workers["cbx"] = process, connection
    entities, stats = detector.detect("secret", ("cbx",), config("CBX"))
    assert entities == []
    assert "timed out" in stats[0]["error"]
    assert not detector._workers
    process.terminate.assert_called_once()
    detector.close()


def test_Should_ReportFailure_When_TypeConfigurationUnavailable():
    detector = TrioDetector()
    entities, stats = detector.detect("secret", ("cbx",), None)
    assert not entities
    assert "configuration unavailable" in stats[0]["error"]
    assert not detector._workers
    detector.close()


def test_Should_KeepComplementaryCoverage_When_TrioSpansPartiallyOverlap():
    from pii_detector.application.orchestration.composite_detector import CompositePIIDetector

    spans = TrioDetector._entities("abcdefghij", "cbx", [prediction(0, 6), prediction(4, 10)], config("CBX"))
    merged = CompositePIIDetector._merge_trio_entities([], spans)
    assert [(entity.start, entity.end) for entity in merged] == [(0, 6), (4, 10)]


@pytest.mark.parametrize("native_type,expected_count", [("PASSWORD", 1), ("AUTH_SECRET", 2)])
def test_Should_MergeNativeAndEnumTypes_When_TrioAndLegacyDetectorsShareOffsets(native_type, expected_count):
    from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
    from pii_detector.domain.entity.detector_source import DetectorSource
    from pii_detector.domain.entity.pii_entity import PIIEntity
    from pii_detector.domain.entity.pii_type import PIIType

    legacy = PIIEntity("secret", PIIType.PASSWORD, "Password", 0, 6, .8, DetectorSource.REGEX)
    native = PIIEntity("secret", native_type, native_type, 0, 6, .9, DetectorSource.CBX)
    merged = CompositePIIDetector._merge_trio_entities([legacy], [native])
    assert len(merged) == expected_count
    assert native in merged


def blocked_worker(name, connection):
    time.sleep(30)


def test_Should_BoundLargeRequestDispatch_When_WorkerNeverReads():
    with patch("pii_detector.infrastructure.detector.trio_detector._model_worker", blocked_worker):
        detector = TrioDetector(timeout=.3)
        try:
            started = time.monotonic()
            entities, stats = detector.detect("x" * 1_000_000, ("cbx",), config("CBX"))
            assert not entities
            assert "timed out" in stats[0]["error"]
            assert time.monotonic() - started < 5
        finally:
            detector.close()


def test_Should_ReleaseDispatchThreads_When_LargeRequestsRepeatedlyTimeOut():
    before = {thread.ident for thread in threading.enumerate()}
    with patch("pii_detector.infrastructure.detector.trio_detector._model_worker", blocked_worker):
        detector = TrioDetector(timeout=.1)
        try:
            for _ in range(3):
                detector.detect("x" * 1_000_000, ("cbx",), config("CBX"))
        finally:
            detector.close()
    remaining = [thread.name for thread in threading.enumerate()
                 if thread.ident not in before and (thread.name == "QueueFeederThread" or thread.name.startswith("trio-send"))]
    assert remaining == []


@pytest.mark.parametrize("offsets", [[(0, 6), (4, 10)], [(0, 6), (0, 10)], [(0, 10), (3, 6)]])
def test_Should_MaskEntireUnion_When_TrioSpansOverlap(offsets):
    from pii_detector.application.orchestration.composite_detector import CompositePIIDetector

    spans = TrioDetector._entities("abcdefghij", "cbx", [prediction(a, b) for a, b in offsets], config("CBX"))
    assert CompositePIIDetector._apply_masks(None, "abcdefghij", spans) == "[PASSWORD]"

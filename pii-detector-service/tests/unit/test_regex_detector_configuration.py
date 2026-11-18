import pytest

from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
from pii_detector.infrastructure.detector.regex_detector import RegexDetector


def config(key='AVS_NUMBER', enabled=True, threshold=0.5):
    return {f'REGEX:{key}': {'detector': 'REGEX', 'enabled': enabled, 'threshold': threshold}}


@pytest.fixture(scope='module')
def detector():
    return RegexDetector()


@pytest.mark.parametrize('separator', ['', ' ', '.', '-', '\u00a0', '\u202f'])
def test_Should_KeepOriginalAvsSpan_When_SeparatorChanges(detector, separator):
    value = separator.join(['756', '9217', '0769', '85'])
    results = detector.detect_pii(f'({value})', pii_type_configs=config('AVS_NUMBER'))
    assert [(r.text, r.start, r.end) for r in results] == [(value, 1, len(value) + 1)]



def test_Should_ApplyFreshFlagsAndThresholds_When_DetectingAvs(detector):
    text = '756.9217.0769.85'
    assert [r.pii_type for r in detector.detect_pii(text, pii_type_configs=config())] == ['AVS_NUMBER']
    assert detector.detect_pii(text, pii_type_configs=config(enabled=False)) == []
    assert detector.detect_pii(text, pii_type_configs=config(threshold=0.99)) == []
    assert detector.detect_pii(text, pii_type_configs={}) == []


def test_Should_IgnoreConfiguration_When_ItBelongsToAnotherDetector(detector):
    settings = {'AVS_NUMBER': {'detector': 'MINISTRAL', 'enabled': True, 'threshold': 0.5}}
    assert detector.detect_pii('756.9217.0769.85', pii_type_configs=settings) == []


def test_Should_PassRequestConfiguration_When_UsingComposite(detector):
    composite = CompositePIIDetector(regex_detector=detector, enable_presidio=False,
                                    enable_ministral=False)
    text = '756.9217.0769.85'
    results = composite.detect_pii(text, pii_type_configs=config())
    assert [(r.text, r.pii_type) for r in results] == [(text, 'AVS_NUMBER')]
    assert composite.detect_pii(text, pii_type_configs=config(enabled=False)) == []

from pathlib import Path
from importlib import import_module

import pytest
import toml

from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
from pii_detector.infrastructure.detector.regex_detector import RegexDetector

CATALOGUE = Path(__file__).parents[2] / 'config/models/national-identifiers.toml'
RULES = toml.load(CATALOGUE)['rules']


def config(key, enabled=True, threshold=0.5):
    return {f'REGEX:{key}': {'detector': 'REGEX', 'enabled': enabled, 'threshold': threshold}}


@pytest.fixture(scope='module')
def detector():
    return RegexDetector()


@pytest.mark.parametrize('rule', RULES, ids=lambda r: r['key'])
def test_Should_ExtractWholeIdentifier_When_OnlyItsRuleIsEnabled(detector, rule):
    value = rule['example']
    text = f'({value})'
    results = detector.detect_pii(text, pii_type_configs=config(rule['key']))
    assert [(r.text, r.pii_type, r.start, r.end) for r in results] == [
        (value, rule['entity_type'], 1, 1 + len(value))
    ]
    assert results[0]._config_pii_type == rule['key']


@pytest.mark.parametrize('key,value', [
    ('SSN_FR_NIR', '2 95 10 99 126 111 99'),
    ('SSN_BE_NISS', '85 07 30 033 29'),
    ('SSN_BE_NISS', '98 47 28 997 66'),
    ('TAX_ID_CH_UID', 'CHE-100.155.213'),
    ('TAX_ID_DE_IDNR', '36574261890'),
    ('SSN_US_SSN', '900-12-3456'),
    ('SSN_US_SSN', '000-12-3456'),
    ('SSN_US_SSN', '536-00-4399'),
    ('SSN_GB_NINO', 'BG123456A'),
    ('SSN_FR_NIR', '2 95 10\n99 126 111 93'),
    ('TAX_ID_CH_UID', 'CHE-100.155.212-99'),
    ('TAX_ID_DE_IDNR', 'A36574261809B'),
    ('TAX_ID_DE_IDNR', '1365742618099'),
])
def test_Should_RejectInvalidOrEmbeddedIdentifier_WithoutKeywords(detector, key, value):
    assert detector.detect_pii(value, pii_type_configs=config(key)) == []


def test_Should_ApplyFreshRuleFlagsAndThresholds_BeforeResolvingOverlaps(detector):
    text = '756.9217.0769.85'
    settings = {**config('AVS_NUMBER', False), **config('TAX_ID_CH_AVS')}
    assert [r.pii_type for r in detector.detect_pii(text, pii_type_configs=settings)] == ['TAX_ID']
    settings['REGEX:TAX_ID_CH_AVS']['enabled'] = False
    assert detector.detect_pii(text, pii_type_configs=settings) == []
    settings['REGEX:TAX_ID_CH_AVS'].update(enabled=True, threshold=0.99)
    assert detector.detect_pii(text, pii_type_configs=settings) == []
    assert detector.detect_pii(text, pii_type_configs={}) == []


def test_Should_IgnoreConfigurationOfAnotherDetector(detector):
    settings = {'SSN_US_SSN': {'detector': 'MINISTRAL', 'enabled': True, 'threshold': 0.5}}
    assert detector.detect_pii('536-90-4399', pii_type_configs=settings) == []


def test_Should_PassRequestConfiguration_ThroughComposite(detector):
    composite = CompositePIIDetector(regex_detector=detector, enable_presidio=False,
                                    enable_ministral=False)
    results = composite.detect_pii('36 574 261 809', pii_type_configs=config('TAX_ID_DE_IDNR'))
    assert [(r.text, r.pii_type) for r in results] == [('36 574 261 809', 'TAX_ID')]
    assert composite.detect_pii('36 574 261 809', pii_type_configs=config('TAX_ID_DE_IDNR', False)) == []


@pytest.mark.parametrize('separator', ['', ' ', '.', '-', '\u00a0', '\u202f'])
def test_Should_KeepOriginalAvsSpan_When_SeparatorChanges(detector, separator):
    value = separator.join(['756', '9217', '0769', '85'])
    results = detector.detect_pii(f'({value})', pii_type_configs=config('AVS_NUMBER'))
    assert [(r.text, r.start, r.end) for r in results] == [(value, 1, len(value) + 1)]


def test_Should_FilterByNationalRule_WhileReturningCanonicalGrpcType(detector):
    module = import_module('pii_detector.infrastructure.adapter.in.grpc.pii_service')
    servicer = object.__new__(module.PIIDetectionServicer)
    entity = detector.detect_pii('36574261809', pii_type_configs=config('TAX_ID_DE_IDNR'))[0]
    kept, reason = servicer._evaluate_entity_filter(entity, config('TAX_ID_DE_IDNR', False), 0, 'test')
    assert not kept
    assert reason.endswith(':disabled')
    proto = module.pii_detection_pb2.PIIEntity()
    servicer._populate_proto_entity(proto, entity)
    assert proto.type == 'TAX_ID'
    assert proto.text == '36574261809'

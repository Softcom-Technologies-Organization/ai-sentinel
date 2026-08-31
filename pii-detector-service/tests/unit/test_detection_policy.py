"""Unit tests for the detection configuration policy.

This module decides which LLM models are active and where each detection
default comes from. Both were uncovered, yet a mistake in either changes
detection behaviour silently rather than failing:

- ``get_enabled_models`` filters out the non-LLM detectors, rejects a model
  entry that carries no ``model_id`` (a plain patterns TOML is not a model),
  and orders the rest by priority — the first entry becomes the primary model;
- ``DetectionConfig`` fills only the attributes left to None, and a
  model-specific threshold must win over the global default;
- a missing config file or a missing key must surface as an actionable error,
  not as a bare KeyError from deep inside the loader.

``_load_llm_config`` is patched throughout so no real TOML is read.
"""
from __future__ import annotations

from unittest.mock import patch

import pytest

from pii_detector.application.config import detection_policy as dp
from pii_detector.application.config.detection_policy import (
    DetectionConfig,
    get_enabled_models,
)

_LOADER = 'pii_detector.application.config.detection_policy._load_llm_config'


def _config(models: dict, detection: dict | None = None) -> dict:
    return {'detection': detection if detection is not None else {}, 'models': models}


class TestGetEnabledModels:
    def test_Should_RaiseValueError_When_NoModelsSection(self):
        with pytest.raises(ValueError, match=r'\[models\]'):
            get_enabled_models({'detection': {}})

    def test_Should_ReturnEmpty_When_NoModelIsEnabled(self):
        # A valid state: detection can be Regex / Presidio / Ministral only.
        config = _config({'some-llm': {'enabled': False, 'model_id': 'acme/x'}})
        assert get_enabled_models(config) == []

    @pytest.mark.parametrize('detector', ['regex-detector', 'presidio-detector'])
    def test_Should_SkipNonLlmDetectors_When_TheyAreEnabled(self, detector):
        config = _config({detector: {'enabled': True, 'model_id': 'irrelevant'}})
        assert get_enabled_models(config) == []

    def test_Should_RejectEntry_When_ModelIdIsMissing(self):
        # A patterns-only TOML (e.g. regex-patterns.toml) is not a model.
        config = _config({'regex-patterns': {'enabled': True}})
        assert get_enabled_models(config) == []

    def test_Should_SortByPriorityAscending_When_SeveralModelsEnabled(self):
        config = _config({
            'slow': {'enabled': True, 'model_id': 'acme/slow', 'priority': 10},
            'fast': {'enabled': True, 'model_id': 'acme/fast', 'priority': 1},
        })

        assert [m['name'] for m in get_enabled_models(config)] == ['fast', 'slow']

    def test_Should_PlaceUnprioritisedModelLast_When_PriorityIsAbsent(self):
        config = _config({
            'unranked': {'enabled': True, 'model_id': 'acme/unranked'},
            'ranked': {'enabled': True, 'model_id': 'acme/ranked', 'priority': 5},
        })

        models = get_enabled_models(config)
        assert [m['name'] for m in models] == ['ranked', 'unranked']
        assert models[1]['priority'] == 999

    def test_Should_ExposeDeclaredValues_When_ModelIsFullySpecified(self):
        config = _config({'llm': {
            'enabled': True,
            'model_id': 'acme/llm',
            'priority': 2,
            'device': 'cuda',
            'max_length': 512,
            'threshold': 0.42,
            'description': 'a model',
            'download': {'custom_filenames': {'weights': 'model.bin'}},
        }})

        assert get_enabled_models(config)[0] == {
            'name': 'llm',
            'model_id': 'acme/llm',
            'priority': 2,
            'device': 'cuda',
            'max_length': 512,
            'threshold': 0.42,
            'description': 'a model',
            'custom_filenames': {'weights': 'model.bin'},
        }

    def test_Should_DefaultMaxLengthAndBlankDescription_When_Omitted(self):
        config = _config({'llm': {'enabled': True, 'model_id': 'acme/llm'}})

        model = get_enabled_models(config)[0]
        assert model['max_length'] == 256
        assert model['description'] == ''
        assert model['device'] is None
        assert model['threshold'] is None
        assert model['custom_filenames'] is None


class TestDetectionConfigDefaults:
    def test_Should_PreferModelThreshold_When_ModelDeclaresOne(self):
        config = _config(
            {'llm': {'enabled': True, 'model_id': 'acme/llm', 'threshold': 0.9}},
            {'default_threshold': 0.5},
        )

        with patch(_LOADER, return_value=config):
            assert DetectionConfig().threshold == 0.9

    def test_Should_FallBackToGlobalThreshold_When_ModelDeclaresNone(self):
        config = _config(
            {'llm': {'enabled': True, 'model_id': 'acme/llm'}},
            {'default_threshold': 0.35},
        )

        with patch(_LOADER, return_value=config):
            assert DetectionConfig().threshold == 0.35

    def test_Should_AdoptPrimaryModel_When_NoValueWasProvided(self):
        config = _config({
            'fast': {'enabled': True, 'model_id': 'acme/fast', 'priority': 1,
                     'device': 'cpu', 'max_length': 128},
            'slow': {'enabled': True, 'model_id': 'acme/slow', 'priority': 9},
        })

        with patch(_LOADER, return_value=config):
            subject = DetectionConfig()

        assert subject.model_id == 'acme/fast'
        assert subject.device == 'cpu'
        assert subject.max_length == 128

    def test_Should_KeepCallerValues_When_ExplicitlyProvided(self):
        config = _config(
            {'llm': {'enabled': True, 'model_id': 'acme/llm', 'threshold': 0.9}},
            {'batch_size': 4},
        )

        with patch(_LOADER, return_value=config):
            subject = DetectionConfig(model_id='caller/model', threshold=0.1, batch_size=32)

        assert subject.model_id == 'caller/model'
        assert subject.threshold == 0.1
        assert subject.batch_size == 32

    def test_Should_UseDetectionSectionDefaults_When_SectionIsEmpty(self):
        with patch(_LOADER, return_value=_config({}, {})):
            subject = DetectionConfig()

        assert subject.threshold == 0.5
        assert subject.batch_size == 4
        assert subject.stride_tokens == 64
        assert subject.long_text_threshold == 10000
        # No enabled model means no model to inherit an id from.
        assert subject.model_id is None


class TestDetectionConfigErrors:
    def test_Should_ExplainExpectedLayout_When_ConfigFileIsMissing(self):
        with patch(_LOADER, side_effect=FileNotFoundError('detection-settings.toml')):
            with pytest.raises(FileNotFoundError, match='config/'):
                DetectionConfig()

    def test_Should_NameTheMissingKey_When_SectionIsIncomplete(self):
        # [detection] absent: the loader succeeds but _apply_defaults cannot
        # resolve the global default, and the operator needs to know which key.
        with patch(_LOADER, return_value={'models': {}}):
            with pytest.raises(ValueError, match='detection'):
                DetectionConfig()

    def test_Should_WrapUnexpectedFailure_When_LoaderRaises(self):
        with patch(_LOADER, side_effect=RuntimeError('malformed TOML')):
            with pytest.raises(ValueError, match='malformed TOML'):
                DetectionConfig()


class TestLoadLlmConfig:
    def test_Should_RaiseFileNotFound_When_SettingsFileIsAbsent(self, tmp_path, monkeypatch):
        # Point the loader at an empty tree: it resolves its config directory
        # relative to its own file, so the module path is what gets redirected.
        monkeypatch.setattr(dp, '__file__', str(tmp_path / 'a/b/c/detection_policy.py'))

        with pytest.raises(FileNotFoundError, match='detection-settings.toml'):
            dp._load_llm_config()

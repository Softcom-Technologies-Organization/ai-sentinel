"""
Unit tests for CompositePIIDetector.

Tests the hybrid detection approach combining Regex, Presidio, and Ministral detectors.
"""

from unittest.mock import Mock, patch

import pytest

from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
from pii_detector.domain.entity.detector_failure import (
    DetectorFailure,
    DetectorFailureCode,
)
from pii_detector.domain.entity.pii_entity import PIIEntity


class TestCompositePIIDetector:
    """Test CompositePIIDetector class."""

    @pytest.fixture
    def mock_ministral_detector(self):
        """Create mock Ministral detector."""
        detector = Mock()
        detector.model_id = "mock-ministral-detector"
        detector.download_model = Mock()
        detector.load_model = Mock()
        detector.detect_pii = Mock(return_value=[])
        return detector

    @pytest.fixture
    def mock_regex_detector(self):
        """Create mock regex detector."""
        detector = Mock()
        detector.model_id = "regex-detector"
        detector.download_model = Mock()
        detector.load_model = Mock()
        detector.detect_pii = Mock(return_value=[])
        detector.mask_pii = Mock(return_value=("", []))
        return detector

    def test_Should_InitializeComposite_When_AllDetectorsProvided(
        self, mock_ministral_detector, mock_regex_detector
    ):
        """Should initialize with regex and Ministral detectors."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_presidio=False,
            enable_ministral=True,
        )

        assert composite.regex_detector is mock_regex_detector
        assert composite.ministral_detector is mock_ministral_detector
        assert composite.enable_regex is True
        assert composite.enable_ministral is True

    def test_Should_DisableMinistral_When_NotRequested(self, mock_regex_detector):
        """Should keep Ministral disabled by default (opt-in detector)."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            enable_presidio=False,
        )

        assert composite.enable_ministral is False

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_CallAllEnabledDetectors_When_Detecting(
        self, mock_presidio_class, mock_ministral_detector, mock_regex_detector
    ):
        """Should call both Regex and Ministral detectors."""
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        ministral_entity = PIIEntity(
            text="John Doe",
            pii_type="GIVENNAME",
            type_label="GIVENNAME",
            start=0,
            end=8,
            score=0.85
        )
        mock_ministral_detector.detect_pii.return_value = [ministral_entity]

        regex_entity = PIIEntity(
            text="test@example.com",
            pii_type="EMAIL",
            type_label="EMAIL",
            start=20,
            end=36,
            score=0.95
        )
        mock_regex_detector.detect_pii.return_value = [regex_entity]

        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "John Doe, email: test@example.com"
        entities = composite.detect_pii(text)

        mock_regex_detector.detect_pii.assert_called_once_with(text, None)
        assert mock_ministral_detector.detect_pii.call_count == 1
        assert len(entities) >= 1

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_DegradeSilently_When_MinistralFails(
        self, mock_presidio_class, mock_ministral_detector, mock_regex_detector
    ):
        """Should keep Regex results when the Ministral detector raises.

        All detector branches degrade silently (log + return []) so a single
        detector failure never breaks the whole request.
        """
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        mock_ministral_detector.detect_pii.side_effect = Exception("Ministral failed")

        regex_entity = PIIEntity(
            text="test@example.com",
            pii_type="EMAIL",
            type_label="EMAIL",
            start=0,
            end=16,
            score=0.95
        )
        mock_regex_detector.detect_pii.return_value = [regex_entity]

        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "test@example.com"
        entities = composite.detect_pii(text)

        assert len(entities) >= 1
        assert any(e.pii_type == "EMAIL" for e in entities)

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_HandleRegexFailure_When_ExceptionThrown(
        self, mock_presidio_class, mock_ministral_detector, mock_regex_detector
    ):
        """Should continue with Ministral results if the regex detector fails."""
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        mock_regex_detector.detect_pii.side_effect = Exception("Regex failed")

        ministral_entity = PIIEntity(
            text="John Doe",
            pii_type="GIVENNAME",
            type_label="GIVENNAME",
            start=0,
            end=8,
            score=0.85
        )
        mock_ministral_detector.detect_pii.return_value = [ministral_entity]

        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "John Doe"
        entities = composite.detect_pii(text)

        assert len(entities) >= 1
        assert any(e.pii_type == "GIVENNAME" for e in entities)

    def test_Should_ReturnEmpty_When_EmptyText(
        self, mock_ministral_detector, mock_regex_detector
    ):
        """Should return empty list for empty text."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_presidio=False,
            enable_ministral=True,
        )

        entities = composite.detect_pii("")

        assert entities == []
        mock_ministral_detector.detect_pii.assert_not_called()
        mock_regex_detector.detect_pii.assert_not_called()

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_PassThreshold_When_Provided(
        self, mock_presidio_class, mock_ministral_detector, mock_regex_detector
    ):
        """Should pass threshold to Regex and Ministral detectors."""
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "test text"
        threshold = 0.8
        composite.detect_pii(text, threshold=threshold)

        mock_regex_detector.detect_pii.assert_called_once_with(text, threshold)
        mock_ministral_detector.detect_pii.assert_called_once_with(text, threshold)

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_MaskPII_When_DetectedEntities(
        self, mock_presidio_class, mock_ministral_detector, mock_regex_detector
    ):
        """Should mask detected PII from both Regex and Ministral detectors."""
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        ministral_entity = PIIEntity(
            text="John",
            pii_type="GIVENNAME",
            type_label="GIVENNAME",
            start=0,
            end=4,
            score=0.85
        )
        regex_entity = PIIEntity(
            text="test@example.com",
            pii_type="EMAIL",
            type_label="EMAIL",
            start=12,
            end=28,
            score=0.95
        )

        mock_ministral_detector.detect_pii.return_value = [ministral_entity]
        mock_regex_detector.detect_pii.return_value = [regex_entity]

        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "John, email: test@example.com"
        masked_text, entities = composite.mask_pii(text)

        assert "[GIVENNAME]" in masked_text
        assert "[EMAIL]" in masked_text
        assert "John" not in masked_text
        assert "test@example.com" not in masked_text

    def test_Should_DownloadModels_When_Called(
        self, mock_ministral_detector, mock_regex_detector
    ):
        """Should call download_model on all enabled detectors."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_presidio=False,
            enable_ministral=True,
        )

        composite.download_model()

        mock_ministral_detector.download_model.assert_called_once()
        mock_regex_detector.download_model.assert_called_once()

    def test_Should_LoadModels_When_Called(
        self, mock_ministral_detector, mock_regex_detector
    ):
        """Should call load_model on all enabled detectors."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_presidio=False,
            enable_ministral=True,
        )

        composite.load_model()

        mock_ministral_detector.load_model.assert_called_once()
        mock_regex_detector.load_model.assert_called_once()

    def test_Should_ReturnFixedCompositeModelId(self, mock_regex_detector):
        """Should always return the fixed composite model ID regardless of wiring."""
        composite = CompositePIIDetector(
            regex_detector=mock_regex_detector,
            enable_presidio=False,
        )

        assert composite.model_id == "composite-regex-presidio-ministral"

    @patch('pii_detector.application.orchestration.composite_detector.RegexDetector')
    def test_Should_ContinueWithoutRegex_When_InitializationFails(
        self, mock_regex_class, mock_ministral_detector
    ):
        """Should continue without regex if initialization fails."""
        # This tests the fallback behavior when regex detector can't be created
        # Make RegexDetector creation fail
        mock_regex_class.side_effect = Exception("Failed to create RegexDetector")

        composite = CompositePIIDetector(
            regex_detector=None,  # Simulate failed initialization
            ministral_detector=mock_ministral_detector,
            enable_regex=True,
            enable_presidio=False,
        )

        # Should fall back to Ministral only
        assert composite.enable_regex is False
        assert composite.ministral_detector is mock_ministral_detector


class TestCompositePIIDetectorIntegration:
    """Integration tests for CompositePIIDetector."""

    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_DetectWithRealRegex_When_IntegrationTest(self, mock_presidio_class):
        """Should detect PII using real regex detector."""
        from pii_detector.infrastructure.detector.regex_detector import RegexDetector

        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio

        # Create real regex detector
        regex_detector = RegexDetector()

        # Mock Ministral detector
        mock_ministral_detector = Mock()
        mock_ministral_detector.model_id = "mock-ministral"
        mock_ministral_detector.download_model = Mock()
        mock_ministral_detector.load_model = Mock()
        mock_ministral_detector.detect_pii = Mock(return_value=[])

        # Create composite
        composite = CompositePIIDetector(
            regex_detector=regex_detector,
            ministral_detector=mock_ministral_detector,
            enable_ministral=True,
        )

        text = "Contact: test@example.com, AWS key AKIAIOSFODNN7EXAMPLE"
        entities = composite.detect_pii(text)

        # Should detect the AWS key with the real regex detector
        pii_types = {e.pii_type for e in entities}
        assert "API_KEY" in pii_types


class TestLocalDetectorHealth:
    """Readiness of in-process detectors reported before a scan starts.

    A detector reported ready but unable to run contributes zero findings, which
    reads exactly like clean content — so readiness must reflect more than the
    detector object existing.
    """

    def test_Should_ReportUnreachable_When_DetectorNotInstantiated(self):
        composite = CompositePIIDetector(
            regex_detector=None, enable_regex=False,
            presidio_detector=None, enable_presidio=False,
        )
        composite.enable_presidio = True

        health = composite.check_detectors_health(
            enable_regex=False, enable_presidio=True, enable_ministral=False
        )

        assert len(health) == 1
        assert health[0]["reachable"] is False
        assert "not instantiated" in health[0]["error"]

    def test_Should_ReportUnreachable_When_LazyModelsFailToLoad(self):
        # Presidio builds its AnalyzerEngine on first use: an engine that cannot be
        # built would pass a presence-only check, then find nothing.
        presidio = Mock()
        presidio.check_health = Mock(return_value=("", DetectorFailure(
            code=DetectorFailureCode.INIT_FAILED,
            message="Presidio analyzer could not be initialized",
        )))
        composite = CompositePIIDetector(
            regex_detector=Mock(), enable_regex=False,
            presidio_detector=presidio, enable_presidio=True,
        )

        health = composite.check_detectors_health(
            enable_regex=False, enable_presidio=True, enable_ministral=False
        )

        assert health[0]["reachable"] is False
        assert health[0]["error"] == "Presidio analyzer could not be initialized"
        assert health[0]["error_code"] == DetectorFailureCode.INIT_FAILED.value
        presidio.check_health.assert_called_once()

    def test_Should_ReportReachable_When_LazyModelsLoad(self):
        presidio = Mock()
        presidio.check_health = Mock(return_value=("", None))
        composite = CompositePIIDetector(
            regex_detector=Mock(), enable_regex=False,
            presidio_detector=presidio, enable_presidio=True,
        )

        health = composite.check_detectors_health(
            enable_regex=False, enable_presidio=True, enable_ministral=False
        )

        assert health[0]["reachable"] is True
        assert health[0]["error"] == ""

    def test_Should_ReportReachable_When_DetectorOffersNoProbe(self):
        # Regex has nothing to load: absent a probe, being instantiated is readiness.
        regex = Mock(spec=["model_id", "download_model", "load_model", "detect_pii"])
        composite = CompositePIIDetector(
            regex_detector=regex, enable_regex=True,
            presidio_detector=None, enable_presidio=False,
        )

        health = composite.check_detectors_health(
            enable_regex=True, enable_presidio=False, enable_ministral=False
        )

        assert health[0]["reachable"] is True
        assert health[0]["error"] == ""

    def test_Should_ReportUnreachable_When_PresidioDisabledInModelConfig(self):
        # The database flag and the TOML model config are independent switches: a Presidio
        # enabled in the database but disabled in its own config finds nothing, silently.
        from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector

        presidio = Mock(spec=PresidioDetector)
        presidio.check_health = Mock(return_value=("", DetectorFailure(
            code=DetectorFailureCode.DISABLED_IN_CONFIG,
            message="Presidio detector is disabled in its model configuration",
        )))
        composite = CompositePIIDetector(
            regex_detector=Mock(), enable_regex=False,
            presidio_detector=presidio, enable_presidio=True,
        )

        health = composite.check_detectors_health(
            enable_regex=False, enable_presidio=True, enable_ministral=False
        )

        assert health[0]["reachable"] is False
        assert health[0]["error_code"] == DetectorFailureCode.DISABLED_IN_CONFIG.value

"""
Unit tests for CompositePIIDetector.

Tests the hybrid detection approach combining ML and regex detectors.
"""

import pytest
from unittest.mock import Mock, MagicMock, patch

from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
from pii_detector.domain.entity.pii_entity import PIIEntity


class TestCompositePIIDetector:
    """Test CompositePIIDetector class."""
    
    @pytest.fixture
    def mock_ml_detector(self):
        """Create mock ML detector."""
        detector = Mock()
        detector.model_id = "mock-ml-detector"
        detector.download_model = Mock()
        detector.load_model = Mock()
        detector.detect_pii = Mock(return_value=[])
        detector.mask_pii = Mock(return_value=("", []))
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
    
    def test_Should_InitializeComposite_When_BothDetectorsProvided(
        self, mock_ml_detector, mock_regex_detector
    ):
        """Should initialize with both ML and regex detectors."""
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        assert composite.ml_detector is mock_ml_detector
        assert composite.regex_detector is mock_regex_detector
        assert composite.enable_regex is True
    
    def test_Should_InitializeMLOnly_When_RegexDisabled(self, mock_ml_detector):
        """Should initialize with ML detector only when regex disabled."""
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            enable_regex=False
        )
        
        assert composite.ml_detector is mock_ml_detector
        assert composite.enable_regex is False
    
    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_CallBothDetectors_When_BothEnabled(
        self, mock_presidio_class, mock_ml_detector, mock_regex_detector
    ):
        """Should call both ML and regex detectors."""
        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio
        
        # Setup ML detector to return one entity
        ml_entity = PIIEntity(
            text="John Doe",
            pii_type="GIVENNAME",
            type_label="GIVENNAME",
            start=0,
            end=8,
            score=0.85
        )
        mock_ml_detector.detect_pii.return_value = [ml_entity]
        
        # Setup regex detector to return one entity
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
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        text = "John Doe, email: test@example.com"
        entities = composite.detect_pii(text)
        
        # Both detectors should be called
        mock_ml_detector.detect_pii.assert_called_once_with(text, None)
        mock_regex_detector.detect_pii.assert_called_once_with(text, None)
        
        # Should have entities from both detectors
        assert len(entities) >= 1
    
    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_HandleMLFailure_When_ExceptionThrown(
        self, mock_presidio_class, mock_ml_detector, mock_regex_detector
    ):
        """Should continue with regex if ML detector fails."""
        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio
        
        # ML detector throws exception
        mock_ml_detector.detect_pii.side_effect = Exception("ML failed")
        
        # Regex detector returns entity
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
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        text = "test@example.com"
        entities = composite.detect_pii(text)
        
        # Should still return regex results
        assert len(entities) >= 1
        assert any(e.pii_type == "EMAIL" for e in entities)
    
    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_HandleRegexFailure_When_ExceptionThrown(
        self, mock_presidio_class, mock_ml_detector, mock_regex_detector
    ):
        """Should continue with ML if regex detector fails."""
        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio
        
        # Regex detector throws exception
        mock_regex_detector.detect_pii.side_effect = Exception("Regex failed")
        
        # ML detector returns entity
        ml_entity = PIIEntity(
            text="John Doe",
            pii_type="GIVENNAME",
            type_label="GIVENNAME",
            start=0,
            end=8,
            score=0.85
        )
        mock_ml_detector.detect_pii.return_value = [ml_entity]
        
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        text = "John Doe"
        entities = composite.detect_pii(text)
        
        # Should still return ML results
        assert len(entities) >= 1
        assert any(e.pii_type == "GIVENNAME" for e in entities)
    
    def test_Should_ReturnEmpty_When_EmptyText(
        self, mock_ml_detector, mock_regex_detector
    ):
        """Should return empty list for empty text."""
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        entities = composite.detect_pii("")
        
        assert entities == []
        # Detectors should not be called
        mock_ml_detector.detect_pii.assert_not_called()
        mock_regex_detector.detect_pii.assert_not_called()
    
    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_PassThreshold_When_Provided(
        self, mock_presidio_class, mock_ml_detector, mock_regex_detector
    ):
        """Should pass threshold to both detectors."""
        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio
        
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        text = "test text"
        threshold = 0.8
        composite.detect_pii(text, threshold=threshold)
        
        mock_ml_detector.detect_pii.assert_called_once_with(text, threshold)
        mock_regex_detector.detect_pii.assert_called_once_with(text, threshold)
    
    @patch('pii_detector.application.orchestration.composite_detector.PresidioDetector')
    def test_Should_MaskPII_When_DetectedEntities(
        self, mock_presidio_class, mock_ml_detector, mock_regex_detector
    ):
        """Should mask detected PII from both detectors."""
        # Mock presidio detector
        mock_presidio = Mock()
        mock_presidio.detect_pii.return_value = []
        mock_presidio_class.return_value = mock_presidio
        
        # Setup entities from both detectors
        ml_entity = PIIEntity(
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
        
        mock_ml_detector.detect_pii.return_value = [ml_entity]
        mock_regex_detector.detect_pii.return_value = [regex_entity]
        
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        text = "John, email: test@example.com"
        masked_text, entities = composite.mask_pii(text)
        
        # Both types should be masked
        assert "[GIVENNAME]" in masked_text
        assert "[EMAIL]" in masked_text
        assert "John" not in masked_text
        assert "test@example.com" not in masked_text
    
    def test_Should_DownloadModels_When_Called(
        self, mock_ml_detector, mock_regex_detector
    ):
        """Should call download_model on all detectors."""
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        composite.download_model()
        
        mock_ml_detector.download_model.assert_called_once()
        mock_regex_detector.download_model.assert_called_once()
    
    def test_Should_LoadModels_When_Called(
        self, mock_ml_detector, mock_regex_detector
    ):
        """Should call load_model on all detectors."""
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=mock_regex_detector
        )
        
        composite.load_model()
        
        mock_ml_detector.load_model.assert_called_once()
        mock_regex_detector.load_model.assert_called_once()
    
    def test_Should_ReturnCompositeModelId_When_MLEnabled(self, mock_ml_detector):
        """Should return composite model ID based on ML detector."""
        composite = CompositePIIDetector(ml_detector=mock_ml_detector)
        
        assert composite.model_id == f"composite-{mock_ml_detector.model_id}"
    
    def test_Should_ReturnRegexOnlyId_When_NoMLDetector(self, mock_regex_detector):
        """Should return regex-only ID when no ML detector."""
        composite = CompositePIIDetector(
            ml_detector=None,
            regex_detector=mock_regex_detector
        )
        
        assert composite.model_id == "composite-regex-only"
    
    @patch('pii_detector.application.orchestration.composite_detector.RegexDetector')
    def test_Should_ContinueWithoutRegex_When_InitializationFails(
        self, mock_regex_class, mock_ml_detector
    ):
        """Should continue without regex if initialization fails."""
        # This tests the fallback behavior when regex detector can't be created
        # Make RegexDetector creation fail
        mock_regex_class.side_effect = Exception("Failed to create RegexDetector")
        
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=None,  # Simulate failed initialization
            enable_regex=True
        )
        
        # Should fall back to ML only
        assert composite.enable_regex is False
        assert composite.ml_detector is mock_ml_detector


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
        
        # Mock ML detector
        mock_ml_detector = Mock()
        mock_ml_detector.model_id = "mock-ml"
        mock_ml_detector.download_model = Mock()
        mock_ml_detector.load_model = Mock()
        mock_ml_detector.detect_pii = Mock(return_value=[])
        
        # Create composite
        composite = CompositePIIDetector(
            ml_detector=mock_ml_detector,
            regex_detector=regex_detector
        )
        
        text = "Contact: test@example.com, IP: 192.168.1.1"
        entities = composite.detect_pii(text)
        
        # Should detect email and IP with regex
        pii_types = {e.pii_type for e in entities}
        assert "EMAIL" in pii_types
        assert "IP_ADDRESS" in pii_types

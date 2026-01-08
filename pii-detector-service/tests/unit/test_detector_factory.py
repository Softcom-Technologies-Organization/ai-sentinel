"""
Test suite for DetectorFactory class.

This module contains comprehensive tests for the DetectorFactory class,
covering registration, creation, and factory patterns.
"""

import pytest
from unittest.mock import Mock, patch

from pii_detector.application.factory.detector_factory import (
    DetectorFactory,
    create_default_factory
)
from pii_detector.application.config.detection_policy import DetectionConfig


class TestDetectorFactoryInitialization:
    """Test cases for DetectorFactory initialization."""
    
    def test_should_initialize_with_empty_registry(self):
        """Test initialization creates empty registry."""
        factory = DetectorFactory()
        
        assert factory.get_registered_types() == []
    
    def test_should_initialize_with_logger(self):
        """Test initialization creates logger."""
        factory = DetectorFactory()
        
        assert factory._logger is not None


class TestDetectorRegistration:
    """Test cases for detector registration."""
    
    def test_should_register_detector_type(self):
        """Test registering a new detector type."""
        factory = DetectorFactory()
        mock_builder = Mock()
        
        factory.register("test-detector", mock_builder)
        
        assert factory.is_registered("test-detector")
        assert "test-detector" in factory.get_registered_types()
    
    def test_should_raise_error_when_registering_duplicate(self):
        """Test error when registering duplicate detector type."""
        factory = DetectorFactory()
        mock_builder = Mock()
        
        factory.register("test-detector", mock_builder)
        
        with pytest.raises(ValueError, match="already registered"):
            factory.register("test-detector", mock_builder)
    
    def test_should_check_if_type_is_registered(self):
        """Test checking if a type is registered."""
        factory = DetectorFactory()
        mock_builder = Mock()
        
        assert not factory.is_registered("test-detector")
        
        factory.register("test-detector", mock_builder)
        
        assert factory.is_registered("test-detector")
    
    def test_should_get_all_registered_types(self):
        """Test getting list of all registered types."""
        factory = DetectorFactory()
        
        factory.register("type1", Mock())
        factory.register("type2", Mock())
        factory.register("type3", Mock())
        
        types = factory.get_registered_types()
        
        assert len(types) == 3
        assert "type1" in types
        assert "type2" in types
        assert "type3" in types


class TestDetectorCreation:
    """Test cases for detector creation."""
    
    def test_should_create_detector_for_gliner_model(self):
        """Test creating detector for GLiNER model."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        
        factory.register("gliner", mock_builder)
        
        result = factory.create("gliner-pii")
        
        assert result == mock_detector
        mock_builder.assert_called_once()
    
    def test_should_create_detector_for_default_model(self):
        """Test creating detector for default model."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        
        factory.register("default", mock_builder)
        
        result = factory.create("piiranha-v1")
        
        assert result == mock_detector
        mock_builder.assert_called_once()
    
    def test_should_pass_config_to_builder(self):
        """Test passing configuration to builder."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        config = DetectionConfig(model_id="test-model")
        
        factory.register("default", mock_builder)
        
        factory.create("test-model", config=config)
        
        mock_builder.assert_called_once()
        call_kwargs = mock_builder.call_args[1]
        assert call_kwargs["config"] == config
    
    def test_should_create_config_when_not_provided(self):
        """Test creating default config when not provided."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        
        factory.register("default", mock_builder)
        
        factory.create("test-model")
        
        mock_builder.assert_called_once()
        call_kwargs = mock_builder.call_args[1]
        assert isinstance(call_kwargs["config"], DetectionConfig)
        assert call_kwargs["config"].model_id == "test-model"
    
    def test_should_raise_error_for_unregistered_type(self):
        """Test error when creating unregistered detector type."""
        factory = DetectorFactory()
        
        with pytest.raises(ValueError, match="No detector registered"):
            factory.create("unknown-model")
    
    def test_should_pass_additional_kwargs_to_builder(self):
        """Test passing additional kwargs to builder."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        
        factory.register("default", mock_builder)
        
        factory.create("test-model", custom_param="value")
        
        call_kwargs = mock_builder.call_args[1]
        assert call_kwargs["custom_param"] == "value"


class TestDetectorTypeDetermination:
    """Test cases for detector type determination."""
    
    def test_should_detect_gliner_type_lowercase(self):
        """Test detecting GLiNER type with lowercase."""
        factory = DetectorFactory()
        
        detector_type = factory._determine_detector_type("gliner-pii")
        
        assert detector_type == "gliner"
    
    def test_should_detect_gliner_type_uppercase(self):
        """Test detecting GLiNER type with uppercase."""
        factory = DetectorFactory()
        
        detector_type = factory._determine_detector_type("GLiNER-model")
        
        assert detector_type == "gliner"
    
    def test_should_detect_gliner_type_mixed_case(self):
        """Test detecting GLiNER type with mixed case."""
        factory = DetectorFactory()
        
        detector_type = factory._determine_detector_type("GliNER-pii-v2")
        
        assert detector_type == "gliner"
    
    def test_should_default_to_default_type(self):
        """Test defaulting to default type."""
        factory = DetectorFactory()
        
        detector_type = factory._determine_detector_type("piiranha-v1")
        
        assert detector_type == "default"
    
    def test_should_default_for_unknown_model(self):
        """Test defaulting for unknown model."""
        factory = DetectorFactory()
        
        detector_type = factory._determine_detector_type("some-other-model")
        
        assert detector_type == "default"


class TestDetectorUnregistration:
    """Test cases for detector unregistration."""
    
    def test_should_unregister_detector_type(self):
        """Test unregistering a detector type."""
        factory = DetectorFactory()
        mock_builder = Mock()
        
        factory.register("test-detector", mock_builder)
        assert factory.is_registered("test-detector")
        
        factory.unregister("test-detector")
        
        assert not factory.is_registered("test-detector")
        assert "test-detector" not in factory.get_registered_types()
    
    def test_should_raise_error_when_unregistering_nonexistent(self):
        """Test error when unregistering non-existent type."""
        factory = DetectorFactory()
        
        with pytest.raises(ValueError, match="is not registered"):
            factory.unregister("nonexistent-type")


class TestDefaultFactory:
    """Test cases for default factory creation."""
    
    def test_should_create_factory_with_default_types(self):
        """Test creating factory with default detector types."""
        factory = create_default_factory()
        
        assert factory.is_registered("gliner")
        assert factory.is_registered("multipass-gliner")
        assert factory.is_registered("regex")
        assert factory.is_registered("default")
        assert len(factory.get_registered_types()) == 4
    
    def test_should_create_gliner_detector_from_default_factory(self):
        """Test creating GLiNER detector from default factory."""
        factory = create_default_factory()
        
        # Should create detector without error
        result = factory.create("gliner-pii")
        
        assert result is not None
        assert hasattr(result, 'detect_pii')
        assert hasattr(result, 'mask_pii')
        assert hasattr(result, 'model_id')
    
    @patch('pii_detector.infrastructure.detector.pii_detector.PIIDetector')
    def test_should_create_pii_detector_from_default_factory(self, mock_pii_detector):
        """Test creating PII detector from default factory."""
        mock_instance = Mock()
        mock_instance.detect_pii = Mock()
        mock_instance.mask_pii = Mock()
        mock_instance.model_id = "piiranha-v1"
        mock_pii_detector.return_value = mock_instance
        
        factory = create_default_factory()
        result = factory.create("piiranha-v1")
        
        assert result is not None
        assert hasattr(result, 'detect_pii')
        assert hasattr(result, 'mask_pii')
        assert hasattr(result, 'model_id')
        mock_pii_detector.assert_called_once()


class TestEdgeCases:
    """Test cases for edge cases and error handling."""
    
    def test_should_handle_empty_model_id(self):
        """Test handling empty model ID."""
        factory = DetectorFactory()
        factory.register("default", Mock(return_value=Mock()))
        
        # Should default to "default" type
        result = factory.create("")
        
        assert result is not None
    
    def test_should_handle_none_in_builder_kwargs(self):
        """Test handling None in builder kwargs."""
        factory = DetectorFactory()
        mock_detector = Mock()
        mock_builder = Mock(return_value=mock_detector)
        
        factory.register("default", mock_builder)
        
        result = factory.create("test-model", custom_param=None)
        
        assert result == mock_detector
    
    def test_should_handle_multiple_creates_same_type(self):
        """Test multiple creates of same type."""
        factory = DetectorFactory()
        mock_builder = Mock(side_effect=[Mock(), Mock(), Mock()])
        
        factory.register("default", mock_builder)
        
        result1 = factory.create("model1")
        result2 = factory.create("model2")
        result3 = factory.create("model3")
        
        assert result1 != result2
        assert result2 != result3
        assert mock_builder.call_count == 3


class TestIntegration:
    """Integration tests for DetectorFactory."""
    
    @patch('pii_detector.infrastructure.detector.pii_detector.PIIDetector')
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERDetector')
    def test_should_complete_full_workflow(self, mock_gliner, mock_pii):
        """Test complete workflow from registration to creation."""
        # Setup mocks
        gliner_instance = Mock()
        gliner_instance.detect_pii = Mock()
        gliner_instance.model_id = "gliner-pii"
        mock_gliner.return_value = gliner_instance
        
        pii_instance = Mock()
        pii_instance.detect_pii = Mock()
        pii_instance.model_id = "piiranha-v1"
        mock_pii.return_value = pii_instance
        
        # Create factory with defaults
        factory = create_default_factory()
        
        # Create different detector types
        gliner_result = factory.create("gliner-pii")
        pii_result = factory.create("piiranha-v1")
        
        # Verify different instances created
        assert gliner_result is not None
        assert pii_result is not None
        assert gliner_result != pii_result
        
        # Verify both conform to protocol
        assert hasattr(gliner_result, 'detect_pii')
        assert hasattr(pii_result, 'detect_pii')
        
        # Verify correct builders were called
        mock_gliner.assert_called_once()
        mock_pii.assert_called_once()
    
    def test_should_support_custom_detector_types(self):
        """Test adding custom detector types."""
        factory = DetectorFactory()
        
        # Custom detector builder
        custom_detector = Mock()
        custom_builder = Mock(return_value=custom_detector)
        
        # Register custom type
        factory.register("custom", custom_builder)
        
        # Mock _determine_detector_type to return "custom"
        factory._determine_detector_type = Mock(return_value="custom")
        
        # Create custom detector
        result = factory.create("custom-model")
        
        assert result == custom_detector
        custom_builder.assert_called_once()

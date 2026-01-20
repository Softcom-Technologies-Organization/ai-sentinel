"""
Unit tests for GLiNER detector stale config fix.

Tests that GLiNER detector correctly uses fresh configs passed at request time
instead of relying on stale cached configs from initialization.

This test suite verifies the fix for the bug where database configuration changes
were not reflected in real-time because configs were cached at initialization.
"""

from unittest.mock import Mock

from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector


class TestGlinerStaleConfig:
    """Test suite for GLiNER detector hot-reload configuration."""
    
    def test_Should_UseRequestTimeConfigs_When_PassedToDetectPii(self, mocker):
        """
        Test that GLiNER detector uses configs passed at request time
        instead of cached initialization configs.
        
        Scenario:
        1. Detector initialized with EMAIL disabled in DB cache
        2. Request comes with EMAIL enabled in fresh configs
        3. Detector should use fresh configs, not cached ones
        
        Business rule: Each gRPC request must use fresh database config.
        """
        # Arrange: Mock database adapter
        mock_db_adapter = Mock()
        
        # Cached configs at initialization: EMAIL disabled
        cached_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'email'
            },
            'PERSONNAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'person name'
            }
        }
        
        # Fresh configs at request time: EMAIL enabled
        fresh_configs = {
            'EMAIL': {
                'enabled': True,  # Now enabled!
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'email'
            },
            'PERSONNAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'person name'
            }
        }
        
        mock_db_adapter.fetch_pii_type_configs.return_value = cached_configs
        
        # Mock get_database_config_adapter at source module (local import in method)
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector (loads model)
        detector = GLiNERDetector()
        
        # Mock model and semantic chunker to avoid actual ML processing
        mock_model = Mock()
        mock_model.data_processor.config.tokenizer = Mock()
        mock_model.predict_entities.return_value = [
            {
                'label': 'email',
                'start': 0,
                'end': 14,  # Corrected: "test@email.com" is 14 chars (0-13)
                'score': 0.9,
                'text': 'test@email.com'
            }
        ]
        detector.model = mock_model
        
        # Mock semantic chunker
        mock_chunk_result = Mock()
        mock_chunk_result.text = "test@email.com"
        mock_chunk_result.start = 0
        mock_chunker = Mock()
        mock_chunker.chunk_text.return_value = [mock_chunk_result]
        detector.semantic_chunker = mock_chunker
        
        # Act: Call detect_pii with fresh configs (EMAIL now enabled)
        text = "test@email.com"
        entities = detector.detect_pii(text, threshold=0.5, pii_type_configs=fresh_configs)
        
        # Assert: EMAIL should be detected using fresh config
        assert len(entities) == 1, "EMAIL entity should be detected when fresh config has it enabled"
        assert entities[0].pii_type == 'EMAIL'
        assert entities[0].text == "test@email.com"
        
        # Assert: Model was called with 'email' label (from fresh config)
        call_args = mock_model.predict_entities.call_args
        labels_used = call_args[0][1]  # Second positional argument is labels
        assert 'email' in labels_used, "email label should be in detection labels from fresh config"
    
    def test_Should_FetchFreshConfigs_When_NoConfigsPassedToDetectPii(self, mocker):
        """
        Test that when no configs passed, detector fetches fresh from database.
        
        This ensures no stale cache - always uses fresh DB data on each request.
        
        Business rule: Each gRPC request without configs must fetch fresh from DB.
        """
        # Arrange
        mock_db_adapter = Mock()
        
        db_configs = {
            'PERSONNAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'person name'
            }
        }
        
        mock_db_adapter.fetch_pii_type_configs.return_value = db_configs
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = GLiNERDetector()
        
        # Mock model and semantic chunker
        mock_model = Mock()
        mock_model.data_processor.config.tokenizer = Mock()
        mock_model.predict_entities.return_value = [
            {
                'label': 'person name',
                'start': 0,
                'end': 10,
                'score': 0.9,
                'text': 'John Smith'
            }
        ]
        detector.model = mock_model
        
        mock_chunk_result = Mock()
        mock_chunk_result.text = "John Smith"
        mock_chunk_result.start = 0
        mock_chunker = Mock()
        mock_chunker.chunk_text.return_value = [mock_chunk_result]
        detector.semantic_chunker = mock_chunker
        
        # Act: Call detect_pii WITHOUT passing pii_type_configs (None/default)
        text = "John Smith"
        entities = detector.detect_pii(text, threshold=0.5)
        
        # Assert: Should have fetched fresh configs from DB
        assert mock_db_adapter.fetch_pii_type_configs.call_count >= 1, (
            "Should fetch fresh configs from database when none passed"
        )
        
        # Assert: Entity detected using fresh DB config
        assert len(entities) == 1
        assert entities[0].pii_type == 'PERSONNAME'
        assert entities[0].text == "John Smith"
    
    def test_Should_UseFreshConfigsOnEachCall_When_ConfigChanges(self, mocker):
        """
        Test that multiple calls use fresh configs each time (no caching).
        
        Scenario:
        1. First call: EMAIL disabled, PERSONNAME enabled in config
        2. Second call: EMAIL enabled, PERSONNAME enabled in config (simulating DB update)
        3. Both calls should use their respective fresh configs
        
        This is the core bug fix test: proves no config caching between requests.
        """
        # Arrange
        mock_db_adapter = Mock()
        
        # First call: EMAIL disabled, PERSONNAME enabled (realistic scenario)
        first_call_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'email'
            },
            'PERSONNAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'person name'
            }
        }
        
        # Second call: EMAIL enabled, PERSONNAME enabled (simulating DB update)
        second_call_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'email'
            },
            'PERSONNAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'person name'
            }
        }
        
        # Configure mock to return different configs on subsequent calls
        mock_db_adapter.fetch_pii_type_configs.side_effect = [
            first_call_configs,
            second_call_configs
        ]
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = GLiNERDetector()
        
        # Mock model and semantic chunker
        mock_model = Mock()
        mock_model.data_processor.config.tokenizer = Mock()
        mock_model.predict_entities.return_value = [
            {
                'label': 'email',
                'start': 0,
                'end': 14,  # Corrected: "test@email.com" is 14 chars (0-13)
                'score': 0.9,
                'text': 'test@email.com'
            }
        ]
        detector.model = mock_model
        
        mock_chunk_result = Mock()
        mock_chunk_result.text = "test@email.com"
        mock_chunk_result.start = 0
        mock_chunker = Mock()
        mock_chunker.chunk_text.return_value = [mock_chunk_result]
        detector.semantic_chunker = mock_chunker
        
        # Act: First call (EMAIL disabled)
        text = "test@email.com"
        entities_first = detector.detect_pii(text, threshold=0.5)
        
        # Assert: First call should not detect EMAIL (disabled in config)
        # The email label won't be in the labels list, so post-filtering removes it
        # OR it won't be passed to the model at all
        first_call_labels = mock_model.predict_entities.call_args_list[0][0][1]
        assert 'email' not in first_call_labels, (
            "email label should not be used when disabled in config"
        )
        
        # Act: Second call (EMAIL enabled - simulating config change)
        entities_second = detector.detect_pii(text, threshold=0.5)
        
        # Assert: Second call should detect EMAIL (enabled in fresh config)
        second_call_labels = mock_model.predict_entities.call_args_list[1][0][1]
        assert 'email' in second_call_labels, (
            "email label should be used when enabled in fresh config"
        )
        assert len(entities_second) == 1
        assert entities_second[0].pii_type == 'EMAIL'
        
        # Assert: Database was queried twice (once per call, no caching)
        assert mock_db_adapter.fetch_pii_type_configs.call_count == 2, (
            "Should fetch fresh configs on each call, proving no caching"
        )
    
    def test_Should_ApplyFreshScoringThresholds_When_ConfigChanges(self, mocker):
        """
        Test that scoring thresholds are applied from fresh configs.
        
        Scenario:
        1. First call: EMAIL threshold = 0.9 (high)
        2. Second call: EMAIL threshold = 0.3 (low)
        3. Entity with score 0.5 should be filtered in first call, kept in second
        
        This tests the post-filtering logic with fresh scoring overrides.
        """
        # Arrange
        mock_db_adapter = Mock()
        
        # First call: High threshold (0.9) - will filter out entity with score 0.5
        first_call_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.9,  # High threshold
                'detector': 'GLINER',
                'detector_label': 'email'
            }
        }
        
        # Second call: Low threshold (0.3) - will keep entity with score 0.5
        second_call_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,  # Low threshold
                'detector': 'GLINER',
                'detector_label': 'email'
            }
        }
        
        mock_db_adapter.fetch_pii_type_configs.side_effect = [
            first_call_configs,
            second_call_configs
        ]
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = GLiNERDetector()
        
        # Mock model to return entity with score 0.5
        mock_model = Mock()
        mock_model.data_processor.config.tokenizer = Mock()
        mock_model.predict_entities.return_value = [
            {
                'label': 'email',
                'start': 0,
                'end': 14,  # Corrected: "test@email.com" is 14 chars (0-13)
                'score': 0.5,  # Mid-range score
                'text': 'test@email.com'
            }
        ]
        detector.model = mock_model
        
        mock_chunk_result = Mock()
        mock_chunk_result.text = "test@email.com"
        mock_chunk_result.start = 0
        mock_chunker = Mock()
        mock_chunker.chunk_text.return_value = [mock_chunk_result]
        detector.semantic_chunker = mock_chunker
        
        # Act: First call with high threshold (0.9)
        text = "test@email.com"
        entities_first = detector.detect_pii(text, threshold=0.1, pii_type_configs=first_call_configs)
        
        # Assert: Entity filtered out (score 0.5 < threshold 0.9)
        assert len(entities_first) == 0, (
            "Entity with score 0.5 should be filtered when threshold is 0.9"
        )
        
        # Act: Second call with low threshold (0.3)
        entities_second = detector.detect_pii(text, threshold=0.1, pii_type_configs=second_call_configs)
        
        # Assert: Entity kept (score 0.5 >= threshold 0.3)
        assert len(entities_second) == 1, (
            "Entity with score 0.5 should pass when threshold is 0.3"
        )
        assert entities_second[0].pii_type == 'EMAIL'
        assert entities_second[0].score == 0.5
    
    def test_Should_HandleEmptyFreshConfigs_When_PassedToDetectPii(self, mocker):
        """
        Test that passing empty configs uses default mapping as fallback.
        
        Business rule: If fresh configs are empty, fall back to default mapping.
        """
        # Arrange
        mock_db_adapter = Mock()
        mock_db_adapter.fetch_pii_type_configs.return_value = {}
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = GLiNERDetector()
        
        # Mock model and semantic chunker
        mock_model = Mock()
        mock_model.data_processor.config.tokenizer = Mock()
        mock_model.predict_entities.return_value = []
        detector.model = mock_model
        
        mock_chunk_result = Mock()
        mock_chunk_result.text = "test@email.com"
        mock_chunk_result.start = 0
        mock_chunker = Mock()
        mock_chunker.chunk_text.return_value = [mock_chunk_result]
        detector.semantic_chunker = mock_chunker
        
        # Act: Pass empty dict as fresh configs
        text = "test@email.com"
        entities = detector.detect_pii(text, threshold=0.5, pii_type_configs={})
        
        # Assert: Should handle gracefully (use defaults)
        assert isinstance(entities, list)
        
        # Assert: Model was called with default labels
        call_args = mock_model.predict_entities.call_args
        labels_used = call_args[0][1]
        assert len(labels_used) > 0, "Should use default mapping when configs are empty"
    
    def test_Should_NotCacheConfigs_When_DetectorInitialized(self, mocker):
        """
        Test that detector does not cache configs at initialization.
        
        This verifies the architectural fix: no instance variables for configs.
        """
        # Arrange
        mock_db_adapter = Mock()
        mock_db_adapter.fetch_pii_type_configs.return_value = {}
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Act: Initialize detector
        detector = GLiNERDetector()
        
        # Assert: No cached config instance variables should exist
        assert not hasattr(detector, 'pii_type_mapping'), (
            "Detector should not have cached pii_type_mapping instance variable"
        )
        assert not hasattr(detector, 'scoring_overrides'), (
            "Detector should not have cached scoring_overrides instance variable"
        )
        assert not hasattr(detector, '_cached_pii_type_configs'), (
            "Detector should not have any cached config instance variables"
        )

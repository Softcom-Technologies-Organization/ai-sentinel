"""
GLiNER model management for PII detection.

This module provides the GLiNERModelManager class that handles downloading
and loading of GLiNER models from Hugging Face.
"""

import logging
from typing import Any

from application.config.detection_policy import DetectionConfig
from domain.exception.exceptions import ModelLoadError


class GLiNERModelManager:
    """Handles GLiNER model downloading and loading operations."""

    def __init__(self, config: DetectionConfig):
        """
        Initialize GLiNER model manager.
        
        Args:
            config: Detection configuration for the model
        """
        self.config = config
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

    def download_model(self) -> None:
        """
        Download the GLiNER model files from Hugging Face.
        
        GLiNER models are automatically downloaded by the library when
        using from_pretrained(), so this is a no-op for compatibility
        with the PIIDetector interface.
        """
        self.logger.info(f"GLiNER model {self.config.model_id} will be downloaded on first load")

    def load_model(self) -> Any:
        """
        Load the GLiNER model with optimizations.
        
        Returns:
            Loaded GLiNER model instance
            
        Raises:
            ModelLoadError: If model loading fails
        """
        self.logger.info(f"Loading GLiNER model: {self.config.model_id}")

        try:
            from gliner import GLiNER
            
            model = GLiNER.from_pretrained(self.config.model_id)
            
            self.logger.info("GLiNER model loaded successfully")
            return model
            
        except ImportError as e:
            self.logger.error("GLiNER library not installed")
            raise ModelLoadError(
                "GLiNER library not installed. Install with: pip install gliner"
            ) from e
        except Exception as e:
            self.logger.error(f"Error loading GLiNER model: {str(e)}")
            raise ModelLoadError(f"Failed to load GLiNER model: {str(e)}") from e

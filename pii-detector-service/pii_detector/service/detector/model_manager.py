"""
Model management for PII detection.

This module provides the ModelManager class that handles downloading
and loading of machine learning models from Hugging Face, including
tokenizer and model initialization with memory optimizations.
"""

import logging
from typing import Tuple

import torch
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer, AutoModelForTokenClassification

from .models.detection_config import DetectionConfig
from .models.exceptions import APIKeyError, ModelLoadError


class ModelManager:
    """Handles model downloading and loading operations."""

    def __init__(self, config: DetectionConfig):
        self.config = config
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

    def download_model(self) -> None:
        """Download the model files from Hugging Face."""
        api_key = self._get_api_key()
        filenames = ["config.json", "model.safetensors", "tokenizer.json", "tokenizer_config.json"]

        self.logger.info("Downloading model files from Hugging Face...")

        for filename in filenames:
            try:
                hf_hub_download(
                    repo_id=self.config.model_id,
                    filename=filename,
                    token=api_key
                )
                self.logger.debug(f"Downloaded {filename}")
            except Exception as e:
                self.logger.error(f"Error downloading {filename}: {str(e)}")
                raise e  # Re-raise the original exception to match test expectations

        self.logger.info("Model download completed successfully")

    def load_model_components(self) -> Tuple[AutoTokenizer, AutoModelForTokenClassification]:
        """Load tokenizer and model with optimizations."""
        self.logger.info("Loading model components...")

        try:
            tokenizer = self._load_tokenizer()
            model = self._load_model()
            return tokenizer, model
        except Exception as e:
            self.logger.error(f"Error loading model components: {str(e)}")
            raise ModelLoadError("Failed to load model components") from e

    def _get_api_key(self) -> str:
        """Get Hugging Face API key from centralized configuration."""
        from config import get_config
        
        try:
            config = get_config()
            api_key = config.model.huggingface_api_key
        except ValueError:
            # Config validation failed
            api_key = None
        
        if not api_key:
            raise APIKeyError("HUGGING_FACE_API_KEY environment variable must be set")
        return api_key

    def _load_tokenizer(self) -> AutoTokenizer:
        """Load tokenizer with optimizations."""
        return AutoTokenizer.from_pretrained(
            self.config.model_id,
            legacy=False,
            model_max_length=self.config.max_length,
            padding=True,
            truncation=True
        )

    def _load_model(self) -> AutoModelForTokenClassification:
        """Load model with memory optimizations."""
        device = self.config.device or ('cuda' if torch.cuda.is_available() else 'cpu')

        # Try with low_cpu_mem_usage first (requires accelerate), fallback without if needed
        try:
            model = AutoModelForTokenClassification.from_pretrained(
                self.config.model_id,
                torch_dtype=torch.float16 if device == 'cuda' else torch.float32,
                low_cpu_mem_usage=True
            )
        except (ImportError, NameError) as e:
            # Fallback without low_cpu_mem_usage if accelerate not available
            self.logger.warning(f"Loading model without low_cpu_mem_usage (accelerate may not be installed): {e}")
            model = AutoModelForTokenClassification.from_pretrained(
                self.config.model_id,
                torch_dtype=torch.float16 if device == 'cuda' else torch.float32,
            )

        model = model.to(device)
        model.eval()

        # Disable gradient computation for inference
        for param in model.parameters():
            param.requires_grad = False

        return model

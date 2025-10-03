"""
Configuration for machine learning model settings.

This module centralizes model-related configuration.
Only includes actually used environment variables.
"""

import os
from dataclasses import dataclass


@dataclass
class ModelConfig:
    """
    Configuration for ML model authentication.
    
    Attributes:
        huggingface_api_key: API key for HuggingFace Hub authentication
    """
    
    huggingface_api_key: str
    
    @classmethod
    def from_env(cls) -> "ModelConfig":
        """
        Load model configuration from environment variables.
        
        Returns:
            ModelConfig instance populated from environment
            
        Environment Variables:
            HUGGING_FACE_API_KEY: HuggingFace API key (required)
        """
        return cls(
            huggingface_api_key=os.getenv("HUGGING_FACE_API_KEY", ""),
        )
    
    def validate(self) -> None:
        """
        Validate model configuration values.
        
        Raises:
            ValueError: If configuration values are invalid
        """
        if not self.huggingface_api_key:
            raise ValueError(
                "HUGGING_FACE_API_KEY is required for model authentication"
            )

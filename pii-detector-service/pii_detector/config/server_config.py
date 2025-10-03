"""
Configuration for gRPC server settings.

This module centralizes server-related configuration.
Only includes actually used environment variables.
"""

import os
from dataclasses import dataclass


@dataclass
class ServerConfig:
    """
    Configuration for gRPC server.
    
    Attributes:
        enable_reflection: Enable gRPC server reflection for debugging
    """
    
    enable_reflection: bool
    
    @classmethod
    def from_env(cls) -> "ServerConfig":
        """
        Load server configuration from environment variables.
        
        Returns:
            ServerConfig instance populated from environment
            
        Environment Variables:
            ENABLE_REFLECTION: Enable reflection (default: true)
        """
        return cls(
            enable_reflection=os.getenv("ENABLE_REFLECTION", "1").lower() not in {
                "0", "false", "no"
            },
        )
    
    def validate(self) -> None:
        """
        Validate server configuration values.
        
        Raises:
            ValueError: If configuration values are invalid
        """
        # No validation needed for boolean flag
        pass

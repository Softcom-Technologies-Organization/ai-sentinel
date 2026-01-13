from enum import Enum

class DetectorSource(Enum):
    """Source of the detected PII entity."""
    UNKNOWN_SOURCE = "UNKNOWN_SOURCE"
    GLINER = "GLINER"
    PRESIDIO = "PRESIDIO"
    REGEX = "REGEX"
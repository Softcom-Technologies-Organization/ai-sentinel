from enum import Enum

class DetectorSource(Enum):
    """Source of the detected PII entity."""
    UNKNOWN_SOURCE = "UNKNOWN_SOURCE"
    PRESIDIO = "PRESIDIO"
    REGEX = "REGEX"
    # Not a PII detector: the deterministic format pre-filter, surfaced as a
    # pseudo detector in run-stats so the number of PII it discarded can be
    # measured. Never labels a real entity's source.
    POSTFILTER = "POSTFILTER"
    # Specialised LLM PII detector (Ministral-PII).
    MINISTRAL = "MINISTRAL"
    BARDS = "BARDS"
    CBX = "CBX"
    ETTIN = "ETTIN"

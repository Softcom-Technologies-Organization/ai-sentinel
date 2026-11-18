from unittest.mock import patch

from pii_detector.application.orchestration.composite_detector import CompositePIIDetector
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.server import main


def fixture_candidates(self, text, threshold, **kwargs):
    candidates = (
        ("IP_ADDRESS", "0.244.999.7"),
        ("MAC_ADDRESS", "13:56:49-13:56:52"),
        ("IBAN_CODE", "CH00 0000 0000 0000 0000 0"),
        ("IP_ADDRESS", "10.217.4.11"),
        ("IBAN_CODE", "CH9300762011623852957"),
    )
    entities = [
        PIIEntity(
            text=value, pii_type=pii_type, type_label=pii_type,
            start=text.index(value), end=text.index(value) + len(value),
            score=1.0, source=DetectorSource.PRESIDIO,
        )
        for pii_type, value in candidates
    ]
    return entities, []


with patch.object(CompositePIIDetector, "detect_pii_with_stats", fixture_candidates):
    main()

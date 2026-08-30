"""Why an enabled detector cannot run, in a form the dashboard can translate."""

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict


class DetectorFailureCode(str, Enum):
    """Machine-readable reasons an enabled detector is unusable.

    One code per situation the operator has a different action for: install the
    detector, start the endpoint, pull the model, load it.
    """

    NOT_INSTANTIATED = "DETECTOR_NOT_INSTANTIATED"
    DISABLED_IN_CONFIG = "DETECTOR_DISABLED_IN_CONFIG"
    INIT_FAILED = "DETECTOR_INIT_FAILED"
    ENDPOINT_UNREACHABLE = "ENDPOINT_UNREACHABLE"
    MODEL_NOT_AVAILABLE = "MODEL_NOT_AVAILABLE"
    MODEL_NOT_LOADED = "MODEL_NOT_LOADED"


@dataclass(frozen=True)
class DetectorFailure:
    """A detector failure as a code plus the values that describe it.

    The dashboard turns ``code`` into a sentence in the operator's language, so
    no English wording built here is ever read by a human. ``params`` carries the
    technical values that sentence interpolates — a model id, an endpoint state —
    which stay untranslated. ``message`` is kept for this service's own logs.
    """

    code: DetectorFailureCode
    params: Dict[str, str] = field(default_factory=dict)
    message: str = ""

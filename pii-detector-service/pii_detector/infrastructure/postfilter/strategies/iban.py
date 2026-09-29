"""IBAN pre-filter strategy (compact shape + mod-97 via python-stdnum).

Rejects a value only when it has the canonical compact IBAN shape
(``^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$`` after stripping spaces) AND
:func:`stdnum.iban.is_valid` reports it invalid (country length + mod-97).
Any non-canonical / truncated form fails open, and any exception keeps the
entity (fail-open absolute).

``iban.is_valid`` also runs python-stdnum's domestic account check where one
exists (BE, ES, ME, NO), like the Presidio IBAN recognizer; Swiss IBANs have
no domestic check. Measured on the corpus:
0 false positives caught (the IBAN FPs are base64 blobs out of shape ->
fail-open), 0 collisions; the value is prod protection against any
well-formed but mod-97-invalid IBAN.
"""

import re

from stdnum import iban as _iban
from stdnum.exceptions import ValidationError

from pii_detector.infrastructure.postfilter.postfilter_strategy import (
    PASS,
    PostfilterVerdict,
)

_IBAN_SHAPE = re.compile(r"^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$")


class IbanStrategy:
    """Reject canonical-shaped IBANs failing the mod-97 / length checksum."""

    pii_type = "IBAN"

    def evaluate(self, value: str) -> PostfilterVerdict:
        if not isinstance(value, str):
            return PASS
        s = value.strip().replace(" ", "").upper()
        if not _IBAN_SHAPE.match(s):  # non-canonical / truncated -> keep
            return PASS
        try:
            if _iban.is_valid(s):  # country length + mod-97
                return PASS
            return PostfilterVerdict(False, "iban mod-97 / length failed")
        except (ValidationError, Exception):  # fail-open absolute
            return PASS

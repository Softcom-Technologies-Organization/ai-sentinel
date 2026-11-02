"""Registry of pre-filter strategies, keyed by normalised ``pii_type``.

v1 GO strategies (IP_ADDRESS, MAC_ADDRESS, IBAN) plus the v2 PRUDENCE
strategies (CARD_NUMBER, AVS_NUMBER) and the v2b strategies (SwissUidStrategy
for TAX_ID/TAX_NUMBER, plus PAYMENT_CARD / NATIONAL_ID_NUMBER aliases) live
here. The remaining NO-GO types from ``data-analysis.md`` (CARD_CVV,
ROUTING_NUMBER, USERNAME, ...) are intentionally absent so an unmapped type
always falls open in :mod:`format_prefilter_validator`.
"""

from pii_detector.infrastructure.postfilter.strategies.avs_number import (
    AvsNumberStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.card_number import (
    CardNumberStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.iban import IbanStrategy
from pii_detector.infrastructure.postfilter.strategies.ip_address import (
    IpAddressStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.mac_address import (
    MacAddressStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.swift_bic import (
    SwiftBicStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.swiss_uid import (
    SwissUidStrategy,
)

_GO_STRATEGIES = (IpAddressStrategy(), MacAddressStrategy(), IbanStrategy())

# v2 (PRUDENCE, data-analysis.md): safe checksum strategies with 0 real FP gain
# on the reference corpus (CARD 3/21 FP, AVS 0/5 FP -- valid checksums, not real
# false positives), kept for prod protection. Never counted in the FP ROI.
_card_number = CardNumberStrategy()
_avs_number = AvsNumberStrategy()
_PRUDENCE_STRATEGIES = (_card_number, _avs_number)

# v2b (data-analysis.md): a new SwissUidStrategy for the Swiss business UID
# (TAX_ID / TAX_NUMBER) plus two pure aliases that route extra pii_types onto an
# existing instance. 0 real FP gain measured on the reference corpus (0/37 UID
# shapes matched; PAYMENT_CARD 0 corpus occurrences; the 2 NATIONAL_ID_NUMBER
# AVS-shaped values carry a valid key -> PASS, foreign ids out of template ->
# PASS). Kept for prod protection only, never counted in the FP ROI.
_swiss_uid = SwissUidStrategy()

# The registry key is NOT always ``strategy.pii_type``: alias keys
# (PAYMENT_CARD, NATIONAL_ID_NUMBER, TAX_NUMBER) deliberately point at an
# instance whose ``pii_type`` differs, so lookups must always be done by the
# registry key, never by ``strategy.pii_type``. Keyed on the normalised
# UPPER_SNAKE pii_type (same normalisation as the gRPC layer) so lookups match
# the strings flowing through the detection pipeline.
STRATEGIES = {s.pii_type: s for s in _GO_STRATEGIES + _PRUDENCE_STRATEGIES}
STRATEGIES["TAX_ID"] = _swiss_uid
# -- v2b aliases: same instance reused under a second registry key ----------
STRATEGIES["TAX_NUMBER"] = _swiss_uid  # Swiss UID under its other pii_type label
STRATEGIES["PAYMENT_CARD"] = _card_number  # alias of CARD_NUMBER (Luhn 13-19)
STRATEGIES["NATIONAL_ID_NUMBER"] = _avs_number  # alias of AVS_NUMBER (756/EAN-13)

# Tier C (fable5-postfilter-task.md): structural SWIFT/BIC validation, keyed
# under the label variants Ministral emits in passthrough. NIR (INSEE) and
# FR VAT/SIRET checksum validators were evaluated and deliberately dropped:
# the reference gold corpus carries synthetically-invalid keys, so they would
# reject true positives (recall rule wins over precision).
_swift_bic = SwiftBicStrategy()
STRATEGIES["SWIFT_BIC"] = _swift_bic
STRATEGIES["BIC"] = _swift_bic
STRATEGIES["SWIFT_CODE"] = _swift_bic
STRATEGIES["SWIFT"] = _swift_bic

# Label variants seen on the 2026-07 Confluence scan that missed their
# existing strategy (pure aliases, same instances).
STRATEGIES["IPV4"] = STRATEGIES["IP_ADDRESS"]
STRATEGIES["IPV6"] = STRATEGIES["IP_ADDRESS"]
STRATEGIES["CREDIT_CARD"] = _card_number
STRATEGIES["CREDIT_DEBIT_CARD"] = _card_number
STRATEGIES["IBAN_CODE"] = STRATEGIES["IBAN"]

<!-- bmad:context -->
<!-- Verified 2026-08-26 against 6e22133b (working tree had uncommitted changes). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## pii-detector-service

Python gRPC service that runs the detectors and returns findings. Entry point
`pii_detector/server.py`, detectors under `pii_detector/infrastructure/detector/`. The live
detectors are Presidio, Regex and Ministral; GLiNER, OpenMed and the LLM judge were removed and
their proto tags are reserved.

## Policy

- Do not enable the spaCy-backed Presidio entities (PERSON, LOCATION, ORG). `pyproject.toml`
  records that shipping spaCy would break Apache-2.0 compatibility. Cover those types with Regex or
  Ministral instead.

## Running and verifying

- `pii_detector/proto/generated/` is gitignored. Run `python pii_detector/proto/generate_pb.py`
  after a fresh clone, or every import of the generated stubs fails.
- `pytest` collects `tests/` including the integration suites; CI runs `tests/unit/` only. Coverage
  is always on through `addopts`.
- Ministral calls a remote OpenAI-compatible endpoint, LM Studio on `http://localhost:1234/v1` by
  default, overridden per request by the `lm_studio_host` / `lm_studio_port` database columns. With
  nothing listening there, only Presidio and Regex return findings.

## Conventions that differ from defaults

- Detector on/off flags are not read from `config/detection-settings.toml`; that file defers them
  to the database and says so.

## Known pitfalls

- Ministral emits labels outside its model card, and inconsistently between runs. Map the variants
  in `_MODEL_LABEL_ALIASES` (`ministral_detector.py`), never by changing `detector_label` in the
  database — that breaks the runs where the model emits the canonical form.

<!-- /bmad:context -->

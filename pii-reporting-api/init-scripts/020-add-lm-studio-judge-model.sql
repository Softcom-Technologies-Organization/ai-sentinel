-- The schema is primarily managed by Hibernate ddl-auto (this column is declared on
-- PiiDetectionConfigEntity). This script keeps Docker-bootstrapped environments consistent
-- and is safe to run multiple times.

ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS lm_studio_judge_model VARCHAR(255);

COMMENT ON COLUMN pii_detection_config.lm_studio_judge_model IS
    'LM Studio identifier of the model the LLM judge prompts. Kept apart from lm_studio_model because the judge accepts any chat model, while the detector is tied to the Ministral-PII tokenizer. NULL: reuse lm_studio_model.';

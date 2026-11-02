BEGIN;

UPDATE pii_detection_config
SET lm_studio_judge_model = 'qwen3.8-flash-next-iq3_s',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'release/v1.2.0-rc.2-strata-qwen'
WHERE id = 1;

COMMIT;

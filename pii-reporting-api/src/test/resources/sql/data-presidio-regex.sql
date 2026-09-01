-- ============================================================================
-- PRESIDIO + REGEX DATA SEED — for FormatPostfilterDiscardSmokeIT
-- ============================================================================
-- Minimal seed for the deterministic format-postfilter smoke test: only the
-- PRESIDIO and REGEX detector rows are needed (GLiNER/GLiNER2/OpenMed and the
-- LLM-judge have been removed from the product).
-- ============================================================================

BEGIN;

ALTER TABLE pii_type_config DROP CONSTRAINT IF EXISTS pii_type_config_pii_type_check;

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================
INSERT INTO pii_detection_config (id, presidio_enabled, regex_enabled, default_threshold, postfilter_enabled, updated_at, updated_by)
VALUES (1, true, true, 0.30, false, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- PRESIDIO PII TYPES
-- ============================================================================

-- Universal
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD',     'PRESIDIO', false, 0.90, 'Financial', 'CREDIT_CARD',     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN_CODE',       'PRESIDIO', true,  0.75, 'Financial', 'IBAN_CODE',       'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO',          'PRESIDIO', true,  0.80, 'Financial', 'CRYPTO',          'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS',     'PRESIDIO', true,  0.80, 'Network',   'MAC_ADDRESS',     'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_LICENSE', 'PRESIDIO', true,  0.90, 'Medical',   'MEDICAL_LICENSE', 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',             'PRESIDIO', true,  0.90, 'Personal',  'NRP',             'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Universal — disabled (commonly accepted PII or high FP)
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('EMAIL_ADDRESS', 'PRESIDIO', false, 0.70, 'Contact',  'EMAIL_ADDRESS', 'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER',  'PRESIDIO', false, 0.90, 'Contact',  'PHONE',         'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL',           'PRESIDIO', false, 0.70, 'Contact',  'URL',           'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IP_ADDRESS',    'PRESIDIO', false, 0.80, 'Network',  'IP_ADDRESS',    'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON',        'PRESIDIO', false, 0.90, 'Personal', 'PERSON',        'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATION',      'PRESIDIO', false, 0.75, 'Location', 'LOCATION',      'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME',     'PRESIDIO', false, 0.75, 'Personal', 'DATE',          'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE',           'PRESIDIO', false, 0.70, 'Personal', 'AGE',           'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Country-specific — ALL disabled (opt-in per tenant)
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('US_SSN',                    'PRESIDIO', false, 0.95, 'Government ID', 'US_SSN',                    'HIGH',   'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_BANK_NUMBER',            'PRESIDIO', false, 0.90, 'Financial',     'US_BANK_NUMBER',            'HIGH',   'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_DRIVER_LICENSE',         'PRESIDIO', false, 0.90, 'Government ID', 'US_DRIVER_LICENSE',         'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_ITIN',                   'PRESIDIO', false, 0.95, 'Government ID', 'US_ITIN',                   'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_PASSPORT',               'PRESIDIO', false, 0.95, 'Government ID', 'US_PASSPORT',               'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UK_NHS',                    'PRESIDIO', false, 0.95, 'Government ID', 'UK_NHS',                    'MEDIUM', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UK_NINO',                   'PRESIDIO', false, 0.95, 'Government ID', 'UK_NINO',                   'MEDIUM', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ES_NIF',                    'PRESIDIO', false, 0.90, 'Government ID', 'ES_NIF',                    'MEDIUM', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ES_NIE',                    'PRESIDIO', false, 0.90, 'Government ID', 'ES_NIE',                    'MEDIUM', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_FISCAL_CODE',            'PRESIDIO', false, 0.95, 'Government ID', 'IT_FISCAL_CODE',            'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_DRIVER_LICENSE',         'PRESIDIO', false, 0.90, 'Government ID', 'IT_DRIVER_LICENSE',         'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_VAT_CODE',               'PRESIDIO', false, 0.90, 'Financial',     'IT_VAT_CODE',               'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_PASSPORT',               'PRESIDIO', false, 0.95, 'Government ID', 'IT_PASSPORT',               'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_IDENTITY_CARD',          'PRESIDIO', false, 0.90, 'Government ID', 'IT_IDENTITY_CARD',          'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PL_PESEL',                  'PRESIDIO', false, 0.95, 'Government ID', 'PL_PESEL',                  'MEDIUM', 'PL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SG_NRIC_FIN',               'PRESIDIO', false, 0.95, 'Government ID', 'SG_NRIC_FIN',               'MEDIUM', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SG_UEN',                    'PRESIDIO', false, 0.90, 'Business',      'SG_UEN',                    'MEDIUM', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_ABN',                    'PRESIDIO', false, 0.90, 'Business',      'AU_ABN',                    'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_ACN',                    'PRESIDIO', false, 0.90, 'Business',      'AU_ACN',                    'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_TFN',                    'PRESIDIO', false, 0.95, 'Government ID', 'AU_TFN',                    'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_MEDICARE',               'PRESIDIO', false, 0.95, 'Medical',       'AU_MEDICARE',               'HIGH',   'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_PAN',                    'PRESIDIO', false, 0.90, 'Government ID', 'IN_PAN',                    'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_AADHAAR',                'PRESIDIO', false, 0.95, 'Government ID', 'IN_AADHAAR',                'HIGH',   'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VEHICLE_REGISTRATION',   'PRESIDIO', false, 0.85, 'Government ID', 'IN_VEHICLE_REGISTRATION',   'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VOTER',                  'PRESIDIO', false, 0.90, 'Government ID', 'IN_VOTER',                  'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_PASSPORT',               'PRESIDIO', false, 0.95, 'Government ID', 'IN_PASSPORT',               'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FI_PERSONAL_IDENTITY_CODE', 'PRESIDIO', false, 0.95, 'Government ID', 'FI_PERSONAL_IDENTITY_CODE', 'MEDIUM', 'FI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('KR_RRN',                    'PRESIDIO', false, 0.95, 'Government ID', 'KR_RRN',                    'MEDIUM', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TH_TNIN',                   'PRESIDIO', false, 0.95, 'Government ID', 'TH_TNIN',                   'MEDIUM', 'TH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- REGEX PII TYPES — all enabled (high precision)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'REGEX', true, 0.95, 'MEDICAL',        'avs number',             'HIGH', 'CH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIALNUM',  'REGEX', true, 0.75, 'IDENTITY',       'social security number', 'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY',    'REGEX', true, 0.95, 'IT_CREDENTIALS', 'api key',                'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

COMMIT;

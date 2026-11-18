BEGIN;

ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS judge_provider VARCHAR(16),
    ADD COLUMN IF NOT EXISTS judge_host VARCHAR(255),
    ADD COLUMN IF NOT EXISTS judge_port INTEGER;

ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS trio_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS bards_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS cbx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS ettin_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE pii_type_config DROP CONSTRAINT IF EXISTS check_detector;
ALTER TABLE pii_type_config
    ADD CONSTRAINT check_detector
    CHECK (detector IN ('PRESIDIO', 'REGEX', 'MINISTRAL', 'BARDS', 'CBX', 'ETTIN'));


-- ============================================================================
-- Drop legacy CHECK constraint on pii_type column if it exists.
-- This constraint blocks custom PII types created via zero-shot detection.
-- ============================================================================
ALTER TABLE pii_type_config DROP CONSTRAINT IF EXISTS pii_type_config_pii_type_check;

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- The concurrency columns are seeded neutral on purpose: a value measured on one
-- machine is not a sane default for another.
-- ============================================================================
INSERT INTO pii_detection_config (id, presidio_enabled, regex_enabled, default_threshold, postfilter_enabled, ministral_enabled, ministral_chunk_size, ministral_overlap, lm_studio_host, lm_studio_port, lm_studio_model, lm_studio_judge_model, ministral_concurrency, ministral_concurrency_auto, concurrency_bench_requested, concurrency_bench_status, concurrency_bench_progress, concurrency_bench_max_concurrency, updated_at, updated_by)
VALUES (1, true, true, 0.30, true, true, 2048, 410, 'localhost', 1234, 'ministral-3b-pii-preview@q8_0', 'qwen3.8-flash-next-iq3_s', 1, true, false, 'IDLE', 0, 4, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- DEFAULT CONFIGURATION POLICY
-- ============================================================================
--
-- ✅  ENABLED by default : high-risk identifiers (government IDs, financial,
--     medical, credentials, legal assets, account identifiers).
--
-- ⛔  DISABLED by default — "commonly accepted PII" that appears naturally in
--     professional documents and would generate excessive false positives if
--     flagged systematically.  This includes:
--       - Contact data   : email, phone, address, city, zip code
--       - Basic identity : person name, date of birth, gender, nationality, age
--       - Web artefacts  : URL, IP address
--       - Financials     : salary amount (contextually acceptable in HR docs)
--       - Country-specific Presidio types (US/UK/ES/IT/PL/SG/AU/IN/FI/KR/TH):
--         disabled until the tenant explicitly opts in for their jurisdiction.
--
-- Thresholds above 0.80 reflect higher-confidence requirements for types that
-- are prone to false positives on short tokens (vehicle IDs, login names, etc.).
-- ============================================================================

-- ============================================================================
-- PRESIDIO PII TYPES
-- ============================================================================
-- ✅ enabled  : CREDIT_CARD, IBAN_CODE, CRYPTO, MAC_ADDRESS, MEDICAL_LICENSE, NRP
-- ⛔ disabled : contact (email, phone, URL), generic personal (person, age, location,
--               date), IP address, and ALL country-specific types (opt-in per tenant)

-- Universal — enabled
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD',     'PRESIDIO', true,  0.90, 'Financial', 'CREDIT_CARD',     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN_CODE',       'PRESIDIO', true,  0.90, 'Financial', 'IBAN_CODE',       'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO',          'PRESIDIO', true,  0.90, 'Financial', 'CRYPTO',          'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS',     'PRESIDIO', true,  0.90, 'Network',   'MAC_ADDRESS',     'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_LICENSE', 'PRESIDIO', false, 0.90, 'Medical',   'MEDICAL_LICENSE', 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',             'PRESIDIO', true,  0.90, 'Personal',  'NRP',             'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Universal — disabled (commonly accepted PII or high false-positive rate)
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

-- Country-specific — ALL disabled by default (opt-in per tenant jurisdiction)

-- USA
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('US_SSN',            'PRESIDIO', false, 0.95, 'Government ID', 'US_SSN',            'HIGH',   'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_BANK_NUMBER',    'PRESIDIO', false, 0.90, 'Financial',     'US_BANK_NUMBER',    'HIGH',   'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_DRIVER_LICENSE', 'PRESIDIO', false, 0.90, 'Government ID', 'US_DRIVER_LICENSE', 'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_ITIN',           'PRESIDIO', false, 0.95, 'Government ID', 'US_ITIN',           'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_PASSPORT',       'PRESIDIO', false, 0.95, 'Government ID', 'US_PASSPORT',       'MEDIUM', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- UK
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('UK_NHS',  'PRESIDIO', false, 0.95, 'Government ID', 'UK_NHS',  'MEDIUM', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UK_NINO', 'PRESIDIO', false, 0.95, 'Government ID', 'UK_NINO', 'MEDIUM', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Spain
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('ES_NIF', 'PRESIDIO', false, 0.90, 'Government ID', 'ES_NIF', 'MEDIUM', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ES_NIE', 'PRESIDIO', false, 0.90, 'Government ID', 'ES_NIE', 'MEDIUM', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Italy
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('IT_FISCAL_CODE',    'PRESIDIO', false, 0.95, 'Government ID', 'IT_FISCAL_CODE',    'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_DRIVER_LICENSE', 'PRESIDIO', false, 0.90, 'Government ID', 'IT_DRIVER_LICENSE', 'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_VAT_CODE',       'PRESIDIO', false, 0.90, 'Financial',     'IT_VAT_CODE',       'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_PASSPORT',       'PRESIDIO', false, 0.95, 'Government ID', 'IT_PASSPORT',       'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_IDENTITY_CARD',  'PRESIDIO', false, 0.90, 'Government ID', 'IT_IDENTITY_CARD',  'MEDIUM', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Poland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('PL_PESEL', 'PRESIDIO', false, 0.95, 'Government ID', 'PL_PESEL', 'MEDIUM', 'PL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Singapore
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('SG_NRIC_FIN', 'PRESIDIO', false, 0.95, 'Government ID', 'SG_NRIC_FIN', 'MEDIUM', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SG_UEN',      'PRESIDIO', false, 0.90, 'Business',      'SG_UEN',      'MEDIUM', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Australia
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('AU_ABN',      'PRESIDIO', false, 0.90, 'Business',      'AU_ABN',      'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_ACN',      'PRESIDIO', false, 0.90, 'Business',      'AU_ACN',      'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_TFN',      'PRESIDIO', false, 0.95, 'Government ID', 'AU_TFN',      'MEDIUM', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_MEDICARE', 'PRESIDIO', false, 0.95, 'Medical',       'AU_MEDICARE', 'HIGH',   'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- India
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('IN_PAN',                  'PRESIDIO', false, 0.90, 'Government ID', 'IN_PAN',                  'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_AADHAAR',              'PRESIDIO', false, 0.95, 'Government ID', 'IN_AADHAAR',              'HIGH',   'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VEHICLE_REGISTRATION', 'PRESIDIO', false, 0.85, 'Government ID', 'IN_VEHICLE_REGISTRATION', 'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VOTER',                'PRESIDIO', false, 0.90, 'Government ID', 'IN_VOTER',                'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_PASSPORT',             'PRESIDIO', false, 0.95, 'Government ID', 'IN_PASSPORT',             'MEDIUM', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Finland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('FI_PERSONAL_IDENTITY_CODE', 'PRESIDIO', false, 0.95, 'Government ID', 'FI_PERSONAL_IDENTITY_CODE', 'MEDIUM', 'FI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Korea
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('KR_RRN', 'PRESIDIO', false, 0.95, 'Government ID', 'KR_RRN', 'MEDIUM', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Thailand
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('TH_TNIN', 'PRESIDIO', false, 0.95, 'Government ID', 'TH_TNIN', 'MEDIUM', 'TH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'REGEX', true, 0.95, 'SSN',            'avs number',             'HIGH', 'CH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIALNUM',  'REGEX', true, 0.75, 'IDENTITY',       'social security number', 'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY',    'REGEX', true, 0.95, 'IT_CREDENTIALS', 'api key',                'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

UPDATE pii_type_config SET category = 'SSN'
WHERE pii_type = 'AVS_NUMBER' AND detector = 'REGEX' AND category IS DISTINCT FROM 'SSN';

DELETE FROM pii_type_config
WHERE detector = 'REGEX' AND pii_type IN (
    'SSN_FR_NIR', 'SSN_BE_NISS', 'SSN_AT_VNR', 'SSN_BG_EGN',
    'SSN_CZ_RC', 'SSN_DK_CPR', 'SSN_EE_IK', 'SSN_FI_HETU',
    'SSN_GR_AMKA', 'SSN_HR_OIB', 'SSN_IE_PPS', 'SSN_IS_KENNITALA',
    'SSN_IT_CF', 'SSN_LT_ASMENS', 'SSN_NL_BSN', 'SSN_NO_FNR',
    'SSN_PL_PESEL', 'SSN_RO_CNP', 'SSN_SE_PERSONNUMMER', 'SSN_SI_EMSO',
    'SSN_SK_RC', 'SSN_GB_NINO', 'SSN_US_SSN', 'SSN_CA_SIN',
    'TAX_ID_CH_AVS', 'TAX_ID_CH_UID', 'TAX_ID_FR_NIF', 'TAX_ID_DE_IDNR',
    'TAX_ID_AT_TIN', 'TAX_ID_ES_NIF', 'TAX_ID_PT_NIF', 'TAX_ID_PL_NIP',
    'TAX_ID_GB_UTR', 'TAX_ID_BG_PNF', 'TAX_ID_LV_PERSONAS', 'TAX_ID_BY_UNP',
    'TAX_ID_MD_IDNO', 'TAX_ID_ME_PIB', 'TAX_ID_MK_EDB', 'TAX_ID_RS_PIB',
    'TAX_ID_UA_RNTRC', 'TAX_ID_BE_NISS', 'TAX_ID_BG_EGN', 'TAX_ID_CZ_RC',
    'TAX_ID_DK_CPR', 'TAX_ID_EE_IK', 'TAX_ID_FI_HETU', 'TAX_ID_HR_OIB',
    'TAX_ID_IE_PPS', 'TAX_ID_IS_KENNITALA', 'TAX_ID_IT_CF', 'TAX_ID_LT_ASMENS',
    'TAX_ID_NL_BSN', 'TAX_ID_NO_FNR', 'TAX_ID_PL_PESEL', 'TAX_ID_RO_CNP',
    'TAX_ID_SE_PERSONNUMMER', 'TAX_ID_SI_EMSO', 'TAX_ID_SK_RC', 'TAX_ID_US_SSN',
    'TAX_ID_CA_SIN', 'TAX_ID_AT_UID', 'TAX_ID_BE_VAT', 'TAX_ID_BG_VAT',
    'TAX_ID_CY_VAT', 'TAX_ID_CZ_DIC', 'TAX_ID_DE_VAT', 'TAX_ID_DK_CVR',
    'TAX_ID_EE_KMKR', 'TAX_ID_ES_VAT', 'TAX_ID_FI_ALV', 'TAX_ID_FR_VAT',
    'TAX_ID_GR_VAT', 'TAX_ID_HR_VAT', 'TAX_ID_HU_ANUM', 'TAX_ID_IE_VAT',
    'TAX_ID_IT_IVA', 'TAX_ID_LT_PVM', 'TAX_ID_LU_VAT', 'TAX_ID_LV_PVN',
    'TAX_ID_MT_VAT', 'TAX_ID_NL_BTW', 'TAX_ID_NO_MVA', 'TAX_ID_PL_VAT',
    'TAX_ID_PT_VAT', 'TAX_ID_RO_VAT', 'TAX_ID_SE_VAT', 'TAX_ID_SI_DDV',
    'TAX_ID_SK_DPH', 'TAX_ID_GB_VAT', 'TAX_ID_AL_NIPT', 'TAX_ID_US_EIN',
    'TAX_ID_US_ITIN', 'TAX_ID_US_PTIN', 'TAX_ID_CA_BN', 'TAX_ID_CN_RIC',
    'TAX_ID_CN_USCC', 'TAX_ID_IN_PAN', 'TAX_ID_IN_GSTIN', 'TAX_ID_RU_INN',
    'TAX_ID_JP_MY_NUMBER', 'TAX_ID_JP_CN', 'TAX_ID_BR_CPF', 'TAX_ID_BR_CNPJ',
    'TAX_ID_AU_TFN', 'TAX_ID_AU_ABN', 'TAX_ID_KR_BRN', 'TAX_ID_MX_RFC',
    'TAX_ID_ID_NPWP', 'TAX_ID_ID_NIK', 'TAX_ID_TR_TCKN', 'TAX_ID_TR_VKN',
    'TAX_ID_SG_UEN', 'TAX_ID_SG_NRIC_FIN', 'TAX_ID_HK_HKID', 'TAX_ID_HK_BR',
    'TAX_ID_PA_RUC_PERSON', 'TAX_ID_PA_RUC_ENTITY'
);

-- ============================================================================
-- Ministral-PII detector seed (73 entities, 8 categories).
-- Specialised LLM detector: enabled by default, threshold 0.50 neutral (no exploitable per-entity confidenceScore).
-- ✅ enabled  : GOV_ID (except passport_number, unique_id), FINANCIAL (except salary),
--               DIGITAL (except url, user_name) -- 26 types
-- ⛔ disabled : IDENTITY, CONTACT, MEDICAL, EMPLOYMENT, TEMPORAL -- per the policy above
-- ============================================================================

-- Category: IDENTITY (Identity & demographics) -- 17 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('FIRST_NAME',           'MINISTRAL', false, 0.50, 'IDENTITY', 'first_name',           'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LAST_NAME',            'MINISTRAL', false, 0.50, 'IDENTITY', 'last_name',            'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TITLE',                'MINISTRAL', false, 0.50, 'IDENTITY', 'title',                'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH',        'MINISTRAL', false, 0.50, 'IDENTITY', 'date_of_birth',        'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE',                  'MINISTRAL', false, 0.50, 'IDENTITY', 'age',                  'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER',               'MINISTRAL', false, 0.50, 'IDENTITY', 'gender',               'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONALITY',          'MINISTRAL', false, 0.50, 'IDENTITY', 'nationality',          'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RACE',                 'MINISTRAL', false, 0.50, 'IDENTITY', 'race',                 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ETHNICITY',            'MINISTRAL', false, 0.50, 'IDENTITY', 'ethnicity',            'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RACE_ETHNICITY',       'MINISTRAL', false, 0.50, 'IDENTITY', 'race_ethnicity',       'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RELIGION',             'MINISTRAL', false, 0.50, 'IDENTITY', 'religion',             'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RELIGIOUS_BELIEF',     'MINISTRAL', false, 0.50, 'IDENTITY', 'religious_belief',     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MARITAL_STATUS',       'MINISTRAL', false, 0.50, 'IDENTITY', 'marital_status',       'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SEXUALITY',            'MINISTRAL', false, 0.50, 'IDENTITY', 'sexuality',            'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POLITICAL_VIEW',       'MINISTRAL', false, 0.50, 'IDENTITY', 'political_view',       'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LANGUAGE',             'MINISTRAL', false, 0.50, 'IDENTITY', 'language',             'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIOMETRIC_IDENTIFIER', 'MINISTRAL', false, 0.50, 'IDENTITY', 'biometric_identifier', 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: CONTACT (Contact & address) -- 12 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('EMAIL',           'MINISTRAL', false, 0.50, 'CONTACT', 'email',           'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER',    'MINISTRAL', false, 0.50, 'CONTACT', 'phone_number',    'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FAX_NUMBER',      'MINISTRAL', false, 0.50, 'CONTACT', 'fax_number',      'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STREET_ADDRESS',  'MINISTRAL', false, 0.50, 'CONTACT', 'street_address',  'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BUILDING_NUMBER', 'MINISTRAL', false, 0.50, 'CONTACT', 'building_number', 'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CITY',            'MINISTRAL', false, 0.50, 'CONTACT', 'city',            'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTY',          'MINISTRAL', false, 0.50, 'CONTACT', 'county',          'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STATE',           'MINISTRAL', false, 0.50, 'CONTACT', 'state',           'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTCODE',        'MINISTRAL', false, 0.50, 'CONTACT', 'postcode',        'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ZIP_CODE',        'MINISTRAL', false, 0.50, 'CONTACT', 'zip_code',        'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTRY',         'MINISTRAL', false, 0.50, 'CONTACT', 'country',         'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COORDINATE',      'MINISTRAL', false, 0.50, 'CONTACT', 'coordinate',      'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: GOV_ID (Government & legal IDs) -- 9 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('SOCIAL_SECURITY_NUMBER',     'MINISTRAL', true, 0.50, 'GOV_ID', 'social_security_number',     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID',                'MINISTRAL', true, 0.50, 'GOV_ID', 'national_id',                'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE_NUMBER',      'MINISTRAL', true, 0.50, 'GOV_ID', 'driver_license_number',      'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID',                     'MINISTRAL', true, 0.50, 'GOV_ID', 'tax_id',                     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE',              'MINISTRAL', true, 0.50, 'GOV_ID', 'license_plate',              'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_IDENTIFIER',         'MINISTRAL', true, 0.50, 'GOV_ID', 'vehicle_identifier',         'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CERTIFICATE_LICENSE_NUMBER', 'MINISTRAL', true, 0.50, 'GOV_ID', 'certificate_license_number', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UNIQUE_ID',                  'MINISTRAL', false, 0.50, 'GOV_ID', 'unique_id',                 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT_NUMBER',            'MINISTRAL', false, 0.50, 'GOV_ID', 'passport_number',           'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Seeded twice for the same concept: 'SSN' duplicated 'SOCIAL_SECURITY_NUMBER'
-- in the operator list. The model's 'ssn' label is now aliased to
-- 'social_security_number' in the detector, so nothing is lost by dropping it.
DELETE FROM pii_type_config WHERE detector = 'MINISTRAL' AND pii_type = 'SSN';

-- Category: MEDICAL (Healthcare) -- 3 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('MEDICAL_RECORD_NUMBER',          'MINISTRAL', false, 0.50, 'MEDICAL', 'medical_record_number',          'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_PLAN_BENEFICIARY_NUMBER', 'MINISTRAL', false, 0.50, 'MEDICAL', 'health_plan_beneficiary_number', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BLOOD_TYPE',                     'MINISTRAL', false, 0.50, 'MEDICAL', 'blood_type',                     'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: FINANCIAL (Financial) -- 8 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_DEBIT_CARD',   'MINISTRAL', true, 0.50, 'FINANCIAL', 'credit_debit_card',   'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CVV',                 'MINISTRAL', true, 0.50, 'FINANCIAL', 'cvv',                 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PIN',                 'MINISTRAL', true, 0.50, 'FINANCIAL', 'pin',                 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_NUMBER',      'MINISTRAL', true, 0.50, 'FINANCIAL', 'account_number',      'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ROUTING_NUMBER', 'MINISTRAL', true, 0.50, 'FINANCIAL', 'bank_routing_number', 'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN',                'MINISTRAL', true, 0.50, 'FINANCIAL', 'iban',                'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SWIFT_BIC',           'MINISTRAL', true, 0.50, 'FINANCIAL', 'swift_bic',           'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SALARY',              'MINISTRAL', false, 0.50, 'FINANCIAL', 'salary',             'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: EMPLOYMENT (Employment & org) -- 7 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('OCCUPATION',        'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'occupation',        'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMPLOYMENT_STATUS', 'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'employment_status', 'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMPLOYEE_ID',       'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'employee_id',       'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EDUCATION_LEVEL',   'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'education_level',   'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ORGANIZATION',      'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'organization',      'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COMPANY_NAME',      'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'company_name',      'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CUSTOMER_ID',       'MINISTRAL', false, 0.50, 'EMPLOYMENT', 'customer_id',       'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: DIGITAL (Digital & network) -- 14 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS',        'MINISTRAL', true, 0.50, 'DIGITAL', 'ip_address',        'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IPV4',              'MINISTRAL', true, 0.50, 'DIGITAL', 'ipv4',              'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IPV6',              'MINISTRAL', true, 0.50, 'DIGITAL', 'ipv6',              'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS',       'MINISTRAL', true, 0.50, 'DIGITAL', 'mac_address',       'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL',               'MINISTRAL', false, 0.50, 'DIGITAL', 'url',              'LOW',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('USER_NAME',         'MINISTRAL', false, 0.50, 'DIGITAL', 'user_name',        'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD',          'MINISTRAL', true, 0.50, 'DIGITAL', 'password',          'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HTTP_COOKIE',       'MINISTRAL', true, 0.50, 'DIGITAL', 'http_cookie',       'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY',           'MINISTRAL', true, 0.50, 'DIGITAL', 'api_key',           'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_IDENTIFIER', 'MINISTRAL', true, 0.50, 'DIGITAL', 'device_identifier', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOFTWARE_LICENSE',  'MINISTRAL', true, 0.50, 'DIGITAL', 'software_license',  'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CLIENT_SECRET',     'MINISTRAL', true, 0.50, 'DIGITAL', 'client_secret',     'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GITHUB_TOKEN',      'MINISTRAL', true, 0.50, 'DIGITAL', 'github_token',      'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN',      'MINISTRAL', true, 0.50, 'DIGITAL', 'access_token',      'HIGH',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: TEMPORAL (Temporal) -- 3 entities
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('DATE',      'MINISTRAL', false, 0.50, 'TEMPORAL', 'date',      'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME', 'MINISTRAL', false, 0.50, 'TEMPORAL', 'date_time', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TIME',      'MINISTRAL', false, 0.50, 'TEMPORAL', 'time',      'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, created_at, updated_at, updated_by)
VALUES
    ('ACCOUNT_IDENTIFIER', 'BARDS', true, 0.35, 'DIGITAL', 'ACCOUNT_IDENTIFIER', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AUTH_SECRET', 'BARDS', true, 0.25, 'DIGITAL', 'AUTH_SECRET', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT_IDENTIFIER', 'BARDS', true, 0.0, 'FINANCIAL', 'BANK_ACCOUNT_IDENTIFIER', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIOMETRIC_DATA', 'BARDS', true, 0.0, 'IDENTITY', 'BIOMETRIC_DATA', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CONTACT_HANDLE', 'BARDS', true, 0.7, 'CONTACT', 'CONTACT_HANDLE', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRIMINAL_OFFENCE_DATA', 'BARDS', false, 0.0, 'GOV_ID', 'CRIMINAL_OFFENCE_DATA', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH', 'BARDS', true, 0.0, 'IDENTITY', 'DATE_OF_BIRTH', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME', 'BARDS', true, 0.45, 'TEMPORAL', 'DATE_TIME', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_IDENTIFIER', 'BARDS', true, 0.8, 'DIGITAL', 'DEVICE_IDENTIFIER', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DOCUMENT_IDENTIFIER', 'BARDS', true, 0.0, 'GOV_ID', 'DOCUMENT_IDENTIFIER', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DOCUMENT_REFERENCE', 'BARDS', false, 0.0, 'GOV_ID', 'DOCUMENT_REFERENCE', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMAIL', 'BARDS', true, 0.0, 'CONTACT', 'EMAIL_ADDRESS', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ETHNIC_ORIGIN', 'BARDS', true, 0.9, 'IDENTITY', 'ETHNIC_ORIGIN', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FINANCIAL_AMOUNT', 'BARDS', false, 0.0, 'FINANCIAL', 'FINANCIAL_AMOUNT', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GEO_LOCATION', 'BARDS', true, 0.55, 'CONTACT', 'GEO_LOCATION', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_DATA', 'BARDS', true, 0.0, 'MEDICAL', 'HEALTH_DATA', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IDENTIFYING_LINK', 'BARDS', true, 0.65, 'DIGITAL', 'IDENTIFYING_LINK', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IP_ADDRESS', 'BARDS', true, 0.65, 'DIGITAL', 'IP_ADDRESS', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATION', 'BARDS', true, 0.6, 'CONTACT', 'LOCATION', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ORGANIZATION_IDENTIFIER', 'BARDS', true, 0.85, 'EMPLOYMENT', 'ORGANIZATION_IDENTIFIER', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COMPANY_NAME', 'BARDS', true, 0.95, 'EMPLOYMENT', 'ORGANIZATION_NAME', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CREDIT_DEBIT_CARD', 'BARDS', true, 0.45, 'FINANCIAL', 'PAYMENT_CARD', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PAYMENT_CARD_SECURITY', 'BARDS', true, 0.45, 'FINANCIAL', 'PAYMENT_CARD_SECURITY', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON_ALIAS', 'BARDS', false, 0.0, 'IDENTITY', 'PERSON_ALIAS', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON_ATTRIBUTE', 'BARDS', true, 0.95, 'IDENTITY', 'PERSON_ATTRIBUTE', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON_IDENTIFIER', 'BARDS', true, 0.0, 'GOV_ID', 'PERSON_IDENTIFIER', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON_NAME', 'BARDS', true, 0.35, 'IDENTITY', 'PERSON_NAME', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSON_ROLE_OR_TITLE', 'BARDS', true, 0.65, 'IDENTITY', 'PERSON_ROLE_OR_TITLE', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'BARDS', true, 0.7, 'CONTACT', 'PHONE_NUMBER', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POLITICAL_OPINION', 'BARDS', true, 0.0, 'IDENTITY', 'POLITICAL_OPINION', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STREET_ADDRESS', 'BARDS', true, 0.0, 'CONTACT', 'POSTAL_ADDRESS', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PROPER_NAME', 'BARDS', true, 0.65, 'IDENTITY', 'PROPER_NAME', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RELIGION_OR_BELIEF', 'BARDS', true, 0.4, 'IDENTITY', 'RELIGION_OR_BELIEF', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SEXUAL_ORIENTATION', 'BARDS', true, 0.0, 'IDENTITY', 'SEXUAL_ORIENTATION', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TRADE_UNION_MEMBERSHIP', 'BARDS', false, 0.0, 'IDENTITY', 'TRADE_UNION_MEMBERSHIP', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'BARDS', true, 0.0, 'DIGITAL', 'URL', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_IDENTIFIER', 'BARDS', true, 0.5, 'GOV_ID', 'VEHICLE_IDENTIFIER', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'CBX', true, 0.7, 'DIGITAL', 'API_KEY', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'CBX', true, 0.9, 'DIGITAL', 'ACCESS_TOKEN', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('JWT_TOKEN', 'CBX', true, 0.0, 'DIGITAL', 'JWT_TOKEN', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SESSION_SECRET', 'CBX', true, 0.95, 'DIGITAL', 'SESSION_SECRET', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('WEBHOOK_SECRET', 'CBX', true, 0.95, 'DIGITAL', 'WEBHOOK_SECRET', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD', 'CBX', true, 0.95, 'DIGITAL', 'PASSWORD', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATABASE_CREDENTIAL', 'CBX', true, 0.95, 'DIGITAL', 'DATABASE_CREDENTIAL', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CLOUD_CREDENTIAL', 'CBX', true, 0.95, 'DIGITAL', 'CLOUD_CREDENTIAL', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PRIVATE_KEY', 'CBX', true, 0.95, 'DIGITAL', 'PRIVATE_KEY', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CERTIFICATE_SECRET', 'CBX', true, 0.95, 'DIGITAL', 'CERTIFICATE_SECRET', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ENCRYPTION_KEY', 'CBX', true, 0.95, 'DIGITAL', 'ENCRYPTION_KEY', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENERIC_SECRET', 'CBX', true, 0.9, 'DIGITAL', 'GENERIC_SECRET', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_NUMBER', 'ETTIN', true, 0.4, 'FINANCIAL', 'account_number', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'ETTIN', true, 0.45, 'IDENTITY', 'age', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'ETTIN', true, 0.3, 'DIGITAL', 'api_key', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ROUTING_NUMBER', 'ETTIN', true, 0.3, 'FINANCIAL', 'bank_routing_number', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIOMETRIC_IDENTIFIER', 'ETTIN', true, 0.3, 'IDENTITY', 'biometric_identifier', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BLOOD_TYPE', 'ETTIN', true, 0.35, 'MEDICAL', 'blood_type', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CERTIFICATE_LICENSE_NUMBER', 'ETTIN', true, 0.4, 'GOV_ID', 'certificate_license_number', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CITY', 'ETTIN', true, 0.3, 'CONTACT', 'city', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COMPANY_NAME', 'ETTIN', true, 0.35, 'EMPLOYMENT', 'company_name', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COORDINATE', 'ETTIN', true, 0.65, 'CONTACT', 'coordinate', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTRY', 'ETTIN', true, 0.4, 'CONTACT', 'country', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTY', 'ETTIN', true, 0.3, 'CONTACT', 'county', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CREDIT_DEBIT_CARD', 'ETTIN', true, 0.6, 'FINANCIAL', 'credit_debit_card', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CUSTOMER_ID', 'ETTIN', true, 0.45, 'EMPLOYMENT', 'customer_id', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CVV', 'ETTIN', true, 0.3, 'FINANCIAL', 'cvv', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE', 'ETTIN', true, 0.3, 'TEMPORAL', 'date', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH', 'ETTIN', true, 0.3, 'IDENTITY', 'date_of_birth', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME', 'ETTIN', true, 0.3, 'TEMPORAL', 'date_time', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_IDENTIFIER', 'ETTIN', true, 0.55, 'DIGITAL', 'device_identifier', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EDUCATION_LEVEL', 'ETTIN', true, 0.35, 'EMPLOYMENT', 'education_level', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMAIL', 'ETTIN', true, 0.7, 'CONTACT', 'email', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMPLOYEE_ID', 'ETTIN', true, 0.7, 'EMPLOYMENT', 'employee_id', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMPLOYMENT_STATUS', 'ETTIN', true, 0.5, 'EMPLOYMENT', 'employment_status', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FAX_NUMBER', 'ETTIN', true, 0.3, 'CONTACT', 'fax_number', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FIRST_NAME', 'ETTIN', true, 0.35, 'IDENTITY', 'first_name', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'ETTIN', true, 0.3, 'IDENTITY', 'gender', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_PLAN_BENEFICIARY_NUMBER', 'ETTIN', true, 0.3, 'MEDICAL', 'health_plan_beneficiary_number', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HTTP_COOKIE', 'ETTIN', true, 0.75, 'DIGITAL', 'http_cookie', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IPV4', 'ETTIN', true, 0.7, 'DIGITAL', 'ipv4', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IPV6', 'ETTIN', true, 0.65, 'DIGITAL', 'ipv6', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LANGUAGE', 'ETTIN', true, 0.6, 'IDENTITY', 'language', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LAST_NAME', 'ETTIN', true, 0.3, 'IDENTITY', 'last_name', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE', 'ETTIN', true, 0.3, 'GOV_ID', 'license_plate', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'ETTIN', true, 0.45, 'DIGITAL', 'mac_address', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_RECORD_NUMBER', 'ETTIN', true, 0.35, 'MEDICAL', 'medical_record_number', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'ETTIN', true, 0.3, 'GOV_ID', 'national_id', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('OCCUPATION', 'ETTIN', true, 0.4, 'EMPLOYMENT', 'occupation', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD', 'ETTIN', true, 0.3, 'DIGITAL', 'password', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'ETTIN', true, 0.3, 'CONTACT', 'phone_number', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PIN', 'ETTIN', true, 0.6, 'FINANCIAL', 'pin', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POLITICAL_VIEW', 'ETTIN', true, 0.3, 'IDENTITY', 'political_view', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTCODE', 'ETTIN', true, 0.6, 'CONTACT', 'postcode', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RACE_ETHNICITY', 'ETTIN', true, 0.75, 'IDENTITY', 'race_ethnicity', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('RELIGIOUS_BELIEF', 'ETTIN', true, 0.35, 'IDENTITY', 'religious_belief', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SEXUALITY', 'ETTIN', true, 0.3, 'IDENTITY', 'sexuality', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIAL_SECURITY_NUMBER', 'ETTIN', true, 0.3, 'GOV_ID', 'ssn', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STATE', 'ETTIN', true, 0.35, 'CONTACT', 'state', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STREET_ADDRESS', 'ETTIN', true, 0.35, 'CONTACT', 'street_address', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SWIFT_BIC', 'ETTIN', true, 0.6, 'FINANCIAL', 'swift_bic', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'ETTIN', true, 0.3, 'GOV_ID', 'tax_id', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TIME', 'ETTIN', true, 0.65, 'TEMPORAL', 'time', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UNIQUE_ID', 'ETTIN', true, 0.85, 'GOV_ID', 'unique_id', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'ETTIN', true, 0.65, 'DIGITAL', 'url', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('USER_NAME', 'ETTIN', true, 0.4, 'DIGITAL', 'user_name', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_IDENTIFIER', 'ETTIN', true, 0.4, 'GOV_ID', 'vehicle_identifier', 'MEDIUM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

UPDATE pii_detection_config
SET judge_enabled_categories = judge_enabled_categories - 'TAX_ID'
WHERE judge_enabled_categories ? 'TAX_ID'
  AND NOT EXISTS (SELECT 1 FROM pii_type_config WHERE REPLACE(UPPER(TRIM(category)), ' ', '_') = 'TAX_ID');

COMMIT;

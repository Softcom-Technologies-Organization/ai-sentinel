BEGIN;

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
INSERT INTO pii_detection_config (id, presidio_enabled, regex_enabled, default_threshold, postfilter_enabled, ministral_enabled, ministral_chunk_size, ministral_overlap, lm_studio_host, lm_studio_port, ministral_concurrency, ministral_concurrency_auto, concurrency_bench_requested, concurrency_bench_status, concurrency_bench_progress, updated_at, updated_by)
VALUES (1, true, true, 0.30, true, true, 2048, 410, 'localhost', 1234, 1, true, false, 'IDLE', 0, CURRENT_TIMESTAMP, 'system')
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

-- ============================================================================
-- REGEX PII TYPES — all enabled (high precision, low false-positive rate)
-- Scope limited to formats with no Presidio equivalent: national identifiers
-- (AVS/CH, SOCIALNUM for FR+BE) and credentials (API_KEY). IP_ADDRESS,
-- MAC_ADDRESS, CREDIT_CARD_NUMBER and PHONE_NUMBER are covered by Presidio and
-- deliberately not detected by REGEX to avoid cross-detector duplication.
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, severity, country_code, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'REGEX', true, 0.95, 'MEDICAL',        'avs number',             'HIGH', 'CH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIALNUM',  'REGEX', true, 0.75, 'IDENTITY',       'social security number', 'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY',    'REGEX', true, 0.95, 'IT_CREDENTIALS', 'api key',                'HIGH', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

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

COMMIT;

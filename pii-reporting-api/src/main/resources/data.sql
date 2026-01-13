BEGIN;

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================
INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, nb_of_label_by_pass, updated_at, updated_by)
VALUES (1, true, true, true, 0.30, 35, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- GLINER PII TYPES - INSERT ONLY
-- ============================================================================
-- Category 1: IDENTITY
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'GLINER', true, 0.80, 'IDENTITY', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'GLINER', true, 0.80, 'IDENTITY', 'national identity number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSN', 'GLINER', true, 0.80, 'IDENTITY', 'social security number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT_NUMBER', 'GLINER', true, 0.80, 'IDENTITY', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE_NUMBER', 'GLINER', true, 0.80, 'IDENTITY', 'driver license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH', 'GLINER', true, 0.80, 'IDENTITY', 'date of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.80, 'IDENTITY', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONALITY', 'GLINER', true, 0.80, 'IDENTITY', 'nationality', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.80, 'IDENTITY', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 2: CONTACT
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL', 'GLINER', true, 0.80, 'CONTACT', 'email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'GLINER', true, 0.80, 'CONTACT', 'phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ADDRESS', 'GLINER', true, 0.80, 'CONTACT', 'address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CITY', 'GLINER', true, 0.80, 'CONTACT', 'city', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ZIP_CODE', 'GLINER', true, 0.80, 'CONTACT', 'zip code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 3: DIGITAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('USERNAME', 'GLINER', true, 0.80, 'DIGITAL', 'username', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_ID', 'GLINER', true, 0.80, 'DIGITAL', 'account id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'GLINER', true, 0.80, 'DIGITAL', 'url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 4: FINANCIAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD_NUMBER', 'GLINER', true, 0.80, 'FINANCIAL', 'credit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT_NUMBER', 'GLINER', true, 0.80, 'FINANCIAL', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.80, 'FINANCIAL', 'iban', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIC_SWIFT', 'GLINER', true, 0.80, 'FINANCIAL', 'swift code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'GLINER', true, 0.80, 'FINANCIAL', 'tax identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SALARY', 'GLINER', true, 0.80, 'FINANCIAL', 'salary amount', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 5: MEDICAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'GLINER', true, 0.80, 'MEDICAL', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PATIENT_ID', 'GLINER', true, 0.80, 'MEDICAL', 'patient id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_RECORD_NUMBER', 'GLINER', true, 0.80, 'MEDICAL', 'medical record number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_INSURANCE_NUMBER', 'GLINER', true, 0.80, 'MEDICAL', 'health insurance number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DIAGNOSIS', 'GLINER', true, 0.80, 'MEDICAL', 'medical diagnosis', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICATION', 'GLINER', true, 0.80, 'MEDICAL', 'medication name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 6: IT_CREDENTIALS
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'ip address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'mac address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOSTNAME', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'hostname', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_ID', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'device id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'access token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRET_KEY', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'secret key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SESSION_ID', 'GLINER', true, 0.80, 'IT_CREDENTIALS', 'session id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 7: LEGAL_ASSET
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CASE_NUMBER', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'case number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_NUMBER', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRIMINAL_RECORD', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'criminal record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_REGISTRATION', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'vehicle registration number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'license plate number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VIN', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'vehicle identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INSURANCE_POLICY_NUMBER', 'GLINER', true, 0.80, 'LEGAL_ASSET', 'insurance policy number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PRESIDIO PII TYPES - INSERT ONLY (detector_label directement renseigné)
-- ============================================================================
-- Contact
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL_ADDRESS', 'PRESIDIO', true, 0.70, 'Contact', 'EMAIL_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER',  'PRESIDIO', true, 0.90, 'Contact', 'PHONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL',           'PRESIDIO', true, 0.70, 'Contact', 'URL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',           'PRESIDIO', true, 0.90, 'Personal', 'NRP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Financial
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD', 'PRESIDIO', true, 0.75, 'Financial', 'CREDIT_CARD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN_CODE',   'PRESIDIO', true, 0.75, 'Financial', 'IBAN_CODE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO',      'PRESIDIO', true, 0.80, 'Financial', 'CRYPTO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Network
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS',  'PRESIDIO', true, 0.80, 'Network', 'IP_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'PRESIDIO', true, 0.80, 'Network', 'MAC_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Personal
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON',    'PRESIDIO', true, 0.90, 'Personal', 'PERSON', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATION',  'PRESIDIO', true, 0.75, 'Location', 'LOCATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME', 'PRESIDIO', true, 0.75, 'Personal', 'DATE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE',       'PRESIDIO', true, 0.70, 'Personal', 'AGE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',       'PRESIDIO', false,0.70, 'Personal', 'NRP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Medical
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('MEDICAL_LICENSE', 'PRESIDIO', true, 0.90, 'Medical', 'MEDICAL_LICENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- USA
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('US_SSN',            'PRESIDIO', true, 0.95, 'Government ID', 'US_SSN', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_BANK_NUMBER',    'PRESIDIO', true, 0.90, 'Financial', 'US_BANK_NUMBER', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_DRIVER_LICENSE', 'PRESIDIO', true, 0.90, 'Government ID', 'US_DRIVER_LICENSE', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_ITIN',           'PRESIDIO', true, 0.95, 'Government ID', 'US_ITIN', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_PASSPORT',       'PRESIDIO', true, 0.95, 'Government ID', 'US_PASSPORT', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- UK
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('UK_NHS',  'PRESIDIO', false,0.95, 'Government ID', 'UK_NHS', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UK_NINO', 'PRESIDIO', false,0.95, 'Government ID', 'UK_NINO', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Spain
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('ES_NIF', 'PRESIDIO', true, 0.90, 'Government ID', 'ES_NIF', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ES_NIE', 'PRESIDIO', true, 0.90, 'Government ID', 'ES_NIE', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Italy
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('IT_FISCAL_CODE',  'PRESIDIO', true, 0.95, 'Government ID', 'IT_FISCAL_CODE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_DRIVER_LICENSE','PRESIDIO', true, 0.90, 'Government ID', 'IT_DRIVER_LICENSE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_VAT_CODE',     'PRESIDIO', true, 0.90, 'Financial', 'IT_VAT_CODE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_PASSPORT',     'PRESIDIO', true, 0.95, 'Government ID', 'IT_PASSPORT', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_IDENTITY_CARD','PRESIDIO', true, 0.90, 'Government ID', 'IT_IDENTITY_CARD', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Poland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('PL_PESEL', 'PRESIDIO', true, 0.95, 'Government ID', 'PL_PESEL', 'PL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Singapore
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('SG_NRIC_FIN', 'PRESIDIO', true, 0.95, 'Government ID', 'SG_NRIC_FIN', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SG_UEN',      'PRESIDIO', true, 0.90, 'Business', 'SG_UEN', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Australia
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('AU_ABN',      'PRESIDIO', true, 0.90, 'Business', 'AU_ABN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_ACN',      'PRESIDIO', true, 0.90, 'Business', 'AU_ACN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_TFN',      'PRESIDIO', true, 0.95, 'Government ID', 'AU_TFN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_MEDICARE', 'PRESIDIO', true, 0.95, 'Medical', 'AU_MEDICARE', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- India
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('IN_PAN',                  'PRESIDIO', true, 0.90, 'Government ID', 'IN_PAN', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_AADHAAR',              'PRESIDIO', true, 0.95, 'Government ID', 'IN_AADHAAR', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VEHICLE_REGISTRATION', 'PRESIDIO', true, 0.85, 'Government ID', 'IN_VEHICLE_REGISTRATION', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VOTER',                'PRESIDIO', true, 0.90, 'Government ID', 'IN_VOTER', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_PASSPORT',             'PRESIDIO', true, 0.95, 'Government ID', 'IN_PASSPORT', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Finland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('FI_PERSONAL_IDENTITY_CODE', 'PRESIDIO', true, 0.95, 'Government ID', 'FI_PERSONAL_IDENTITY_CODE', 'FI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Korea
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('KR_RRN', 'PRESIDIO', true, 0.95, 'Government ID', 'KR_RRN', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Thailand
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('TH_TNIN', 'PRESIDIO', true, 0.95, 'Government ID', 'TH_TNIN', 'TH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- REGEX PII TYPES - INSERT ONLY
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER',          'REGEX', true, 0.95, 'MEDICAL', 'avs number', 'CH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSN',           'REGEX', true, 0.75, 'IDENTITY', 'social number', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CREDIT_CARD_NUMBER',  'REGEX', true, 0.90, 'FINANCIAL', 'credit card number', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER',        'REGEX', true, 0.90, 'CONTACT', 'PHONE_NUMBER', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IP_ADDRESS',          'REGEX', true, 0.95, 'IT_CREDENTIALS', 'ip address', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS',         'REGEX', true, 0.95, 'IT_CREDENTIALS', 'mac address', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY',             'REGEX', true, 0.95, 'IT_CREDENTIALS', 'api key', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

COMMIT;
BEGIN;

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================
INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, true, true, 0.30, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- GLINER PII TYPES (44 types) - INSERT ONLY
-- ============================================================================
-- Category 1: IDENTITY
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'GLINER', true, 0.80, 'Person Name', 'Names of individuals (first, last, full)', 'IDENTITY', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'GLINER', true, 0.80, 'National ID', 'National identity card numbers', 'IDENTITY', 'national identity number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSN', 'GLINER', true, 0.80, 'Social Security Number', 'Social security numbers', 'IDENTITY', 'social security number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT_NUMBER', 'GLINER', true, 0.80, 'Passport Number', 'Passport identification numbers', 'IDENTITY', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE_NUMBER', 'GLINER', true, 0.80, 'Driver License', 'Driver license numbers', 'IDENTITY', 'driver license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH', 'GLINER', true, 0.80, 'Date of Birth', 'Birth dates', 'IDENTITY', 'date of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.80, 'Gender', 'Gender identifiers', 'IDENTITY', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONALITY', 'GLINER', true, 0.80, 'Nationality', 'Nationality or citizenship', 'IDENTITY', 'nationality', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.80, 'Age', 'Age values', 'IDENTITY', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 2: CONTACT
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL', 'GLINER', true, 0.80, 'Email Address', 'Email addresses', 'CONTACT', 'email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'GLINER', true, 0.80, 'Phone Number', 'Phone numbers (mobile, landline, fax)', 'CONTACT', 'phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ADDRESS', 'GLINER', true, 0.80, 'Address', 'Physical addresses (home, work, mailing)', 'CONTACT', 'address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTAL_CODE', 'GLINER', true, 0.80, 'Postal Code', 'ZIP codes and postal codes', 'CONTACT', 'postal code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 3: DIGITAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('USERNAME', 'GLINER', true, 0.80, 'Username', 'Usernames, logins, and online handles', 'DIGITAL', 'username', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_ID', 'GLINER', true, 0.80, 'Account ID', 'Account, user, and customer IDs', 'DIGITAL', 'account id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'GLINER', true, 0.80, 'URL', 'Web URLs and links', 'DIGITAL', 'url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 4: FINANCIAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD_NUMBER', 'GLINER', true, 0.80, 'Credit Card Number', 'Credit and debit card numbers', 'FINANCIAL', 'credit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT_NUMBER', 'GLINER', true, 0.80, 'Bank Account Number', 'Bank account numbers', 'FINANCIAL', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.80, 'IBAN', 'International Bank Account Numbers', 'FINANCIAL', 'iban', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIC_SWIFT', 'GLINER', true, 0.80, 'BIC/SWIFT Code', 'Bank Identifier Codes', 'FINANCIAL', 'swift code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'GLINER', true, 0.80, 'Tax ID', 'Tax identification numbers', 'FINANCIAL', 'tax identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SALARY', 'GLINER', true, 0.80, 'Salary', 'Salary and wage information', 'FINANCIAL', 'salary amount', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 5: MEDICAL
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'GLINER', true, 0.80, 'AVS Number', 'Swiss social security number (AVS/AHV)', 'MEDICAL', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PATIENT_ID', 'GLINER', true, 0.80, 'Patient ID', 'Patient identification numbers', 'MEDICAL', 'patient id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_RECORD_NUMBER', 'GLINER', true, 0.80, 'Medical Record Number', 'Medical record numbers', 'MEDICAL', 'medical record number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_INSURANCE_NUMBER', 'GLINER', true, 0.80, 'Health Insurance Number', 'Health insurance ID numbers', 'MEDICAL', 'health insurance number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DIAGNOSIS', 'GLINER', true, 0.80, 'Diagnosis', 'Medical diagnoses and conditions', 'MEDICAL', 'medical diagnosis', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICATION', 'GLINER', true, 0.80, 'Medication', 'Medications, prescriptions, and treatments', 'MEDICAL', 'medication name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 6: IT_CREDENTIALS
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS', 'GLINER', true, 0.80, 'IP Address', 'IPv4 and IPv6 addresses', 'IT_CREDENTIALS', 'ip address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'GLINER', true, 0.80, 'MAC Address', 'Network MAC addresses', 'IT_CREDENTIALS', 'mac address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOSTNAME', 'GLINER', true, 0.80, 'Hostname', 'Computer hostnames', 'IT_CREDENTIALS', 'hostname', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_ID', 'GLINER', true, 0.80, 'Device ID', 'Device identification numbers', 'IT_CREDENTIALS', 'device id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD', 'GLINER', true, 0.80, 'Password', 'Passwords and passphrases', 'IT_CREDENTIALS', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'GLINER', true, 0.80, 'API Key', 'API keys and access keys', 'IT_CREDENTIALS', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'GLINER', true, 0.80, 'Access Token', 'Access tokens (bearer, OAuth, refresh)', 'IT_CREDENTIALS', 'access token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRET_KEY', 'GLINER', true, 0.80, 'Secret Key', 'Secret keys (private, SSH, crypto)', 'IT_CREDENTIALS', 'secret key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SESSION_ID', 'GLINER', true, 0.80, 'Session ID', 'Session and cookie identifiers', 'IT_CREDENTIALS', 'session id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category 7: LEGAL_ASSET
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CASE_NUMBER', 'GLINER', true, 0.80, 'Case Number', 'Legal case numbers', 'LEGAL_ASSET', 'case number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_NUMBER', 'GLINER', true, 0.80, 'License Number', 'License and permit numbers', 'LEGAL_ASSET', 'license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRIMINAL_RECORD', 'GLINER', true, 0.80, 'Criminal Record', 'Criminal record information', 'LEGAL_ASSET', 'criminal record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_REGISTRATION', 'GLINER', true, 0.80, 'Vehicle Registration', 'Vehicle registration numbers', 'LEGAL_ASSET', 'vehicle registration number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE', 'GLINER', true, 0.80, 'License Plate', 'License plate numbers', 'LEGAL_ASSET', 'license plate number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VIN', 'GLINER', true, 0.80, 'VIN', 'Vehicle identification numbers', 'LEGAL_ASSET', 'vehicle identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INSURANCE_POLICY_NUMBER', 'GLINER', true, 0.80, 'Insurance Policy', 'Insurance policy numbers', 'LEGAL_ASSET', 'insurance policy number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PRESIDIO PII TYPES - INSERT ONLY (detector_label directement renseigné)
-- ============================================================================
-- Contact
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL_ADDRESS', 'PRESIDIO', true, 0.70, 'Email Address', 'Email addresses (Presidio)', 'Contact', 'EMAIL_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER',  'PRESIDIO', true, 0.75, 'Phone Number', 'Phone numbers (Presidio)', 'Contact', 'PHONE_NUMBER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL',           'PRESIDIO', true, 0.70, 'URL', 'Web URLs', 'Contact', 'URL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',           'PRESIDIO', true, 0.90, 'NRP', 'Nationality, Religious or Political group', 'Personal', 'NRP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Financial
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD', 'PRESIDIO', true, 0.75, 'Credit Card', 'Credit card numbers (Presidio)', 'Financial', 'CREDIT_CARD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN_CODE',   'PRESIDIO', true, 0.75, 'IBAN', 'International Bank Account Numbers', 'Financial', 'IBAN_CODE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO',      'PRESIDIO', true, 0.80, 'Crypto Wallet', 'Cryptocurrency wallet addresses', 'Financial', 'CRYPTO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Network
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS',  'PRESIDIO', true, 0.80, 'IP Address', 'IPv4 and IPv6 addresses', 'Network', 'IP_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'PRESIDIO', true, 0.80, 'MAC Address', 'MAC addresses', 'Network', 'MAC_ADDRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Personal
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON',    'PRESIDIO', true, 0.65, 'Person Name', 'Person names', 'Personal', 'PERSON', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATION',  'PRESIDIO', true, 0.75, 'Location', 'Geographic locations', 'Location', 'LOCATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_TIME', 'PRESIDIO', true, 0.75, 'Date/Time', 'Dates and times', 'Personal', 'DATE_TIME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE',       'PRESIDIO', true, 0.70, 'Age', 'Age values', 'Personal', 'AGE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRP',       'PRESIDIO', false,0.70, 'NRP', 'Nationality, Religious or Political group (disabled)', 'Personal', 'NRP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Medical
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('MEDICAL_LICENSE', 'PRESIDIO', true, 0.90, 'Medical License', 'Medical license numbers', 'Medical', 'MEDICAL_LICENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- USA
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('US_SSN',            'PRESIDIO', true, 0.95, 'US SSN', 'US Social Security Number', 'Government ID', 'US_SSN', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_BANK_NUMBER',    'PRESIDIO', true, 0.90, 'US Bank Account', 'US Bank Account Number', 'Financial', 'US_BANK_NUMBER', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_DRIVER_LICENSE', 'PRESIDIO', true, 0.90, 'US Driver License', 'US Driver License Number', 'Government ID', 'US_DRIVER_LICENSE', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_ITIN',           'PRESIDIO', true, 0.95, 'US ITIN', 'US Individual Taxpayer ID', 'Government ID', 'US_ITIN', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('US_PASSPORT',       'PRESIDIO', true, 0.95, 'US Passport', 'US Passport Number', 'Government ID', 'US_PASSPORT', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- UK
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('UK_NHS',  'PRESIDIO', false,0.95, 'UK NHS', 'UK NHS Number (disabled)', 'Government ID', 'UK_NHS', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UK_NINO', 'PRESIDIO', false,0.95, 'UK NINO', 'UK National Insurance Number (disabled)', 'Government ID', 'UK_NINO', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Spain
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('ES_NIF', 'PRESIDIO', true, 0.90, 'Spanish NIF', 'Spanish Personal Tax ID', 'Government ID', 'ES_NIF', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ES_NIE', 'PRESIDIO', true, 0.90, 'Spanish NIE', 'Spanish Foreigners ID', 'Government ID', 'ES_NIE', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Italy
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('IT_FISCAL_CODE',  'PRESIDIO', true, 0.95, 'Italian Fiscal Code', 'Italian Fiscal Code', 'Government ID', 'IT_FISCAL_CODE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_DRIVER_LICENSE','PRESIDIO', true, 0.90, 'Italian Driver License', 'Italian Driver License', 'Government ID', 'IT_DRIVER_LICENSE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_VAT_CODE',     'PRESIDIO', true, 0.90, 'Italian VAT', 'Italian VAT Code', 'Financial', 'IT_VAT_CODE', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_PASSPORT',     'PRESIDIO', true, 0.95, 'Italian Passport', 'Italian Passport Number', 'Government ID', 'IT_PASSPORT', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IT_IDENTITY_CARD','PRESIDIO', true, 0.90, 'Italian ID Card', 'Italian Identity Card', 'Government ID', 'IT_IDENTITY_CARD', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Poland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('PL_PESEL', 'PRESIDIO', true, 0.95, 'Polish PESEL', 'Polish Personal ID', 'Government ID', 'PL_PESEL', 'PL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Singapore
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('SG_NRIC_FIN', 'PRESIDIO', true, 0.95, 'Singapore NRIC', 'Singapore NRIC/FIN', 'Government ID', 'SG_NRIC_FIN', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SG_UEN',      'PRESIDIO', true, 0.90, 'Singapore UEN', 'Singapore Unique Entity Number', 'Business', 'SG_UEN', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Australia
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('AU_ABN',      'PRESIDIO', true, 0.90, 'Australian ABN', 'Australian Business Number', 'Business', 'AU_ABN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_ACN',      'PRESIDIO', true, 0.90, 'Australian ACN', 'Australian Company Number', 'Business', 'AU_ACN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_TFN',      'PRESIDIO', true, 0.95, 'Australian TFN', 'Australian Tax File Number', 'Government ID', 'AU_TFN', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AU_MEDICARE', 'PRESIDIO', true, 0.95, 'Australian Medicare', 'Australian Medicare Number', 'Medical', 'AU_MEDICARE', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- India
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('IN_PAN',                  'PRESIDIO', true, 0.90, 'Indian PAN', 'Indian Permanent Account Number', 'Government ID', 'IN_PAN', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_AADHAAR',              'PRESIDIO', true, 0.95, 'Indian Aadhaar', 'Indian Aadhaar Number', 'Government ID', 'IN_AADHAAR', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VEHICLE_REGISTRATION', 'PRESIDIO', true, 0.85, 'Indian Vehicle Reg', 'Indian Vehicle Registration', 'Government ID', 'IN_VEHICLE_REGISTRATION', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_VOTER',                'PRESIDIO', true, 0.90, 'Indian Voter ID', 'Indian Voter ID', 'Government ID', 'IN_VOTER', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IN_PASSPORT',             'PRESIDIO', true, 0.95, 'Indian Passport', 'Indian Passport Number', 'Government ID', 'IN_PASSPORT', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Finland
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('FI_PERSONAL_IDENTITY_CODE', 'PRESIDIO', true, 0.95, 'Finnish ID Code', 'Finnish Personal Identity Code', 'Government ID', 'FI_PERSONAL_IDENTITY_CODE', 'FI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Korea
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('KR_RRN', 'PRESIDIO', true, 0.95, 'Korean RRN', 'Korean Resident Registration Number', 'Government ID', 'KR_RRN', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Thailand
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, country_code, created_at, updated_at, updated_by)
VALUES
    ('TH_TNIN', 'PRESIDIO', true, 0.95, 'Thai National ID', 'Thai National ID Number', 'Government ID', 'TH_TNIN', 'TH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Generic/Alias Presidio Types - INSERT ONLY (detector_label directement renseigné)
-- ============================================================================
-- Short aliases
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PHONE',         'PRESIDIO', false, 0.90, 'Phone (Generic)', 'Generic phone number (alias for PHONE_NUMBER)', 'Contact',   'PHONE_NUMBER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN',          'PRESIDIO', false, 0.95, 'IBAN (Generic)', 'Generic IBAN (alias for IBAN_CODE)',            'Financial', 'IBAN_CODE',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO_WALLET', 'PRESIDIO', false, 0.90, 'Crypto Wallet (Generic)', 'Generic crypto wallet (alias for CRYPTO)', 'Financial','CRYPTO',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Generic national ID aliases
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('SSN',           'PRESIDIO', false, 0.90, 'SSN (Generic)', 'Generic Social Security Number', 'Government ID', 'US_SSN',            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT',      'PRESIDIO', false, 0.90, 'Passport (Generic)', 'Generic passport number',   'Government ID', 'US_PASSPORT',       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE','PRESIDIO', false, 0.85, 'Driver License (Generic)', 'Generic driver license','Government ID','US_DRIVER_LICENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ITIN',          'PRESIDIO', false, 0.90, 'ITIN (Generic)', 'Generic Individual Taxpayer ID','Government ID', 'US_ITIN',           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Generic medical/business aliases
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('NHS_NUMBER', 'PRESIDIO', false, 0.90, 'NHS Number (Generic)', 'Generic NHS number', 'Medical',       'UK_NHS',       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NRIC',       'PRESIDIO', false, 0.90, 'NRIC (Generic)', 'Generic National Registration ID', 'Government ID', 'SG_NRIC_FIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ABN',        'PRESIDIO', false, 0.85, 'ABN (Generic)', 'Generic Australian Business Number', 'Business',    'AU_ABN',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACN',        'PRESIDIO', false, 0.85, 'ACN (Generic)', 'Generic Australian Company Number',  'Business',    'AU_ACN',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TFN',        'PRESIDIO', false, 0.90, 'TFN (Generic)', 'Generic Tax File Number',            'Government ID','AU_TFN',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICARE',   'PRESIDIO', false, 0.90, 'Medicare (Generic)', 'Generic Medicare number',       'Medical',     'AU_MEDICARE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

-- Additional generic types
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'PRESIDIO', false, 0.75, 'Person Name (Generic)', 'Generic person name (alias for PERSON)', 'Identity', 'PERSON',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE',        'PRESIDIO', false, 0.85, 'Date (Generic)',        'Generic date (simpler than DATE_TIME)',  'Identity', 'DATE_TIME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (pii_type, detector) DO NOTHING;

COMMIT;
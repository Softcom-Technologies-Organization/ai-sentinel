-- ============================================================================
-- PII Detection Configuration Data
-- ============================================================================
-- This file contains INSERT statements for initializing PII detection configuration
-- Tables are created by JPA/Hibernate (ddl-auto=update)
-- This script runs after schema creation (spring.jpa.defer-datasource-initialization=true)
--
-- Source: Migrated from init-scripts/006-pii-type-config.sql and 007-pii-detection-config.sql
-- ============================================================================

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================

INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, true, true, 0.30, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- OLD PII Type Configuration - GLiNER Detector (COMMENTED OUT)
-- These have been replaced by the NEW GLINER PII section below
-- ============================================================================

-- -- Category: Contact Information
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('EMAIL', 'GLINER', true, 0.2, 'Email Address', 'Email addresses detected by GLiNER', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('TELEPHONENUM', 'GLINER', true, 0.20, 'Phone Number', 'Telephone numbers', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('USERNAME', 'GLINER', true, 0.2, 'Username', 'Usernames and account identifiers', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Category: Personal Identity
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('GIVENNAME', 'GLINER', true, 0.2, 'First Name', 'Given names (first names)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('SURNAME', 'GLINER', true, 0.2, 'Last Name', 'Surnames (last names)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('DATEOFBIRTH', 'GLINER', true, 0.2, 'Date of Birth', 'Birth dates', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Category: Financial
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('CREDITCARDNUMBER', 'GLINER', true, 0.20, 'Credit Card', 'Credit card numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('ACCOUNTNUM', 'GLINER', true, 0.20, 'Account Number', 'Bank account numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('TAXNUM', 'GLINER', true, 0.20, 'Tax Number', 'Tax identification numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Category: Government IDs
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('SOCIALNUM', 'GLINER', true, 0.20, 'Social Security Number',
--         'Social security and national ID numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('DRIVERLICENSENUM', 'GLINER', true, 0.20, 'Driver License', 'Driver license numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('IDCARDNUM', 'GLINER', true, 0.20, 'ID Card Number', 'National ID card numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Category: Location
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('STREET', 'GLINER', true, 0.80, 'Street Address', 'Street addresses', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('CITY', 'GLINER', true, 0.80, 'City', 'City names', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('ZIPCODE', 'GLINER', true, 0.80, 'Zip Code', 'Postal/ZIP codes', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
--        ('BUILDINGNUM', 'GLINER', true, 0.80, 'Building Number', 'Building numbers', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Category: Security
-- INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
-- VALUES ('PASSWORD', 'GLINER', true, 0.2, 'Password', 'Passwords and secrets', 'Security', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
-- ON CONFLICT (pii_type, detector) DO NOTHING;

-- -- Old detector label mappings (commented out)
-- -- UPDATE pii_type_config SET detector_label = 'account number' WHERE pii_type = 'ACCOUNTNUM' AND detector = 'GLINER';
-- -- ... (all other old mappings)

-- -- Old additional GLiNER PII types (commented out)
-- -- INSERT INTO pii_type_config ... FULLNAME, URL, IPADDRESS, etc.

-- -- Old phases 1-8 (all commented out)
-- -- ROUTINGNUM, CVV, PASSPORTNUM, MEDICALPROFNAME, etc.


-- ============================================================================
-- ============================================================================
-- NEW GLINER PII - Optimized for Category Multi-Pass Detection
-- ============================================================================
-- ============================================================================
--
-- Design principles:
-- 1. Each PII type belongs to exactly ONE category (no duplicates)
-- 2. Labels are natural language phrases that GLiNER understands well
-- 3. Categories are balanced (4-8 types each) for optimal multi-pass performance
-- 4. Thresholds are tuned per category based on detection confidence
--
-- Categories:
-- - Contact (2 types): Communication identifiers
-- - Identity (5 types): Personal identification info
-- - Financial (6 types): Banking and payment info
-- - Government_ID (5 types): Official documents and IDs
-- - Location (5 types): Geographic and address info
-- - Security (5 types): Credentials and secrets
-- - Technical (3 types): Network and system identifiers
-- - Healthcare (4 types): Medical information
-- ============================================================================

-- ============================================================================
-- Category: CONTACT_CHANNEL
-- Direct ways to contact / reach a person
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL', 'GLINER', true, 0.2, 'Email Address', 'Email addresses', 'CONTACT_CHANNEL', 'email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE', 'GLINER', true, 0.2, 'Phone Number', 'Phone and telephone numbers', 'CONTACT_CHANNEL', 'phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: PERSON_IDENTITY
-- Who the person is (identity attributes used to identify)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'GLINER', true, 0.7, 'Person Name', 'Full names or partial names of individuals', 'PERSON_IDENTITY', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('USERNAME', 'GLINER', true, 0.7, 'Username', 'Usernames and account names', 'PERSON_IDENTITY', 'username', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: PERSON_DEMOGRAPHICS
-- Personal characteristics (often quasi-identifiers)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('DATE_OF_BIRTH', 'GLINER', true, 0.7, 'Date of Birth', 'Birth dates', 'PERSON_DEMOGRAPHICS', 'date of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.7, 'Age', 'Age of individuals', 'PERSON_DEMOGRAPHICS', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.7, 'Gender', 'Gender identifiers', 'PERSON_DEMOGRAPHICS', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: FINANCIAL_IDENTIFIER
-- Banking / payment / wallet identifiers
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD', 'GLINER', true, 0.5, 'Credit Card Number', 'Credit and debit card numbers', 'FINANCIAL_IDENTIFIER', 'credit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT', 'GLINER', true, 0.5, 'Bank Account Number', 'Bank account numbers', 'FINANCIAL_IDENTIFIER', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.5, 'IBAN', 'International Bank Account Numbers', 'FINANCIAL_IDENTIFIER', 'iban', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ROUTING_NUMBER', 'GLINER', true, 0.5, 'Routing Number', 'Bank routing numbers', 'FINANCIAL_IDENTIFIER', 'routing number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'GLINER', true, 0.5, 'Tax ID', 'Tax identification numbers', 'FINANCIAL_IDENTIFIER', 'tax identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTO_WALLET', 'GLINER', true, 0.4, 'Crypto Wallet', 'Cryptocurrency wallet addresses', 'FINANCIAL_IDENTIFIER', 'cryptocurrency wallet address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: GOVERNMENT_IDENTIFIER
-- Official government-issued identifiers (human IDs)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('SSN', 'GLINER', true, 0.9, 'Social Security Number', 'Social security numbers', 'GOVERNMENT_IDENTIFIER', 'social security number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT', 'GLINER', true, 0.9, 'Passport Number', 'Passport numbers', 'GOVERNMENT_IDENTIFIER', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE', 'GLINER', true, 0.9, 'Driver License', 'Driver license numbers', 'GOVERNMENT_IDENTIFIER', 'driver license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'GLINER', true, 0.9, 'National ID', 'National identity card numbers', 'GOVERNMENT_IDENTIFIER', 'national id number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: GEO_LOCATION
-- Address / geographic info
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('ADDRESS', 'GLINER', true, 0.2, 'Street Address', 'Street addresses including building numbers', 'GEO_LOCATION', 'street address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CITY', 'GLINER', true, 0.2, 'City', 'City names', 'GEO_LOCATION', 'city', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STATE', 'GLINER', true, 0.2, 'State/Province', 'State or province names', 'GEO_LOCATION', 'state', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTRY', 'GLINER', true, 0.2, 'Country', 'Country names', 'GEO_LOCATION', 'country', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ZIP_CODE', 'GLINER', true, 0.2, 'ZIP/Postal Code', 'ZIP codes and postal codes', 'GEO_LOCATION', 'postal code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: CREDENTIAL_SECRET
-- Secrets / credentials / auth material (high risk)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PASSWORD', 'GLINER', true, 0.2, 'Password', 'Passwords and passphrases', 'CREDENTIAL_SECRET', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'GLINER', true, 0.2, 'API Key', 'API keys and access keys', 'CREDENTIAL_SECRET', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'GLINER', true, 0.2, 'Access Token', 'Access tokens and bearer tokens', 'CREDENTIAL_SECRET', 'access token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRET_KEY', 'GLINER', true, 0.2, 'Secret Key', 'Secret keys and private keys', 'CREDENTIAL_SECRET', 'secret key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CONNECTION_STRING', 'GLINER', true, 0.2, 'Connection String', 'Database connection strings', 'CREDENTIAL_SECRET', 'database connection string', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: STRUCTURED_TECH_IDENTIFIER
-- Structured identifiers with specific formats (often numbers/hex + separators)
-- This is where your "IP ~ AVS" grouping fits best.
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS', 'GLINER', true, 0.2, 'IP Address', 'IPv4 and IPv6 addresses', 'STRUCTURED_TECH_IDENTIFIER', 'ip address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AVS_NUMBER', 'GLINER', true, 0.2, 'AVS Number', 'Swiss social security number (AVS/AHV)', 'STRUCTURED_TECH_IDENTIFIER', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'GLINER', true, 0.2, 'MAC Address', 'Network MAC addresses', 'STRUCTURED_TECH_IDENTIFIER', 'mac address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'GLINER', true, 0.2, 'URL', 'Web URLs and links', 'STRUCTURED_TECH_IDENTIFIER', 'url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Category: HEALTHCARE
-- Medical / PHI-like entities
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('MEDICAL_RECORD', 'GLINER', true, 0.2, 'Medical Record Number', 'Medical record numbers', 'HEALTHCARE', 'medical record number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_INSURANCE', 'GLINER', true, 0.2, 'Health Insurance Number', 'Health insurance ID numbers', 'HEALTHCARE', 'health insurance number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_CONDITION', 'GLINER', true, 0.2, 'Medical Condition', 'Medical conditions and diagnoses', 'HEALTHCARE', 'medical condition', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICATION', 'GLINER', true, 0.2, 'Medication', 'Medication and drug names', 'HEALTHCARE', 'medication', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;


-- ============================================================================
-- NEW GLINER PII Summary:
-- Total: 35 PII types across 8 categories
-- - Contact: 2 types
-- - Identity: 5 types
-- - Financial: 6 types
-- - Government_ID: 5 types
-- - Location: 5 types
-- - Security: 5 types
-- - Technical: 3 types
-- - Healthcare: 4 types
-- ============================================================================


-- ============================================================================
-- PII Type Configuration - Presidio Detector (50+ types)
-- Source: pii-detector-service/config/models/presidio-detector.toml
-- ============================================================================

-- Category: Contact
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('EMAIL_ADDRESS', 'PRESIDIO', true, 0.95, 'Email Address', 'Email addresses (Presidio)', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('PHONE_NUMBER', 'PRESIDIO', false, 0.90, 'Phone Number', 'Phone numbers (Presidio, disabled by default)',
        'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('URL', 'PRESIDIO', false, 0.85, 'URL', 'Web URLs', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Financial
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('CREDIT_CARD', 'PRESIDIO', true, 0.90, 'Credit Card', 'Credit card numbers (Presidio)', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IBAN_CODE', 'PRESIDIO', true, 0.95, 'IBAN', 'International Bank Account Numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('CRYPTO', 'PRESIDIO', true, 0.90, 'Crypto Wallet', 'Cryptocurrency wallet addresses', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Network
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('IP_ADDRESS', 'PRESIDIO', true, 0.98, 'IP Address', 'IPv4 and IPv6 addresses', 'Network', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('MAC_ADDRESS', 'PRESIDIO', false, 0.90, 'MAC Address', 'MAC addresses (disabled by default)', 'Network', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Personal
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('PERSON', 'PRESIDIO', false, 0.75, 'Person Name', 'Person names (generates false positives, disabled)',
        'Personal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('LOCATION', 'PRESIDIO', false, 0.90, 'Location', 'Geographic locations (disabled)', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('DATE_TIME', 'PRESIDIO', false, 0.90, 'Date/Time', 'Dates and times (disabled)', 'Personal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('AGE', 'PRESIDIO', true, 0.85, 'Age', 'Age values', 'Personal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('NRP', 'PRESIDIO', false, 0.70, 'NRP', 'Nationality, Religious or Political group (disabled)', 'Personal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Medical
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('MEDICAL_LICENSE', 'PRESIDIO', true, 0.90, 'Medical License', 'Medical license numbers', 'Medical', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: USA
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('US_SSN', 'PRESIDIO', true, 0.95, 'US SSN', 'US Social Security Number', 'Government ID', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('US_BANK_NUMBER', 'PRESIDIO', true, 0.90, 'US Bank Account', 'US Bank Account Number', 'Financial', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('US_DRIVER_LICENSE', 'PRESIDIO', true, 0.90, 'US Driver License', 'US Driver License Number',
        'Government ID', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('US_ITIN', 'PRESIDIO', true, 0.95, 'US ITIN', 'US Individual Taxpayer ID', 'Government ID', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('US_PASSPORT', 'PRESIDIO', true, 0.95, 'US Passport', 'US Passport Number', 'Government ID', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: UK
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('UK_NHS', 'PRESIDIO', false, 0.95, 'UK NHS', 'UK NHS Number (disabled)', 'Government ID', 'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('UK_NINO', 'PRESIDIO', false, 0.95, 'UK NINO', 'UK National Insurance Number (disabled)', 'Government ID',
        'UK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Spain
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('ES_NIF', 'PRESIDIO', true, 0.90, 'Spanish NIF', 'Spanish Personal Tax ID', 'Government ID', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ES_NIE', 'PRESIDIO', true, 0.90, 'Spanish NIE', 'Spanish Foreigners ID', 'Government ID', 'ES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Italy
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('IT_FISCAL_CODE', 'PRESIDIO', true, 0.95, 'Italian Fiscal Code', 'Italian Fiscal Code', 'Government ID',
        'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IT_DRIVER_LICENSE', 'PRESIDIO', true, 0.90, 'Italian Driver License', 'Italian Driver License',
        'Government ID', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IT_VAT_CODE', 'PRESIDIO', true, 0.90, 'Italian VAT', 'Italian VAT Code', 'Financial', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IT_PASSPORT', 'PRESIDIO', true, 0.95, 'Italian Passport', 'Italian Passport Number', 'Government ID', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IT_IDENTITY_CARD', 'PRESIDIO', true, 0.90, 'Italian ID Card', 'Italian Identity Card', 'Government ID',
        'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Poland
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('PL_PESEL', 'PRESIDIO', true, 0.95, 'Polish PESEL', 'Polish Personal ID', 'Government ID', 'PL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Singapore
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('SG_NRIC_FIN', 'PRESIDIO', true, 0.95, 'Singapore NRIC', 'Singapore NRIC/FIN', 'Government ID', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('SG_UEN', 'PRESIDIO', true, 0.90, 'Singapore UEN', 'Singapore Unique Entity Number', 'Business', 'SG', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Australia
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('AU_ABN', 'PRESIDIO', true, 0.90, 'Australian ABN', 'Australian Business Number', 'Business', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('AU_ACN', 'PRESIDIO', true, 0.90, 'Australian ACN', 'Australian Company Number', 'Business', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('AU_TFN', 'PRESIDIO', true, 0.95, 'Australian TFN', 'Australian Tax File Number', 'Government ID', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('AU_MEDICARE', 'PRESIDIO', true, 0.95, 'Australian Medicare', 'Australian Medicare Number', 'Medical', 'AU', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: India
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('IN_PAN', 'PRESIDIO', true, 0.90, 'Indian PAN', 'Indian Permanent Account Number', 'Government ID', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IN_AADHAAR', 'PRESIDIO', true, 0.95, 'Indian Aadhaar', 'Indian Aadhaar Number', 'Government ID', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IN_VEHICLE_REGISTRATION', 'PRESIDIO', true, 0.85, 'Indian Vehicle Reg', 'Indian Vehicle Registration',
        'Government ID', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IN_VOTER', 'PRESIDIO', true, 0.90, 'Indian Voter ID', 'Indian Voter ID', 'Government ID', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IN_PASSPORT', 'PRESIDIO', true, 0.95, 'Indian Passport', 'Indian Passport Number', 'Government ID', 'IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Finland
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('FI_PERSONAL_IDENTITY_CODE', 'PRESIDIO', true, 0.95, 'Finnish ID Code', 'Finnish Personal Identity Code',
        'Government ID', 'FI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Korea
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('KR_RRN', 'PRESIDIO', true, 0.95, 'Korean RRN', 'Korean Resident Registration Number', 'Government ID', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Thailand
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category,
                              country_code, created_at, updated_at, updated_by)
VALUES ('TH_TNIN', 'PRESIDIO', true, 0.95, 'Thai National ID', 'Thai National ID Number', 'Government ID', 'TH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Detector label mappings for Presidio PII types
-- Presidio uses uppercase entity types as detector labels (e.g., EMAIL_ADDRESS, CREDIT_CARD)
-- ============================================================================

-- Contact Information
UPDATE pii_type_config SET detector_label = 'EMAIL_ADDRESS' WHERE pii_type = 'EMAIL_ADDRESS' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'PHONE_NUMBER' WHERE pii_type = 'PHONE_NUMBER' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'URL' WHERE pii_type = 'URL' AND detector = 'PRESIDIO';

-- Financial
UPDATE pii_type_config SET detector_label = 'CREDIT_CARD' WHERE pii_type = 'CREDIT_CARD' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IBAN_CODE' WHERE pii_type = 'IBAN_CODE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'CRYPTO' WHERE pii_type = 'CRYPTO' AND detector = 'PRESIDIO';

-- Network
UPDATE pii_type_config SET detector_label = 'IP_ADDRESS' WHERE pii_type = 'IP_ADDRESS' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'MAC_ADDRESS' WHERE pii_type = 'MAC_ADDRESS' AND detector = 'PRESIDIO';

-- Personal
UPDATE pii_type_config SET detector_label = 'PERSON' WHERE pii_type = 'PERSON' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'LOCATION' WHERE pii_type = 'LOCATION' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'DATE_TIME' WHERE pii_type = 'DATE_TIME' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AGE' WHERE pii_type = 'AGE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'NRP' WHERE pii_type = 'NRP' AND detector = 'PRESIDIO';

-- Medical
UPDATE pii_type_config SET detector_label = 'MEDICAL_LICENSE' WHERE pii_type = 'MEDICAL_LICENSE' AND detector = 'PRESIDIO';

-- USA
UPDATE pii_type_config SET detector_label = 'US_SSN' WHERE pii_type = 'US_SSN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_BANK_NUMBER' WHERE pii_type = 'US_BANK_NUMBER' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_DRIVER_LICENSE' WHERE pii_type = 'US_DRIVER_LICENSE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_ITIN' WHERE pii_type = 'US_ITIN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_PASSPORT' WHERE pii_type = 'US_PASSPORT' AND detector = 'PRESIDIO';

-- UK
UPDATE pii_type_config SET detector_label = 'UK_NHS' WHERE pii_type = 'UK_NHS' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'UK_NINO' WHERE pii_type = 'UK_NINO' AND detector = 'PRESIDIO';

-- Spain
UPDATE pii_type_config SET detector_label = 'ES_NIF' WHERE pii_type = 'ES_NIF' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'ES_NIE' WHERE pii_type = 'ES_NIE' AND detector = 'PRESIDIO';

-- Italy
UPDATE pii_type_config SET detector_label = 'IT_FISCAL_CODE' WHERE pii_type = 'IT_FISCAL_CODE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IT_DRIVER_LICENSE' WHERE pii_type = 'IT_DRIVER_LICENSE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IT_VAT_CODE' WHERE pii_type = 'IT_VAT_CODE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IT_PASSPORT' WHERE pii_type = 'IT_PASSPORT' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IT_IDENTITY_CARD' WHERE pii_type = 'IT_IDENTITY_CARD' AND detector = 'PRESIDIO';

-- Poland
UPDATE pii_type_config SET detector_label = 'PL_PESEL' WHERE pii_type = 'PL_PESEL' AND detector = 'PRESIDIO';

-- Singapore
UPDATE pii_type_config SET detector_label = 'SG_NRIC_FIN' WHERE pii_type = 'SG_NRIC_FIN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'SG_UEN' WHERE pii_type = 'SG_UEN' AND detector = 'PRESIDIO';

-- Australia
UPDATE pii_type_config SET detector_label = 'AU_ABN' WHERE pii_type = 'AU_ABN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_ACN' WHERE pii_type = 'AU_ACN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_TFN' WHERE pii_type = 'AU_TFN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_MEDICARE' WHERE pii_type = 'AU_MEDICARE' AND detector = 'PRESIDIO';

-- India
UPDATE pii_type_config SET detector_label = 'IN_PAN' WHERE pii_type = 'IN_PAN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IN_AADHAAR' WHERE pii_type = 'IN_AADHAAR' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IN_VEHICLE_REGISTRATION' WHERE pii_type = 'IN_VEHICLE_REGISTRATION' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IN_VOTER' WHERE pii_type = 'IN_VOTER' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IN_PASSPORT' WHERE pii_type = 'IN_PASSPORT' AND detector = 'PRESIDIO';

-- Finland
UPDATE pii_type_config SET detector_label = 'FI_PERSONAL_IDENTITY_CODE' WHERE pii_type = 'FI_PERSONAL_IDENTITY_CODE' AND detector = 'PRESIDIO';

-- Korea
UPDATE pii_type_config SET detector_label = 'KR_RRN' WHERE pii_type = 'KR_RRN' AND detector = 'PRESIDIO';

-- Thailand
UPDATE pii_type_config SET detector_label = 'TH_TNIN' WHERE pii_type = 'TH_TNIN' AND detector = 'PRESIDIO';

-- ============================================================================
-- Summary:
-- - 1 global detection config (singleton)
-- - 35 NEW GLiNER PII types (8 categories, no duplicates)
-- - 50 Presidio country-specific PII types
-- ============================================================================

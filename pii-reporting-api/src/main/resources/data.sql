-- ============================================================================
-- PII Detection Configuration Data
-- ============================================================================
-- This file contains INSERT statements for initializing PII detection configuration
-- Tables are created by JPA/Hibernate (ddl-auto=update)
-- This script runs after schema creation (spring.jpa.defer-datasource-initialization=true)
--
-- Source: Migrated from init-scripts/007-pii-type-config.sql and 008-pii-detection-config.sql
-- ============================================================================

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================

INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, true, true, 0.80, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- PII Type Configuration - GLiNER Detector (17 types)
-- Source: pii-detector-service/config/models/gliner-pii.toml
-- ============================================================================

-- Category: Contact Information
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('EMAIL', 'GLINER', true, 0.80, 'Email Address', 'Email addresses detected by GLiNER', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('TELEPHONENUM', 'GLINER', true, 0.80, 'Phone Number', 'Telephone numbers', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('USERNAME', 'GLINER', true, 0.75, 'Username', 'Usernames and account identifiers', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Personal Identity
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('GIVENNAME', 'GLINER', true, 0.75, 'First Name', 'Given names (first names)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('SURNAME', 'GLINER', true, 0.75, 'Last Name', 'Surnames (last names)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('DATEOFBIRTH', 'GLINER', true, 0.80, 'Date of Birth', 'Birth dates', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Financial
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('CREDITCARDNUMBER', 'GLINER', true, 0.70, 'Credit Card', 'Credit card numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ACCOUNTNUM', 'GLINER', true, 0.70, 'Account Number', 'Bank account numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('TAXNUM', 'GLINER', true, 0.80, 'Tax Number', 'Tax identification numbers', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Government IDs
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('SOCIALNUM', 'GLINER', true, 0.80, 'Social Security Number',
        'Social security and national ID numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('DRIVERLICENSENUM', 'GLINER', true, 0.80, 'Driver License', 'Driver license numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IDCARDNUM', 'GLINER', true, 0.80, 'ID Card Number', 'National ID card numbers', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Location
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('STREET', 'GLINER', true, 0.80, 'Street Address', 'Street addresses', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('CITY', 'GLINER', true, 0.80, 'City', 'City names', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ZIPCODE', 'GLINER', true, 0.80, 'Zip Code', 'Postal/ZIP codes', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('BUILDINGNUM', 'GLINER', true, 0.80, 'Building Number', 'Building numbers', 'Location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Category: Security
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('PASSWORD', 'GLINER', true, 0.85, 'Password', 'Passwords and secrets', 'Security', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

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
-- Generic/Alias PII Types (disabled by default to avoid conflicts)
-- These complement country-specific types
-- ============================================================================

-- Generic short aliases for existing Presidio types
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('PHONE', 'PRESIDIO', false, 0.90, 'Phone (Generic)', 'Generic phone number (alias for PHONE_NUMBER)', 'Contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('IBAN', 'PRESIDIO', false, 0.95, 'IBAN (Generic)', 'Generic IBAN (alias for IBAN_CODE)', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('CRYPTO_WALLET', 'PRESIDIO', false, 0.90, 'Crypto Wallet (Generic)', 'Generic crypto wallet (alias for CRYPTO)', 'Financial', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Generic national ID types
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('SSN', 'PRESIDIO', false, 0.90, 'SSN (Generic)', 'Generic Social Security Number', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('PASSPORT', 'PRESIDIO', false, 0.90, 'Passport (Generic)', 'Generic passport number', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('DRIVER_LICENSE', 'PRESIDIO', false, 0.85, 'Driver License (Generic)', 'Generic driver license', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ITIN', 'PRESIDIO', false, 0.90, 'ITIN (Generic)', 'Generic Individual Taxpayer ID', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Generic medical/business IDs
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('NHS_NUMBER', 'PRESIDIO', false, 0.90, 'NHS Number (Generic)', 'Generic NHS number', 'Medical', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('NRIC', 'PRESIDIO', false, 0.90, 'NRIC (Generic)', 'Generic National Registration ID', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ABN', 'PRESIDIO', false, 0.85, 'ABN (Generic)', 'Generic Australian Business Number', 'Business', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('ACN', 'PRESIDIO', false, 0.85, 'ACN (Generic)', 'Generic Australian Company Number', 'Business', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('TFN', 'PRESIDIO', false, 0.90, 'TFN (Generic)', 'Generic Tax File Number', 'Government ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('MEDICARE', 'PRESIDIO', false, 0.90, 'Medicare (Generic)', 'Generic Medicare number', 'Medical', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Additional generic types
INSERT INTO pii_type_config (pii_type, detector, enabled, threshold, display_name, description, category, created_at, updated_at, updated_by)
VALUES ('PERSON_NAME', 'PRESIDIO', false, 0.75, 'Person Name (Generic)', 'Generic person name (alias for PERSON)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
       ('DATE', 'PRESIDIO', false, 0.85, 'Date (Generic)', 'Generic date (simpler than DATE_TIME)', 'Identity', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Summary: 
-- - 1 global detection config (singleton)
-- - 17 GLiNER PII types
-- - 50 Presidio country-specific PII types
-- - 16 Presidio generic/alias PII types
-- Total: 83 PII type configurations
-- ============================================================================

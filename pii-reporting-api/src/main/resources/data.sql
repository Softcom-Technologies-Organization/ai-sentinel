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
-- Detector label mappings for GLiNER PII types
-- Migrated from init-scripts/009-add-detector-label.sql
-- ============================================================================

UPDATE pii_type_config SET detector_label = 'account number' WHERE pii_type = 'ACCOUNTNUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'building number' WHERE pii_type = 'BUILDINGNUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'city' WHERE pii_type = 'CITY' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'credit card number' WHERE pii_type = 'CREDITCARDNUMBER' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'date of birth' WHERE pii_type = 'DATEOFBIRTH' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'driver license number' WHERE pii_type = 'DRIVERLICENSENUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'email address' WHERE pii_type = 'EMAIL' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'first name' WHERE pii_type = 'GIVENNAME' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'ID card number' WHERE pii_type = 'IDCARDNUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'password' WHERE pii_type = 'PASSWORD' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'social security number' WHERE pii_type = 'SOCIALNUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'street' WHERE pii_type = 'STREET' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'last name' WHERE pii_type = 'SURNAME' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'tax number' WHERE pii_type = 'TAXNUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'phone number' WHERE pii_type = 'TELEPHONENUM' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'username' WHERE pii_type = 'USERNAME' AND detector = 'GLINER';
UPDATE pii_type_config SET detector_label = 'zip code' WHERE pii_type = 'ZIPCODE' AND detector = 'GLINER';

-- Insert additional GLiNER PII types with detector labels
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('PERSONNAME', 'GLINER', true, 0.7, 'Person Name', 'Generic person name (not split into first/last)', 'Identity', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FULLNAME', 'GLINER', true, 0.7, 'Full Name', 'Complete person name', 'Identity', 'full name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'GLINER', true, 0.7, 'URL', 'Web URL or hyperlink', 'Online', 'URL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.7, 'IBAN', 'International Bank Account Number', 'Financial', 'IBAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IPADDRESS', 'GLINER', true, 0.7, 'IP Address', 'IPv4 or IPv6 address', 'Technical', 'IP address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MACADDRESS', 'GLINER', true, 0.7, 'MAC Address', 'Network hardware address', 'Technical', 'MAC address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRYPTOWALLET', 'GLINER', true, 0.7, 'Crypto Wallet', 'Cryptocurrency wallet address', 'Financial', 'crypto wallet', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE', 'GLINER', true, 0.7, 'Date', 'Generic date (not birth date)', 'Temporal', 'date', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLEREG', 'GLINER', true, 0.7, 'Vehicle Registration', 'Vehicle license plate or registration number', 'Identity', 'vehicle registration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VOTERID', 'GLINER', true, 0.7, 'Voter ID', 'Voter identification number', 'Identity', 'voter ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- Additional GLiNER labels - Missing label mappings fix
-- Source: gliner.txt (GLiNER model labels - source of truth)
-- Critical Bug Fix: "bank account" and other GLiNER detections now mappable via UI
-- ============================================================================

-- ============================================================================
-- PHASE 1: CRITICAL FIX - Financial Information (Bank Account Bug)
-- ============================================================================

-- Add BANKACCOUNT enum mapping for "bank account" label
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('BANKACCOUNT', 'GLINER', true, 0.70, 'Bank Account', 'Bank account numbers detected by GLiNER', 'Financial', 'bank account', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Add other critical financial labels
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('ROUTINGNUM', 'GLINER', true, 0.70, 'Routing Number', 'Bank routing numbers', 'Financial', 'routing number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CREDITCARDEXP', 'GLINER', true, 0.70, 'Credit Card Expiration', 'Credit card expiration dates', 'Financial', 'credit card expiration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CVV', 'GLINER', true, 0.75, 'CVV', 'Credit card security codes (CVV/CVC)', 'Financial', 'cvv', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 2: NORMALIZATION FIXES - Label Variations
-- ============================================================================

-- Add alternative mapping for "credit card" (in addition to "credit card number")
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('CREDITCARD', 'GLINER', true, 0.70, 'Credit Card (short)', 'Credit card numbers - short label variant', 'Financial', 'credit card', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Add alternative mapping for "driver license" (in addition to "driver license number")
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('DRIVERLICENSE', 'GLINER', true, 0.80, 'Driver License (short)', 'Driver license numbers - short label variant', 'Government ID', 'driver license', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Add "dob" mapping (in addition to "date of birth")
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('DOB', 'GLINER', true, 0.80, 'DOB', 'Date of birth - short label variant', 'Identity', 'dob', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Add "ssn" mapping (in addition to "social security number")
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('SSN_SHORT', 'GLINER', true, 0.80, 'SSN (short)', 'Social Security Number - short label variant', 'Government ID', 'ssn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Add "location zip" mapping (in addition to "zip code")
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('LOCATIONZIP', 'GLINER', true, 0.80, 'Location Zip', 'ZIP/postal codes - location variant', 'Location', 'location zip', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 3: HIGH PRIORITY - Identification Documents & Healthcare
-- ============================================================================

-- Identification Documents
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('PASSPORTNUM', 'GLINER', true, 0.80, 'Passport Number', 'Passport numbers', 'Government ID', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLEID', 'GLINER', true, 0.75, 'Vehicle ID', 'Vehicle identification numbers', 'Identity', 'vehicle id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- Healthcare Information
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('MEDICALPROFNAME', 'GLINER', true, 0.75, 'Medical Professional Name', 'Healthcare provider names', 'Healthcare', 'name medical professional', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTHCARENUM', 'GLINER', true, 0.80, 'Healthcare Number', 'Healthcare identification numbers', 'Healthcare', 'healthcare number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 4: MEDIUM PRIORITY - Personal Identifiers
-- ============================================================================

-- Personal Identity Information
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('NAME', 'GLINER', true, 0.70, 'Name (Generic)', 'Generic person names', 'Identity', 'name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.75, 'Age', 'Age information', 'Identity', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.75, 'Gender', 'Gender identifiers', 'Identity', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MARITALSTATUS', 'GLINER', true, 0.75, 'Marital Status', 'Marital status information', 'Identity', 'marital status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 5: MEDIUM PRIORITY - Location Information
-- ============================================================================

-- Location Details
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('LOCATIONADDRESS', 'GLINER', true, 0.80, 'Location Address', 'Complete street addresses', 'Location', 'location address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATIONSTREET', 'GLINER', true, 0.80, 'Location Street', 'Street names', 'Location', 'location street', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATIONCITY', 'GLINER', true, 0.80, 'Location City', 'City names', 'Location', 'location city', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATIONSTATE', 'GLINER', true, 0.80, 'Location State', 'State/province names', 'Location', 'location state', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOCATIONCOUNTRY', 'GLINER', true, 0.75, 'Location Country', 'Country names', 'Location', 'location country', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 6: LOW PRIORITY - Healthcare Details
-- ============================================================================

-- Detailed Healthcare Information
INSERT INTO pii_type_config 
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES 
    ('MEDICALCONDITION', 'GLINER', true, 0.70, 'Medical Condition', 'Medical conditions and diagnoses', 'Healthcare', 'condition', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICALPROCESS', 'GLINER', true, 0.70, 'Medical Process', 'Medical procedures and treatments', 'Healthcare', 'medical process', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRUG', 'GLINER', true, 0.75, 'Drug', 'Pharmaceutical drugs and medications', 'Healthcare', 'drug', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DOSE', 'GLINER', true, 0.75, 'Dosage', 'Medication dosage information', 'Healthcare', 'dose', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BLOODTYPE', 'GLINER', true, 0.85, 'Blood Type', 'Blood type information', 'Healthcare', 'blood type', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INJURY', 'GLINER', true, 0.70, 'Injury', 'Physical injuries and trauma', 'Healthcare', 'injury', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICALFACILITY', 'GLINER', true, 0.75, 'Medical Facility', 'Healthcare facility names', 'Healthcare', 'organization medical facility', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICALCODE', 'GLINER', true, 0.80, 'Medical Code', 'Medical classification codes', 'Healthcare', 'medical code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 7: VERY LOW PRIORITY - Other Information
-- ============================================================================

-- Monetary Information
INSERT INTO pii_type_config
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('MONEY', 'GLINER', false, 0.65, 'Money', 'Monetary amounts and values', 'Financial', 'money', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO NOTHING;

-- ============================================================================
-- PHASE 8: SECURITY SECRETS & CREDENTIALS (Common in Confluence)
-- ============================================================================

INSERT INTO pii_type_config
    (pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('APIKEY', 'GLINER', true, 0.20, 'API Key', 'API keys and access keys', 'Security', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TOKEN', 'GLINER', true, 0.20, 'Token', 'Generic tokens and access tokens', 'Security', 'token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('JWT', 'GLINER', true, 0.20, 'JWT', 'JSON Web Tokens', 'Security', 'jwt', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESSKEYID', 'GLINER', true, 0.20, 'Access Key ID', 'AWS-style access key IDs', 'Security', 'access key id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRETACCESSKEY', 'GLINER', true, 0.20, 'Secret Access Key', 'AWS-style secret access keys', 'Security', 'secret access key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DBCONNECTIONSTRING', 'GLINER', true, 0.20, 'DB Connection String', 'Database connection strings', 'Security', 'db connection string', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATABASESTRING', 'GLINER', true, 0.20, 'Database String', 'Database connection strings', 'Security', 'database string', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('JDBCURL', 'GLINER', true, 0.20, 'JDBC URL', 'Java database connection URLs', 'Security', 'jdbc url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATABASEURL', 'GLINER', true, 0.20, 'Database URL', 'Database URLs', 'Security', 'database url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTGRESQLSTRING', 'GLINER', true, 0.20, 'PostgreSQL String', 'PostgreSQL connection strings', 'Security', 'postgresql string', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AVSNUM', 'GLINER', true, 0.20, 'AVS Number', 'Swiss social security number (AVS/AHV)', 'Government ID', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBANNUMBER', 'GLINER', true, 0.20, 'IBAN Number', 'International Bank Account Number (alternate label)', 'Financial', 'iban number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANKACCOUNTNUMBER', 'GLINER', true, 0.20, 'Bank Account Number', 'Bank account numbers (alternate label)', 'Financial', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
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

-- Detector label mappings for generic/alias Presidio types
-- These map to the same Presidio entity types as their main counterparts
UPDATE pii_type_config SET detector_label = 'PHONE_NUMBER' WHERE pii_type = 'PHONE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'IBAN_CODE' WHERE pii_type = 'IBAN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'CRYPTO' WHERE pii_type = 'CRYPTO_WALLET' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_SSN' WHERE pii_type = 'SSN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_PASSPORT' WHERE pii_type = 'PASSPORT' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_DRIVER_LICENSE' WHERE pii_type = 'DRIVER_LICENSE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'US_ITIN' WHERE pii_type = 'ITIN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'UK_NHS' WHERE pii_type = 'NHS_NUMBER' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'SG_NRIC_FIN' WHERE pii_type = 'NRIC' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_ABN' WHERE pii_type = 'ABN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_ACN' WHERE pii_type = 'ACN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_TFN' WHERE pii_type = 'TFN' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'AU_MEDICARE' WHERE pii_type = 'MEDICARE' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'PERSON' WHERE pii_type = 'PERSON_NAME' AND detector = 'PRESIDIO';
UPDATE pii_type_config SET detector_label = 'DATE_TIME' WHERE pii_type = 'DATE' AND detector = 'PRESIDIO';

-- ============================================================================
-- Summary: 
-- - 1 global detection config (singleton)
-- - 17 GLiNER PII types
-- - 50 Presidio country-specific PII types
-- - 16 Presidio generic/alias PII types
-- Total: 83 PII type configurations
-- ============================================================================

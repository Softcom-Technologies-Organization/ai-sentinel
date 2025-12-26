-- ============================================================================
-- PII Detection Configuration Data
-- ============================================================================
-- CONSOLIDATED VERSION: 44 PII types across 7 categories
-- Reduced from 107 types / 13 categories for better performance
-- ============================================================================

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================

INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, false, false, 0.30, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- ============================================================================
-- GLINER PII TYPES - Consolidated Multi-Pass Detection Categories
-- ============================================================================
-- ============================================================================
--
-- Design principles:
-- 1. Consolidated overlapping types into generic versions
-- 2. Removed low-value types that cause false positives
-- 3. Reduced categories from 13 to 7 for faster multi-pass detection
--
-- Categories (7 total, down from 13):
-- 1. IDENTITY - Core personal identity (9 types)
-- 2. CONTACT - Contact information (4 types)
-- 3. DIGITAL - Online/digital identifiers (3 types)
-- 4. FINANCIAL - Banking and payment info (6 types)
-- 5. MEDICAL - Health information (6 types)
-- 6. IT_CREDENTIALS - Technical identifiers and secrets (9 types)
-- 7. LEGAL_ASSET - Legal documents and property (7 types)
-- ============================================================================

-- ============================================================================
-- Category 1: IDENTITY - Core personal identity
-- Consolidated: FIRST_NAME, LAST_NAME, FULL_NAME -> PERSON_NAME
-- Consolidated: ID_CARD_NUMBER -> NATIONAL_ID
-- Removed: PLACE_OF_BIRTH, MARITAL_STATUS (low value)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'GLINER', true, 0.3, 'Person Name', 'Names of individuals (first, last, full)', 'IDENTITY', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'GLINER', true, 0.3, 'National ID', 'National identity card numbers', 'IDENTITY', 'national identity number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSN', 'GLINER', true, 0.3, 'Social Security Number', 'Social security numbers', 'IDENTITY', 'social security number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT_NUMBER', 'GLINER', true, 0.3, 'Passport Number', 'Passport identification numbers', 'IDENTITY', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE_NUMBER', 'GLINER', true, 0.3, 'Driver License', 'Driver license numbers', 'IDENTITY', 'driver license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DATE_OF_BIRTH', 'GLINER', true, 0.3, 'Date of Birth', 'Birth dates', 'IDENTITY', 'date of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.3, 'Gender', 'Gender identifiers', 'IDENTITY', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONALITY', 'GLINER', true, 0.3, 'Nationality', 'Nationality or citizenship', 'IDENTITY', 'nationality', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.3, 'Age', 'Age values', 'IDENTITY', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 2: CONTACT - Contact information
-- Consolidated: HOME_ADDRESS, MAILING_ADDRESS, STREET_ADDRESS -> ADDRESS
-- Consolidated: MOBILE_PHONE, FAX_NUMBER -> PHONE_NUMBER
-- Removed: CITY, STATE, COUNTRY, PO_BOX (granular, high false positive)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL', 'GLINER', true, 0.3, 'Email Address', 'Email addresses', 'CONTACT', 'email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'GLINER', true, 0.3, 'Phone Number', 'Phone numbers (mobile, landline, fax)', 'CONTACT', 'phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ADDRESS', 'GLINER', true, 0.3, 'Address', 'Physical addresses (home, work, mailing)', 'CONTACT', 'address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTAL_CODE', 'GLINER', true, 0.3, 'Postal Code', 'ZIP codes and postal codes', 'CONTACT', 'postal code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 3: DIGITAL - Online/digital identifiers
-- Consolidated: LOGIN, ONLINE_HANDLE -> USERNAME
-- Consolidated: USER_ID, CUSTOMER_ID -> ACCOUNT_ID
-- Consolidated: API_ENDPOINT, FILE_PATH -> URL (or removed)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('USERNAME', 'GLINER', true, 0.3, 'Username', 'Usernames, logins, and online handles', 'DIGITAL', 'username', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_ID', 'GLINER', true, 0.3, 'Account ID', 'Account, user, and customer IDs', 'DIGITAL', 'account id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('URL', 'GLINER', true, 0.3, 'URL', 'Web URLs and links', 'DIGITAL', 'url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 4: FINANCIAL - Banking and payment information
-- Consolidated: DEBIT_CARD_NUMBER -> CREDIT_CARD_NUMBER
-- Removed: ROUTING_NUMBER, VAT_NUMBER, PAYMENT_REFERENCE, INVOICE_NUMBER, TRANSACTION_ID (business IDs, not PII)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD_NUMBER', 'GLINER', true, 0.3, 'Credit Card Number', 'Credit and debit card numbers', 'FINANCIAL', 'credit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT_NUMBER', 'GLINER', true, 0.3, 'Bank Account Number', 'Bank account numbers', 'FINANCIAL', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.3, 'IBAN', 'International Bank Account Numbers', 'FINANCIAL', 'iban', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIC_SWIFT', 'GLINER', true, 0.3, 'BIC/SWIFT Code', 'Bank Identifier Codes', 'FINANCIAL', 'swift code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'GLINER', true, 0.3, 'Tax ID', 'Tax identification numbers', 'FINANCIAL', 'tax identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SALARY', 'GLINER', true, 0.3, 'Salary', 'Salary and wage information', 'FINANCIAL', 'salary amount', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 5: MEDICAL - Health information (HIPAA/GDPR Art. 9)
-- Consolidated: MEDICAL_CONDITION -> DIAGNOSIS
-- Consolidated: PRESCRIPTION, TREATMENT -> MEDICATION
-- Removed: DOCTOR_NAME (use PERSON_NAME), HOSPITAL_NAME (org name, not PII)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'GLINER', true, 0.3, 'AVS Number', 'Swiss social security number (AVS/AHV)', 'MEDICAL', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PATIENT_ID', 'GLINER', true, 0.3, 'Patient ID', 'Patient identification numbers', 'MEDICAL', 'patient id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_RECORD_NUMBER', 'GLINER', true, 0.3, 'Medical Record Number', 'Medical record numbers', 'MEDICAL', 'medical record number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_INSURANCE_NUMBER', 'GLINER', true, 0.3, 'Health Insurance Number', 'Health insurance ID numbers', 'MEDICAL', 'health insurance number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DIAGNOSIS', 'GLINER', true, 0.3, 'Diagnosis', 'Medical diagnoses and conditions', 'MEDICAL', 'medical diagnosis', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICATION', 'GLINER', true, 0.3, 'Medication', 'Medications, prescriptions, and treatments', 'MEDICAL', 'medication name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 6: IT_CREDENTIALS - Technical identifiers and secrets
-- Consolidated: REFRESH_TOKEN, OAUTH_TOKEN -> ACCESS_TOKEN
-- Consolidated: PRIVATE_KEY, SSH_KEY -> SECRET_KEY
-- Consolidated: COOKIE_ID -> SESSION_ID
-- Removed: SERIAL_NUMBER, IMEI, IMSI (device IDs rarely PII)
-- Removed: USER_AGENT, PASSWORD_HASH (metadata/derived)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('IP_ADDRESS', 'GLINER', true, 0.3, 'IP Address', 'IPv4 and IPv6 addresses', 'IT_CREDENTIALS', 'ip address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'GLINER', true, 0.3, 'MAC Address', 'Network MAC addresses', 'IT_CREDENTIALS', 'mac address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOSTNAME', 'GLINER', true, 0.3, 'Hostname', 'Computer hostnames', 'IT_CREDENTIALS', 'hostname', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_ID', 'GLINER', true, 0.3, 'Device ID', 'Device identification numbers', 'IT_CREDENTIALS', 'device id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD', 'GLINER', true, 0.3, 'Password', 'Passwords and passphrases', 'IT_CREDENTIALS', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'GLINER', true, 0.3, 'API Key', 'API keys and access keys', 'IT_CREDENTIALS', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'GLINER', true, 0.3, 'Access Token', 'Access tokens (bearer, OAuth, refresh)', 'IT_CREDENTIALS', 'access token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRET_KEY', 'GLINER', true, 0.3, 'Secret Key', 'Secret keys (private, SSH, crypto)', 'IT_CREDENTIALS', 'secret key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SESSION_ID', 'GLINER', true, 0.3, 'Session ID', 'Session and cookie identifiers', 'IT_CREDENTIALS', 'session id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 7: LEGAL_ASSET - Legal documents and property
-- Merged: LEGAL + ASSET categories
-- Consolidated: PERMIT_NUMBER -> LICENSE_NUMBER
-- Removed: COURT_NAME (org name), IMMIGRATION_STATUS (status not ID)
-- Removed: PROPERTY_ID (rarely PII), BIOMETRIC (never in Confluence)
-- Removed: All PROFESSIONAL types (JOB_TITLE, COMPANY_NAME etc. not PII)
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CASE_NUMBER', 'GLINER', true, 0.3, 'Case Number', 'Legal case numbers', 'LEGAL_ASSET', 'case number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_NUMBER', 'GLINER', true, 0.3, 'License Number', 'License and permit numbers', 'LEGAL_ASSET', 'license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRIMINAL_RECORD', 'GLINER', true, 0.3, 'Criminal Record', 'Criminal record information', 'LEGAL_ASSET', 'criminal record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VEHICLE_REGISTRATION', 'GLINER', true, 0.3, 'Vehicle Registration', 'Vehicle registration numbers', 'LEGAL_ASSET', 'vehicle registration number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE', 'GLINER', true, 0.3, 'License Plate', 'License plate numbers', 'LEGAL_ASSET', 'license plate number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VIN', 'GLINER', true, 0.3, 'VIN', 'Vehicle identification numbers', 'LEGAL_ASSET', 'vehicle identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INSURANCE_POLICY_NUMBER', 'GLINER', true, 0.3, 'Insurance Policy', 'Insurance policy numbers', 'LEGAL_ASSET', 'insurance policy number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- GLINER PII Summary (CONSOLIDATED):
-- Total: 44 PII types across 7 categories (down from 107/13)
-- Multi-pass speedup: ~2x (7 passes instead of 13)
--
-- - IDENTITY: 9 types (core personal identity)
-- - CONTACT: 4 types (how to reach someone)
-- - DIGITAL: 3 types (online identifiers)
-- - FINANCIAL: 6 types (money/banking)
-- - MEDICAL: 6 types (health info)
-- - IT_CREDENTIALS: 9 types (technical/secrets)
-- - LEGAL_ASSET: 7 types (legal + property)
--
-- Removed categories: PROFESSIONAL, LOCATION, RESOURCE, TEMPORAL, BIOMETRIC
-- These were either low-value, high false positive, or consolidated elsewhere
-- ============================================================================

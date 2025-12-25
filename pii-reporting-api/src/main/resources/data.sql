-- ============================================================================
-- PII Detection Configuration Data
-- ============================================================================
-- This file contains INSERT statements for initializing PII detection configuration
-- Tables are created by JPA/Hibernate (ddl-auto=update)
-- This script runs after schema creation (spring.jpa.defer-datasource-initialization=true)
-- ============================================================================

-- ============================================================================
-- PII Detection Global Config (Singleton with id=1)
-- ============================================================================

INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, false, false, 0.30, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- ============================================================================
-- GLINER PII TYPES - Multi-Pass Detection Categories
-- ============================================================================
-- ============================================================================
--
-- Design principles:
-- 1. Each PII type belongs to exactly ONE category (no duplicates)
-- 2. Labels are natural language phrases optimized for GLiNER detection
-- 3. All types use threshold 0.3 for consistent detection
-- 4. Categories are designed for parallel multi-pass detection
--
-- Categories (13 total):
-- 1. IDENTITY - Core personal identity (14 types)
-- 2. CONTACT - How to reach a person (11 types)
-- 3. DIGITAL_IDENTITY - Online/digital identifiers (8 types)
-- 4. FINANCIAL - Banking and payment info (12 types)
-- 5. MEDICAL - Health information (11 types)
-- 6. PROFESSIONAL - Employment and education (10 types)
-- 7. LOCATION - Geographic/precise location (5 types)
-- 8. IT - Technical and credential identifiers (19 types)
-- 9. RESOURCE - Online resources (5 types)
-- 10. TEMPORAL - Dates and time (4 types)
-- 11. BIOMETRIC - Biometric data (5 types)
-- 12. LEGAL - Legal and government (6 types)
-- 13. ASSET - Vehicles and property (5 types)
-- ============================================================================

-- ============================================================================
-- Category 1: IDENTITY - Core personal identity
-- Uniquely identifies a person
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('PERSON_NAME', 'GLINER', true, 0.3, 'Person Name', 'Full or partial names of individuals', 'IDENTITY', 'person name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FIRST_NAME', 'GLINER', true, 0.3, 'First Name', 'Given names', 'IDENTITY', 'first name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LAST_NAME', 'GLINER', true, 0.3, 'Last Name', 'Family names or surnames', 'IDENTITY', 'last name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FULL_NAME', 'GLINER', true, 0.3, 'Full Name', 'Complete names including first and last', 'IDENTITY', 'full name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONAL_ID', 'GLINER', true, 0.3, 'National ID', 'National identity card numbers', 'IDENTITY', 'national identity number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSN', 'GLINER', true, 0.3, 'Social Security Number', 'Social security numbers', 'IDENTITY', 'social security number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSPORT_NUMBER', 'GLINER', true, 0.3, 'Passport Number', 'Passport identification numbers', 'IDENTITY', 'passport number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DRIVER_LICENSE_NUMBER', 'GLINER', true, 0.3, 'Driver License', 'Driver license numbers', 'IDENTITY', 'driver license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ID_CARD_NUMBER', 'GLINER', true, 0.3, 'ID Card Number', 'Identity card numbers', 'IDENTITY', 'identity card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIRTH_DATE', 'GLINER', true, 0.3, 'Birth Date', 'Date of birth', 'IDENTITY', 'date of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PLACE_OF_BIRTH', 'GLINER', true, 0.3, 'Place of Birth', 'Birthplace location', 'IDENTITY', 'place of birth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('GENDER', 'GLINER', true, 0.3, 'Gender', 'Gender identifiers', 'IDENTITY', 'gender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('NATIONALITY', 'GLINER', true, 0.3, 'Nationality', 'Nationality or citizenship', 'IDENTITY', 'nationality', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MARITAL_STATUS', 'GLINER', true, 0.3, 'Marital Status', 'Marital or relationship status', 'IDENTITY', 'marital status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 2: CONTACT - How to reach or locate a person
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMAIL', 'GLINER', true, 0.3, 'Email Address', 'Email addresses', 'CONTACT', 'email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PHONE_NUMBER', 'GLINER', true, 0.3, 'Phone Number', 'Phone and telephone numbers', 'CONTACT', 'phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MOBILE_PHONE', 'GLINER', true, 0.3, 'Mobile Phone', 'Mobile phone numbers', 'CONTACT', 'mobile phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FAX_NUMBER', 'GLINER', true, 0.3, 'Fax Number', 'Fax numbers', 'CONTACT', 'fax number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOME_ADDRESS', 'GLINER', true, 0.3, 'Home Address', 'Residential addresses', 'CONTACT', 'home address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAILING_ADDRESS', 'GLINER', true, 0.3, 'Mailing Address', 'Postal mailing addresses', 'CONTACT', 'mailing address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('POSTAL_CODE', 'GLINER', true, 0.3, 'Postal Code', 'ZIP codes and postal codes', 'CONTACT', 'postal code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CITY', 'GLINER', true, 0.3, 'City', 'City names', 'CONTACT', 'city name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STATE', 'GLINER', true, 0.3, 'State', 'State or province names', 'CONTACT', 'state or province', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COUNTRY', 'GLINER', true, 0.3, 'Country', 'Country names', 'CONTACT', 'country name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PO_BOX', 'GLINER', true, 0.3, 'PO Box', 'Post office box numbers', 'CONTACT', 'po box number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 3: DIGITAL_IDENTITY - Online/digital identifiers
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('USERNAME', 'GLINER', true, 0.3, 'Username', 'Usernames and account names', 'DIGITAL_IDENTITY', 'username', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LOGIN', 'GLINER', true, 0.3, 'Login', 'Login identifiers', 'DIGITAL_IDENTITY', 'login identifier', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ONLINE_HANDLE', 'GLINER', true, 0.3, 'Online Handle', 'Online handles and nicknames', 'DIGITAL_IDENTITY', 'online handle', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIAL_MEDIA_HANDLE', 'GLINER', true, 0.3, 'Social Media Handle', 'Social media usernames', 'DIGITAL_IDENTITY', 'social media handle', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SOCIAL_MEDIA_URL', 'GLINER', true, 0.3, 'Social Media URL', 'Social media profile URLs', 'DIGITAL_IDENTITY', 'social media profile url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('USER_ID', 'GLINER', true, 0.3, 'User ID', 'User identification numbers', 'DIGITAL_IDENTITY', 'user id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCOUNT_ID', 'GLINER', true, 0.3, 'Account ID', 'Account identification numbers', 'DIGITAL_IDENTITY', 'account id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CUSTOMER_ID', 'GLINER', true, 0.3, 'Customer ID', 'Customer identification numbers', 'DIGITAL_IDENTITY', 'customer id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
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
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CREDIT_CARD_NUMBER', 'GLINER', true, 0.3, 'Credit Card Number', 'Credit card numbers', 'FINANCIAL', 'credit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEBIT_CARD_NUMBER', 'GLINER', true, 0.3, 'Debit Card Number', 'Debit card numbers', 'FINANCIAL', 'debit card number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BANK_ACCOUNT_NUMBER', 'GLINER', true, 0.3, 'Bank Account Number', 'Bank account numbers', 'FINANCIAL', 'bank account number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IBAN', 'GLINER', true, 0.3, 'IBAN', 'International Bank Account Numbers', 'FINANCIAL', 'iban', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('BIC_SWIFT', 'GLINER', true, 0.3, 'BIC/SWIFT Code', 'Bank Identifier Codes', 'FINANCIAL', 'swift code', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ROUTING_NUMBER', 'GLINER', true, 0.3, 'Routing Number', 'Bank routing numbers', 'FINANCIAL', 'routing number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TAX_ID', 'GLINER', true, 0.3, 'Tax ID', 'Tax identification numbers', 'FINANCIAL', 'tax identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VAT_NUMBER', 'GLINER', true, 0.3, 'VAT Number', 'Value added tax numbers', 'FINANCIAL', 'vat number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PAYMENT_REFERENCE', 'GLINER', true, 0.3, 'Payment Reference', 'Payment reference numbers', 'FINANCIAL', 'payment reference', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INVOICE_NUMBER', 'GLINER', true, 0.3, 'Invoice Number', 'Invoice numbers', 'FINANCIAL', 'invoice number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SALARY', 'GLINER', true, 0.3, 'Salary', 'Salary and wage information', 'FINANCIAL', 'salary amount', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TRANSACTION_ID', 'GLINER', true, 0.3, 'Transaction ID', 'Transaction identifiers', 'FINANCIAL', 'transaction id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
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
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('AVS_NUMBER', 'GLINER', true, 0.3, 'AVS Number', 'Swiss social security number (AVS/AHV)', 'MEDICAL', 'avs number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PATIENT_ID', 'GLINER', true, 0.3, 'Patient ID', 'Patient identification numbers', 'MEDICAL', 'patient id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_RECORD_NUMBER', 'GLINER', true, 0.3, 'Medical Record Number', 'Medical record numbers', 'MEDICAL', 'medical record number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HEALTH_INSURANCE_NUMBER', 'GLINER', true, 0.3, 'Health Insurance Number', 'Health insurance ID numbers', 'MEDICAL', 'health insurance number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DIAGNOSIS', 'GLINER', true, 0.3, 'Diagnosis', 'Medical diagnoses', 'MEDICAL', 'medical diagnosis', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICAL_CONDITION', 'GLINER', true, 0.3, 'Medical Condition', 'Medical conditions and diseases', 'MEDICAL', 'medical condition', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MEDICATION', 'GLINER', true, 0.3, 'Medication', 'Medication and drug names', 'MEDICAL', 'medication name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PRESCRIPTION', 'GLINER', true, 0.3, 'Prescription', 'Prescription information', 'MEDICAL', 'prescription', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TREATMENT', 'GLINER', true, 0.3, 'Treatment', 'Medical treatment information', 'MEDICAL', 'medical treatment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DOCTOR_NAME', 'GLINER', true, 0.3, 'Doctor Name', 'Names of doctors and physicians', 'MEDICAL', 'doctor name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOSPITAL_NAME', 'GLINER', true, 0.3, 'Hospital Name', 'Names of hospitals and medical facilities', 'MEDICAL', 'hospital name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 6: PROFESSIONAL - Employment and education
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('EMPLOYEE_ID', 'GLINER', true, 0.3, 'Employee ID', 'Employee identification numbers', 'PROFESSIONAL', 'employee id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('EMPLOYEE_NAME', 'GLINER', true, 0.3, 'Employee Name', 'Names of employees', 'PROFESSIONAL', 'employee name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('JOB_TITLE', 'GLINER', true, 0.3, 'Job Title', 'Job titles and positions', 'PROFESSIONAL', 'job title', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEPARTMENT', 'GLINER', true, 0.3, 'Department', 'Department names', 'PROFESSIONAL', 'department name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COMPANY_NAME', 'GLINER', true, 0.3, 'Company Name', 'Names of companies and organizations', 'PROFESSIONAL', 'company name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('WORK_EMAIL', 'GLINER', true, 0.3, 'Work Email', 'Work email addresses', 'PROFESSIONAL', 'work email address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('WORK_PHONE', 'GLINER', true, 0.3, 'Work Phone', 'Work phone numbers', 'PROFESSIONAL', 'work phone number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('STUDENT_ID', 'GLINER', true, 0.3, 'Student ID', 'Student identification numbers', 'PROFESSIONAL', 'student id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SCHOOL_NAME', 'GLINER', true, 0.3, 'School Name', 'Names of schools', 'PROFESSIONAL', 'school name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('UNIVERSITY_NAME', 'GLINER', true, 0.3, 'University Name', 'Names of universities', 'PROFESSIONAL', 'university name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 7: LOCATION - Precise or sensitive location
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('GPS_COORDINATES', 'GLINER', true, 0.3, 'GPS Coordinates', 'GPS latitude and longitude coordinates', 'LOCATION', 'gps coordinates', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOME_LOCATION', 'GLINER', true, 0.3, 'Home Location', 'Home location information', 'LOCATION', 'home location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('WORK_LOCATION', 'GLINER', true, 0.3, 'Work Location', 'Work location information', 'LOCATION', 'work location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CURRENT_LOCATION', 'GLINER', true, 0.3, 'Current Location', 'Current location information', 'LOCATION', 'current location', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ADDRESS', 'GLINER', true, 0.3, 'Street Address', 'Street addresses', 'LOCATION', 'street address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 8: IT - Technical identifiers and credentials
-- Merged IT + CREDENTIALS as requested
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    -- Technical identifiers
    ('IP_ADDRESS', 'GLINER', true, 0.3, 'IP Address', 'IPv4 and IPv6 addresses', 'IT', 'ip address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('MAC_ADDRESS', 'GLINER', true, 0.3, 'MAC Address', 'Network MAC addresses', 'IT', 'mac address', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('HOSTNAME', 'GLINER', true, 0.3, 'Hostname', 'Computer hostnames', 'IT', 'hostname', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DEVICE_ID', 'GLINER', true, 0.3, 'Device ID', 'Device identification numbers', 'IT', 'device id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SERIAL_NUMBER', 'GLINER', true, 0.3, 'Serial Number', 'Hardware serial numbers', 'IT', 'serial number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IMEI', 'GLINER', true, 0.3, 'IMEI', 'Mobile device IMEI numbers', 'IT', 'imei number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IMSI', 'GLINER', true, 0.3, 'IMSI', 'Mobile subscriber identification numbers', 'IT', 'imsi number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SESSION_ID', 'GLINER', true, 0.3, 'Session ID', 'Session identifiers', 'IT', 'session id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COOKIE_ID', 'GLINER', true, 0.3, 'Cookie ID', 'Cookie identifiers', 'IT', 'cookie id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('USER_AGENT', 'GLINER', true, 0.3, 'User Agent', 'Browser user agent strings', 'IT', 'user agent string', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    -- Credentials (merged into IT)
    ('PASSWORD', 'GLINER', true, 0.3, 'Password', 'Passwords and passphrases', 'IT', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PASSWORD_HASH', 'GLINER', true, 0.3, 'Password Hash', 'Hashed passwords', 'IT', 'password hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_KEY', 'GLINER', true, 0.3, 'API Key', 'API keys and access keys', 'IT', 'api key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ACCESS_TOKEN', 'GLINER', true, 0.3, 'Access Token', 'Access tokens and bearer tokens', 'IT', 'access token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('REFRESH_TOKEN', 'GLINER', true, 0.3, 'Refresh Token', 'OAuth refresh tokens', 'IT', 'refresh token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SECRET_KEY', 'GLINER', true, 0.3, 'Secret Key', 'Secret keys and private keys', 'IT', 'secret key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PRIVATE_KEY', 'GLINER', true, 0.3, 'Private Key', 'Cryptographic private keys', 'IT', 'private key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('SSH_KEY', 'GLINER', true, 0.3, 'SSH Key', 'SSH keys', 'IT', 'ssh key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('OAUTH_TOKEN', 'GLINER', true, 0.3, 'OAuth Token', 'OAuth tokens', 'IT', 'oauth token', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 9: RESOURCE - Online resources
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('URL', 'GLINER', true, 0.3, 'URL', 'Web URLs and links', 'RESOURCE', 'url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PROFILE_URL', 'GLINER', true, 0.3, 'Profile URL', 'Profile page URLs', 'RESOURCE', 'profile url', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERSONAL_WEBSITE', 'GLINER', true, 0.3, 'Personal Website', 'Personal website URLs', 'RESOURCE', 'personal website', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('API_ENDPOINT', 'GLINER', true, 0.3, 'API Endpoint', 'API endpoint URLs', 'RESOURCE', 'api endpoint', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FILE_PATH', 'GLINER', true, 0.3, 'File Path', 'File system paths', 'RESOURCE', 'file path', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 10: TEMPORAL - Dates and time
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('DATE', 'GLINER', true, 0.3, 'Date', 'Date values', 'TEMPORAL', 'date', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TIME', 'GLINER', true, 0.3, 'Time', 'Time values', 'TEMPORAL', 'time', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('TIMESTAMP', 'GLINER', true, 0.3, 'Timestamp', 'Date and time timestamps', 'TEMPORAL', 'timestamp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('AGE', 'GLINER', true, 0.3, 'Age', 'Age values', 'TEMPORAL', 'age', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 11: BIOMETRIC - Biometric data
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('FINGERPRINT', 'GLINER', true, 0.3, 'Fingerprint', 'Fingerprint data', 'BIOMETRIC', 'fingerprint data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('FACIAL_RECOGNITION_DATA', 'GLINER', true, 0.3, 'Facial Recognition', 'Facial recognition data', 'BIOMETRIC', 'facial recognition data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IRIS_SCAN', 'GLINER', true, 0.3, 'Iris Scan', 'Iris scan data', 'BIOMETRIC', 'iris scan data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VOICE_PRINT', 'GLINER', true, 0.3, 'Voice Print', 'Voice print data', 'BIOMETRIC', 'voice print', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('DNA_SEQUENCE', 'GLINER', true, 0.3, 'DNA Sequence', 'DNA sequence data', 'BIOMETRIC', 'dna sequence', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 12: LEGAL - Legal and government
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('CASE_NUMBER', 'GLINER', true, 0.3, 'Case Number', 'Legal case numbers', 'LEGAL', 'case number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('COURT_NAME', 'GLINER', true, 0.3, 'Court Name', 'Names of courts', 'LEGAL', 'court name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('CRIMINAL_RECORD', 'GLINER', true, 0.3, 'Criminal Record', 'Criminal record information', 'LEGAL', 'criminal record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_NUMBER', 'GLINER', true, 0.3, 'License Number', 'License numbers', 'LEGAL', 'license number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PERMIT_NUMBER', 'GLINER', true, 0.3, 'Permit Number', 'Permit numbers', 'LEGAL', 'permit number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('IMMIGRATION_STATUS', 'GLINER', true, 0.3, 'Immigration Status', 'Immigration status information', 'LEGAL', 'immigration status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Category 13: ASSET - Vehicles and property
-- ============================================================================
INSERT INTO pii_type_config
(pii_type, detector, enabled, threshold, display_name, description, category, detector_label, created_at, updated_at, updated_by)
VALUES
    ('VEHICLE_REGISTRATION', 'GLINER', true, 0.3, 'Vehicle Registration', 'Vehicle registration numbers', 'ASSET', 'vehicle registration number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('LICENSE_PLATE', 'GLINER', true, 0.3, 'License Plate', 'License plate numbers', 'ASSET', 'license plate number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('VIN', 'GLINER', true, 0.3, 'VIN', 'Vehicle identification numbers', 'ASSET', 'vehicle identification number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('PROPERTY_ID', 'GLINER', true, 0.3, 'Property ID', 'Property identification numbers', 'ASSET', 'property id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('INSURANCE_POLICY_NUMBER', 'GLINER', true, 0.3, 'Insurance Policy', 'Insurance policy numbers', 'ASSET', 'insurance policy number', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (pii_type, detector) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    threshold = EXCLUDED.threshold,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    detector_label = EXCLUDED.detector_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- GLINER PII Summary:
-- Total: 114 PII types across 13 categories
-- - IDENTITY: 14 types
-- - CONTACT: 11 types
-- - DIGITAL_IDENTITY: 8 types
-- - FINANCIAL: 12 types
-- - MEDICAL: 11 types
-- - PROFESSIONAL: 10 types
-- - LOCATION: 5 types
-- - IT: 19 types (merged IT + CREDENTIALS)
-- - RESOURCE: 5 types
-- - TEMPORAL: 4 types
-- - BIOMETRIC: 5 types
-- - LEGAL: 6 types
-- - ASSET: 5 types
-- ============================================================================

"""
Enumeration of supported PII types.

This module defines all Personally Identifiable Information (PII) types
that can be detected by the system, with French labels for user interfaces.
"""

from enum import Enum


class PIIType(Enum):
    """Enumeration of supported PII types with French labels."""

    # Existing types
    ACCOUNTNUM = "Numéro de compte"
    BUILDINGNUM = "Numéro de bâtiment"
    CITY = "Ville"
    CREDITCARDNUMBER = "Numéro de carte de crédit"
    DATEOFBIRTH = "Date de naissance"
    DRIVERLICENSENUM = "Numéro de permis de conduire"
    EMAIL = "Email"
    GIVENNAME = "Prénom"
    IDCARDNUM = "Numéro de carte d'identité"
    PASSWORD = "Mot de passe"
    SOCIALNUM = "Numéro de sécurité sociale"
    STREET = "Rue"
    SURNAME = "Nom de famille"
    TAXNUM = "Numéro fiscal"
    TELEPHONENUM = "Numéro de téléphone"
    USERNAME = "Nom d'utilisateur"
    ZIPCODE = "Code postal"
    
    # Additional types for Presidio and Regex detectors
    PHONE = "Téléphone"
    URL = "URL"
    CREDIT_CARD = "Carte bancaire"
    IBAN = "IBAN"
    CRYPTO_WALLET = "Portefeuille crypto"
    SSN = "Numéro de sécurité sociale"
    NHS_NUMBER = "Numéro NHS"
    NRIC = "NRIC"
    ABN = "ABN"
    ACN = "ACN"
    TFN = "TFN"
    MEDICARE = "Medicare"
    IP_ADDRESS = "Adresse IP"
    MAC_ADDRESS = "Adresse MAC"
    PERSON_NAME = "Nom de personne"
    LOCATION = "Lieu"
    DATE = "Date"
    AGE = "Âge"
    MEDICAL_LICENSE = "Licence médicale"
    PASSPORT = "Passeport"
    DRIVER_LICENSE = "Permis de conduire"
    ITIN = "ITIN"
    NRP = "Groupe nationalité/religion/politique"
    
    # USA specific
    US_BANK_NUMBER = "Numéro de compte bancaire US"
    US_DRIVER_LICENSE = "Permis de conduire US"
    US_ITIN = "ITIN US"
    US_PASSPORT = "Passeport US"
    US_SSN = "SSN US"
    
    # UK specific
    UK_NHS = "Numéro NHS UK"
    UK_NINO = "NINO UK"
    
    # Spain specific
    ES_NIF = "NIF espagnol"
    ES_NIE = "NIE espagnol"
    
    # Italy specific
    IT_FISCAL_CODE = "Code fiscal italien"
    IT_DRIVER_LICENSE = "Permis de conduire italien"
    IT_VAT_CODE = "Code TVA italien"
    IT_PASSPORT = "Passeport italien"
    IT_IDENTITY_CARD = "Carte d'identité italienne"
    
    # Poland specific
    PL_PESEL = "PESEL polonais"
    
    # Singapore specific
    SG_NRIC_FIN = "NRIC/FIN singapourien"
    SG_UEN = "UEN singapourien"
    
    # Australia specific
    AU_ABN = "ABN australien"
    AU_ACN = "ACN australien"
    AU_TFN = "TFN australien"
    AU_MEDICARE = "Medicare australien"
    
    # India specific
    IN_PAN = "PAN indien"
    IN_AADHAAR = "Aadhaar indien"
    IN_VEHICLE_REGISTRATION = "Immatriculation véhicule indien"
    IN_VOTER = "Carte d'électeur indienne"
    IN_PASSPORT = "Passeport indien"
    
    # Finland specific
    FI_PERSONAL_IDENTITY_CODE = "Code d'identité finlandais"
    
    # Korea specific
    KR_RRN = "RRN coréen"
    
    # Thailand specific
    TH_TNIN = "TNIN thaïlandais"
    
    # Technical tokens
    API_KEY = "Clé API"
    JWT_TOKEN = "Token JWT"
    GITHUB_TOKEN = "Token GitHub"
    AWS_KEY = "Clé AWS"
    UNKNOWN = "Inconnu"

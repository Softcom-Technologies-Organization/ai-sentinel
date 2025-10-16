"""
Enumeration of supported PII types.

This module defines all Personally Identifiable Information (PII) types
that can be detected by the system, with French labels for user interfaces.
"""

from enum import Enum


class PIIType(Enum):
    """Enumeration of supported PII types with French labels."""

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

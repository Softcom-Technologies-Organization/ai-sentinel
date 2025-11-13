"""
Tests pour vérifier que tous les nouveaux types PII Presidio sont bien configurés.
"""

import pytest
from pii_detector.domain.entity.pii_type import PIIType
from pii_detector.infrastructure.detector.presidio_detector import PRESIDIO_TO_PII_TYPE_MAP


class TestPresidioNewPiiTypes:
    """Tests pour vérifier les nouveaux types PII ajoutés."""
    
    def test_should_have_all_global_types_in_mapping(self):
        """Vérifie que tous les types globaux sont dans le mapping."""
        global_types = [
            "EMAIL_ADDRESS", "PHONE_NUMBER", "URL", "CREDIT_CARD", 
            "IBAN_CODE", "CRYPTO", "IP_ADDRESS", "PERSON", 
            "LOCATION", "DATE_TIME", "NRP", "MEDICAL_LICENSE"
        ]
        
        for entity_type in global_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type global {entity_type} manquant dans le mapping"
    
    def test_should_have_all_usa_types_in_mapping(self):
        """Vérifie que tous les types USA sont dans le mapping."""
        usa_types = [
            "US_SSN", "US_BANK_NUMBER", "US_DRIVER_LICENSE", 
            "US_ITIN", "US_PASSPORT"
        ]
        
        for entity_type in usa_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type USA {entity_type} manquant dans le mapping"
    
    def test_should_have_all_uk_types_in_mapping(self):
        """Vérifie que tous les types UK sont dans le mapping."""
        uk_types = ["UK_NHS", "UK_NINO"]
        
        for entity_type in uk_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type UK {entity_type} manquant dans le mapping"
    
    def test_should_have_all_spain_types_in_mapping(self):
        """Vérifie que tous les types espagnols sont dans le mapping."""
        spain_types = ["ES_NIF", "ES_NIE"]
        
        for entity_type in spain_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Espagne {entity_type} manquant dans le mapping"
    
    def test_should_have_all_italy_types_in_mapping(self):
        """Vérifie que tous les types italiens sont dans le mapping."""
        italy_types = [
            "IT_FISCAL_CODE", "IT_DRIVER_LICENSE", "IT_VAT_CODE",
            "IT_PASSPORT", "IT_IDENTITY_CARD"
        ]
        
        for entity_type in italy_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Italie {entity_type} manquant dans le mapping"
    
    def test_should_have_all_poland_types_in_mapping(self):
        """Vérifie que tous les types polonais sont dans le mapping."""
        poland_types = ["PL_PESEL"]
        
        for entity_type in poland_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Pologne {entity_type} manquant dans le mapping"
    
    def test_should_have_all_singapore_types_in_mapping(self):
        """Vérifie que tous les types singapouriens sont dans le mapping."""
        singapore_types = ["SG_NRIC_FIN", "SG_UEN"]
        
        for entity_type in singapore_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Singapour {entity_type} manquant dans le mapping"
    
    def test_should_have_all_australia_types_in_mapping(self):
        """Vérifie que tous les types australiens sont dans le mapping."""
        australia_types = ["AU_ABN", "AU_ACN", "AU_TFN", "AU_MEDICARE"]
        
        for entity_type in australia_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Australie {entity_type} manquant dans le mapping"
    
    def test_should_have_all_india_types_in_mapping(self):
        """Vérifie que tous les types indiens sont dans le mapping."""
        india_types = [
            "IN_PAN", "IN_AADHAAR", "IN_VEHICLE_REGISTRATION",
            "IN_VOTER", "IN_PASSPORT"
        ]
        
        for entity_type in india_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Inde {entity_type} manquant dans le mapping"
    
    def test_should_have_all_finland_types_in_mapping(self):
        """Vérifie que tous les types finlandais sont dans le mapping."""
        finland_types = ["FI_PERSONAL_IDENTITY_CODE"]
        
        for entity_type in finland_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Finlande {entity_type} manquant dans le mapping"
    
    def test_should_have_all_korea_types_in_mapping(self):
        """Vérifie que tous les types coréens sont dans le mapping."""
        korea_types = ["KR_RRN"]
        
        for entity_type in korea_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Corée {entity_type} manquant dans le mapping"
    
    def test_should_have_all_thailand_types_in_mapping(self):
        """Vérifie que tous les types thaïlandais sont dans le mapping."""
        thailand_types = ["TH_TNIN"]
        
        for entity_type in thailand_types:
            assert entity_type in PRESIDIO_TO_PII_TYPE_MAP, \
                f"Type Thaïlande {entity_type} manquant dans le mapping"
    
    def test_should_map_to_valid_pii_types(self):
        """Vérifie que tous les mappings pointent vers des PIIType valides."""
        for presidio_type, pii_type in PRESIDIO_TO_PII_TYPE_MAP.items():
            assert isinstance(pii_type, PIIType), \
                f"Le mapping {presidio_type} ne pointe pas vers un PIIType valide"
    
    def test_should_have_pii_type_for_all_mapped_presidio_types(self):
        """Vérifie que tous les types Presidio mappés ont un PIIType correspondant."""
        for presidio_type, pii_type in PRESIDIO_TO_PII_TYPE_MAP.items():
            assert pii_type != PIIType.UNKNOWN, \
                f"Le type Presidio {presidio_type} est mappé vers UNKNOWN"
    
    def test_should_have_all_country_specific_types_in_pii_type_enum(self):
        """Vérifie que tous les types spécifiques par pays existent dans PIIType."""
        country_types = [
            # USA
            "US_BANK_NUMBER", "US_DRIVER_LICENSE", "US_ITIN", 
            "US_PASSPORT", "US_SSN",
            # UK
            "UK_NHS", "UK_NINO",
            # Spain
            "ES_NIF", "ES_NIE",
            # Italy
            "IT_FISCAL_CODE", "IT_DRIVER_LICENSE", "IT_VAT_CODE",
            "IT_PASSPORT", "IT_IDENTITY_CARD",
            # Poland
            "PL_PESEL",
            # Singapore
            "SG_NRIC_FIN", "SG_UEN",
            # Australia
            "AU_ABN", "AU_ACN", "AU_TFN", "AU_MEDICARE",
            # India
            "IN_PAN", "IN_AADHAAR", "IN_VEHICLE_REGISTRATION",
            "IN_VOTER", "IN_PASSPORT",
            # Finland
            "FI_PERSONAL_IDENTITY_CODE",
            # Korea
            "KR_RRN",
            # Thailand
            "TH_TNIN"
        ]
        
        for type_name in country_types:
            assert hasattr(PIIType, type_name), \
                f"PIIType.{type_name} n'existe pas dans l'énumération"
    
    def test_should_have_nrp_type(self):
        """Vérifie que le type NRP existe."""
        assert hasattr(PIIType, "NRP"), "PIIType.NRP n'existe pas"
        assert "NRP" in PRESIDIO_TO_PII_TYPE_MAP, "NRP manquant dans le mapping"
        assert PRESIDIO_TO_PII_TYPE_MAP["NRP"] == PIIType.NRP, \
            "NRP n'est pas correctement mappé"

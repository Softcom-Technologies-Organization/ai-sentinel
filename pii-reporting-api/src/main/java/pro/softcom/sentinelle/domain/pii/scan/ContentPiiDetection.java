package pro.softcom.sentinelle.domain.pii.scan;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Représente l'analyse du contenu d'une page Confluence.
 */
@Builder
public record ContentPiiDetection(
    String pageId,
    String pageTitle,
    String spaceKey,
    LocalDateTime analysisDate,
    List<SensitiveData> sensitiveDataFound,
    Map<String, Integer> statistics
) {
    
    // Sonar S1192: avoid duplicate literal for phone number label
    public static final String PHONE_NUMBER_LABEL = "Numéro de téléphone";

    /**
     * Types de données sensibles détectables
     */
    @Getter
    public enum DataType {
        EMAIL("Email", "Adresse email détectée"),
        PHONE("Téléphone", PHONE_NUMBER_LABEL),
        PHONE_NUMBER(PHONE_NUMBER_LABEL, "Numéro de téléphone formaté"),
        TELEPHONENUM(PHONE_NUMBER_LABEL, "Numéro de téléphone détecté"),
        PERSON("Personne", "Nom de personne détecté"),
        NAME("Nom", "Nom propre détecté"),
        SURNAME("Nom de famille", "Nom de famille détecté"),
        LOCATION("Localisation", "Lieu géographique"),
        ADDRESS("Adresse", "Adresse postale"),
        STREET("Rue", "Nom de rue détecté"),
        CITY("Ville", "Nom de ville détecté"),
        ZIPCODE("Code postal", "Code postal détecté"),
        BUILDINGNUM("Numéro de bâtiment", "Numéro de bâtiment détecté"),
        CREDIT_CARD("Carte de crédit", "Numéro de carte de crédit"),
        BANK_ACCOUNT("Compte bancaire", "Numéro de compte bancaire"),
        SSN("Numéro AVS", "Numéro de sécurité sociale"),
        ID_CARD("Carte d'identité", "Numéro de carte d'identité"),
        PASSWORD("Mot de passe", "Mot de passe détecté"),
        API_KEY("Clé API", "Clé d'API détectée"),
        TOKEN("Jeton", "Jeton d'authentification"),
        URL("URL", "Lien web détecté"),
        IP_ADDRESS("Adresse IP", "Adresse IP détectée"),
        UNKNOWN("Inconnu", "Type de données non identifié"),
        // Legacy values for backward compatibility
        AVS("AVS", "Numéro AVS (legacy)"),
        SECURITY("Sécurité", "Information de sécurité (legacy)"),
        ATTACHMENT("Pièce jointe", "Lien vers une pièce jointe (legacy)"), USERNAME("Nom d'utilisateur", "Nom utilisé" +
                " par un utilisateur pour s'identifier");
        
        private final String label;
        private final String description;
        
        DataType(String label, String description) {
            this.label = label;
            this.description = description;
        }

    }
    
    /**
     * Représente une donnée sensible trouvée
     */
    public record SensitiveData(
        DataType type,
        String value,
        String context,
        int position,
        int end,
        Double score,
        String selector
    ) {
        /**
         * Masque la valeur sensible pour l'affichage
         */
        public String getMaskedValue() {
            return switch (type) {
                case EMAIL -> maskEmail(value);
                case PHONE, PHONE_NUMBER, TELEPHONENUM -> maskPhone(value);
                case SSN, AVS -> maskAVS(value);
                case CREDIT_CARD -> maskCreditCard(value);
                case BANK_ACCOUNT -> maskBankAccount(value);
                case PASSWORD, API_KEY, TOKEN, SECURITY -> "***MASKED***";
                case PERSON, NAME, SURNAME -> maskPersonName(value);
                case ID_CARD -> maskIdCard(value);
                case IP_ADDRESS -> maskIpAddress(value);
                case URL, ATTACHMENT -> maskUrl(value);
                case LOCATION, ADDRESS, STREET, CITY, ZIPCODE, BUILDINGNUM -> maskAddress(value);
                case UNKNOWN -> "***UNKNOWN***";
                case USERNAME -> maskUsername(value);
            };
        }
        
        private String maskEmail(String email) {
            int atIndex = email.indexOf('@');
            if (atIndex > 2) {
                return email.substring(0, 2) + "***" + email.substring(atIndex);
            }
            return "***@***";
        }
        
        private String maskPhone(String phone) {
            if (phone.length() > 6) {
                return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 2);
            }
            return "***";
        }
        
        private String maskAVS(String avs) {
            if (avs.length() > 8) {
                return "756.****.****.XX";
            }
            return "***";
        }
        
        private String maskCreditCard(String creditCard) {
            if (creditCard.length() > 8) {
                return "****-****-****-" + creditCard.substring(creditCard.length() - 4);
            }
            return "****-****-****-****";
        }
        
        private String maskBankAccount(String bankAccount) {
            if (bankAccount.length() > 6) {
                return "***" + bankAccount.substring(bankAccount.length() - 3);
            }
            return "***";
        }
        
        private String maskPersonName(String name) {
            if (name.length() > 3) {
                return name.charAt(0) + "***" + name.substring(name.length() - 1);
            }
            return "***";
        }
        
        private String maskIdCard(String idCard) {
            if (idCard.length() > 6) {
                return idCard.substring(0, 2) + "***" + idCard.substring(idCard.length() - 2);
            }
            return "***";
        }
        
        private String maskIpAddress(String ipAddress) {
            String[] parts = ipAddress.split("\\.");
            if (parts.length == 4) {
                return parts[0] + ".***.***.***";
            }
            return "***.***.***";
        }
        
        private String maskUrl(String url) {
            if (url.contains("://")) {
                String[] parts = url.split("://", 2);
                return parts[0] + "://***";
            }
            return "***";
        }
        
        private String maskAddress(String address) {
            if (address.length() > 10) {
                return address.substring(0, 3) + "***" + address.substring(address.length() - 3);
            }
            return "***";
        }
        
        private String maskUsername(String username) {
            if (username.length() > 4) {
                return username.substring(0, 2) + "***" + username.substring(username.length() - 1);
            }
            return "***";
        }
    }
    
    /**
     * Calcule le score de risque basé sur les données trouvées
     */
    public int getRiskScore() {
        return sensitiveDataFound.stream()
            .mapToInt(data -> switch (data.type()) {
                // High risk - Financial and identification data
                case SSN, CREDIT_CARD, BANK_ACCOUNT, ID_CARD, AVS -> 10;
                // High risk - Security credentials
                case PASSWORD, API_KEY, TOKEN, SECURITY -> 10;
                // Medium risk - Personal contact information
                case EMAIL, PHONE, PHONE_NUMBER, TELEPHONENUM -> 5;
                // Medium risk - Personal identification
                case PERSON, NAME, SURNAME -> 5;
                // Low risk - Location and address information
                case LOCATION, ADDRESS, STREET, CITY, ZIPCODE, BUILDINGNUM -> 3;
                // Low risk - Network and attachment information
                case URL, IP_ADDRESS, ATTACHMENT -> 2;
                // Minimal risk - Unknown types
                case UNKNOWN -> 1;
                // Medium risk - User identification
                case USERNAME -> 5;
            })
            .sum();
    }
    
    /**
     * Détermine le niveau de risque
     */
    public String getRiskLevel() {
        int score = getRiskScore();
        if (score >= 50) return "CRITIQUE";
        if (score >= 30) return "ÉLEVÉ";
        if (score >= 15) return "MOYEN";
        if (score > 0) return "FAIBLE";
        return "AUCUN";
    }
}

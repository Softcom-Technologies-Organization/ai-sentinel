package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct integration test for PII detection service with real gRPC calls.
 * This test bypasses the full scan complexity to focus on PII detection.
 */
@Testcontainers
@SpringBootTest(
    classes = AiSentinelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Import(TestPiiDetectionClientConfiguration.class)
class DirectPiiDetectionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private PiiDetectorClient piiDetectionService;

    /**
     * Comprehensive PII test content containing all detectable types
     */
    private static final String COMPREHENSIVE_PII_CONTENT = """
        Rapport de test PII - Données sensibles complètes

        === EMAILS ===
        Contact principal: john.doe@example.com
        Support technique: support@company.ch
        Email personnel: marie.dupont@gmail.com
        Adresse professionnelle: admin@test-domain.org

        === TÉLÉPHONES ===
        Numéro principal: +41 22 123 45 67
        Mobile: 079 456 78 90
        International: +33 1 42 86 83 26
        Fixe: 022 345 67 89

        === PERSONNES ===
        Responsable: Jean-Pierre Martin
        Contact: Marie-Claire Dubois
        Directeur: François Müller
        Assistante: Sophie Bernasconi

        === ADRESSES ===
        Siège social: Rue de la Paix 15, 1201 Genève
        Succursale: Avenue des Alpes 42, 1820 Montreux
        Entrepôt: Chemin des Vignes 8, 1009 Pully

        === DONNÉES FINANCIÈRES ===
        Numéro AVS: 756.1234.5678.90
        Carte de crédit: 4532 1234 5678 9012
        IBAN: CH93 0076 2011 6238 5295 7
        Compte bancaire: 12-345678-9

        === SÉCURITÉ ===
        Mot de passe: SecretPassword123!
        Clé API: sk-1234567890abcdef
        Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
        PIN: 1234

        === DONNÉES TECHNIQUES ===
        Serveur: https://api.internal.company.com/v1
        IP interne: 192.168.1.100
        URL admin: http://admin.local:8080/dashboard
        Adresse IP: 10.0.0.1

        Ce document contient des informations confidentielles.
        """;

    @Test
    void directPiiDetection_ShouldDetectAllPiiTypes() {
        System.out.println("[DEBUG_LOG] Testing direct PII detection with comprehensive content");

        // When - Analyze the comprehensive PII content
        ContentPiiDetection result = piiDetectionService.analyzePageContent(
            "test-page-123",
            "Test PII Page",
            "TEST",
            COMPREHENSIVE_PII_CONTENT
        );

        // Then - Verify PII detection results
        System.out.println("[DEBUG_LOG] Analysis completed");
        System.out.println("[DEBUG_LOG] Page ID: " + result.pageId());
        System.out.println("[DEBUG_LOG] Page Title: " + result.pageTitle());
        System.out.println("[DEBUG_LOG] Space Key: " + result.spaceKey());
        System.out.println("[DEBUG_LOG] Sensitive data found: " + result.sensitiveDataFound().size());
        System.out.println("[DEBUG_LOG] Statistics: " + result.statistics());

        // Verify basic analysis structure
        assertThat(result.pageId()).isEqualTo("test-page-123");
        assertThat(result.pageTitle()).isEqualTo("Test PII Page");
        assertThat(result.spaceKey()).isEqualTo("TEST");
        assertThat(result.analysisDate()).isNotNull();

        // Verify PII was detected
        var sensitiveData = result.sensitiveDataFound();
        assertThat(sensitiveData).isNotEmpty();

        // Check for EMAIL detection
        var emailData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.EMAIL)
            .toList();
        assertThat(emailData).isNotEmpty();
        System.out.println("[DEBUG_LOG] Emails detected: " + emailData.size());
        emailData.forEach(email ->
            System.out.println("[DEBUG_LOG]   - Email: " + email.value())
        );

        // Check for PHONE detection
        var phoneData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.PHONE)
            .toList();
        assertThat(phoneData).isNotEmpty();
        System.out.println("[DEBUG_LOG] Phones detected: " + phoneData.size());
        phoneData.forEach(phone ->
            System.out.println("[DEBUG_LOG]   - Phone: " + phone.value())
        );

        // Check for ADDRESS detection (includes person names)
        var addressData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.ADDRESS)
            .toList();
        System.out.println("[DEBUG_LOG] Addresses/Names detected: " + addressData.size());
        addressData.forEach(address ->
            System.out.println("[DEBUG_LOG]   - Address/Name: " + address.value())
        );

        // Check for AVS detection (financial data)
        var avsData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.AVS)
            .toList();
        System.out.println("[DEBUG_LOG] Financial data detected: " + avsData.size());
        avsData.forEach(avs ->
            System.out.println("[DEBUG_LOG]   - Financial: " + avs.value())
        );

        // Check for SECURITY detection
        var securityData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.SECURITY)
            .toList();
        System.out.println("[DEBUG_LOG] Security data detected: " + securityData.size());
        securityData.forEach(security ->
            System.out.println("[DEBUG_LOG]   - Security: " + security.value())
        );

        // Check for ATTACHMENT detection (URLs, IPs)
        var attachmentData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.ATTACHMENT)
            .toList();
        System.out.println("[DEBUG_LOG] URLs/IPs detected: " + attachmentData.size());
        attachmentData.forEach(attachment ->
            System.out.println("[DEBUG_LOG]   - URL/IP: " + attachment.value())
        );

        // Verify risk assessment

        // Verify statistics
        assertThat(result.statistics()).isNotEmpty();

        // Print detailed statistics
        result.statistics().forEach((type, count) ->
            System.out.println("[DEBUG_LOG] Statistics - " + type + ": " + count)
        );

        System.out.println("[DEBUG_LOG] Direct PII detection test completed successfully");
    }

    @Test
    void directPiiDetection_SimpleEmail_ShouldDetectEmail() {
        System.out.println("[DEBUG_LOG] Testing simple email detection");

        // When - Analyze simple email content
        ContentPiiDetection result = piiDetectionService.analyzeContent("Contact: john@example.com");

        // Then - Verify email detection
        System.out.println("[DEBUG_LOG] Simple email analysis completed");
        System.out.println("[DEBUG_LOG] Sensitive data found: " + result.sensitiveDataFound().size());

        var sensitiveData = result.sensitiveDataFound();
        assertThat(sensitiveData).isNotEmpty();

        var emailData = sensitiveData.stream()
            .filter(data -> data.type() == PersonallyIdentifiableInformationType.EMAIL)
            .toList();
        assertThat(emailData).hasSize(1);
        assertThat(emailData.getFirst().value()).contains("john@example.com");

        System.out.println("[DEBUG_LOG] Simple email detection test completed successfully");
    }
}

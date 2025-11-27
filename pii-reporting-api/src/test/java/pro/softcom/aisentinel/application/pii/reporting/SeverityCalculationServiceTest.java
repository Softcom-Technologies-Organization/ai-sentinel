package pro.softcom.aisentinel.application.pii.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.HIGH;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.LOW;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.MEDIUM;

import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

/**
 * Unit tests for {@link SeverityCalculationService}. Verifies severity classification rules for all
 * 50+ PII types.
 */
@DisplayName("SeverityCalculationService")
class SeverityCalculationServiceTest {

    private SeverityCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SeverityCalculationService();
    }

    @Nested
    @DisplayName("Calculate Severity - HIGH")
    class HighSeverityTests {

        @ParameterizedTest(name = "{0} should be HIGH severity")
        @CsvSource({
            "PASSWORD",
            "ACCOUNTNUM",
            "API_KEY",
            "GITHUB_TOKEN",
            "AWS_ACCESS_KEY",
            "JWT_TOKEN",
            "CREDITCARDNUMBER",
            "IBAN",
            "CRYPTO_WALLET",
            "US_BANK_NUMBER",
            "SOCIALNUM",
            "US_SSN",
            "MEDICAL_LICENSE",
            "AU_MEDICARE",
            "IN_AADHAAR"
        })
        void should_ReturnHighSeverity_When_CriticalPiiType(String piiType) {
            PiiSeverity result = service.calculateSeverity(piiType);

            assertThat(result)
                .as("Type %s should be classified as HIGH severity", piiType)
                .isEqualTo(HIGH);
        }
    }

    @Nested
    @DisplayName("Calculate Severity - MEDIUM")
    class MediumSeverityTests {

        @ParameterizedTest(name = "{0} should be MEDIUM severity")
        @CsvSource({
            "DRIVERLICENSENUM",
            "IDCARDNUM",
            "TAXNUM",
            "US_PASSPORT",
            "US_DRIVER_LICENSE",
            "US_ITIN",
            "ES_NIF",
            "ES_NIE",
            "IT_FISCAL_CODE",
            "IT_PASSPORT",
            "IT_IDENTITY_CARD",
            "IT_DRIVER_LICENSE",
            "IT_VAT_CODE",
            "PL_PESEL",
            "SG_NRIC_FIN",
            "SG_UEN",
            "AU_TFN",
            "AU_ABN",
            "AU_ACN",
            "IN_PAN",
            "IN_VEHICLE_REGISTRATION",
            "IN_VOTER",
            "IN_PASSPORT",
            "FI_PERSONAL_IDENTITY_CODE",
            "KR_RRN",
            "TH_TNIN",
            "DATEOFBIRTH",
            "AGE"
        })
        void should_ReturnMediumSeverity_When_OfficialDocumentPiiType(String piiType) {
            PiiSeverity result = service.calculateSeverity(piiType);

            assertThat(result)
                .as("Type %s should be classified as MEDIUM severity", piiType)
                .isEqualTo(MEDIUM);
        }
    }

    @Nested
    @DisplayName("Calculate Severity - LOW")
    class LowSeverityTests {

        @ParameterizedTest(name = "{0} should be LOW severity")
        @CsvSource({
            "EMAIL",
            "TELEPHONENUM",
            "PHONE_NUMBER",
            "GIVENNAME",
            "SURNAME",
            "USERNAME",
            "IP_ADDRESS",
            "MAC_ADDRESS",
            "CITY",
            "STREET",
            "BUILDINGNUM",
            "ZIPCODE"
        })
        void should_ReturnLowSeverity_When_ContactOrLocationPiiType(String piiType) {
            PiiSeverity result = service.calculateSeverity(piiType);

            assertThat(result)
                .as("Type %s should be classified as LOW severity", piiType)
                .isEqualTo(LOW);
        }
    }

    @Nested
    @DisplayName("Calculate Severity - Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return LOW for unknown PII type (default)")
        void should_ReturnLowSeverity_When_UnknownPiiType() {
            PiiSeverity result = service.calculateSeverity("UNKNOWN_TYPE");

            assertThat(result)
                .as("Unknown PII types should default to LOW severity")
                .isEqualTo(LOW);
        }

        @Test
        @DisplayName("Should handle case-insensitive type names")
        void should_HandleCaseInsensitive_When_CalculatingSeverity() {
            SoftAssertions softly = new SoftAssertions();

            softly.assertThat(service.calculateSeverity("password"))
                .as("lowercase should work")
                .isEqualTo(HIGH);

            softly.assertThat(service.calculateSeverity("Password"))
                .as("mixed case should work")
                .isEqualTo(HIGH);

            softly.assertThat(service.calculateSeverity("PASSWORD"))
                .as("uppercase should work")
                .isEqualTo(HIGH);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle whitespace in type names")
        void should_HandleWhitespace_When_CalculatingSeverity() {
            SoftAssertions softly = new SoftAssertions();

            softly.assertThat(service.calculateSeverity(" PASSWORD "))
                .as("type with leading/trailing spaces")
                .isEqualTo(HIGH);

            softly.assertThat(service.calculateSeverity("  EMAIL  "))
                .as("type with multiple spaces")
                .isEqualTo(LOW);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle null type gracefully")
        void should_ReturnLowSeverity_When_NullPiiType() {
            PiiSeverity result = service.calculateSeverity(null);

            assertThat(result)
                .as("Null PII type should default to LOW severity")
                .isEqualTo(LOW);
        }

        @Test
        @DisplayName("Should handle empty string gracefully")
        void should_ReturnLowSeverity_When_EmptyPiiType() {
            PiiSeverity result = service.calculateSeverity("");

            assertThat(result)
                .as("Empty PII type should default to LOW severity")
                .isEqualTo(LOW);
        }
    }

    @Nested
    @DisplayName("Aggregate Counts")
    class AggregateCountsTests {

        @Test
        @DisplayName("Should return zero counts for empty list")
        void should_ReturnZeroCounts_When_EmptyList() {
            SeverityCounts result = service.aggregateCounts(List.of());

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isZero();
            softly.assertThat(result.medium()).isZero();
            softly.assertThat(result.low()).isZero();
            softly.assertThat(result.total()).isZero();
            softly.assertAll();
        }

        @Test
        @DisplayName("Should count only HIGH severity entities")
        void should_CountOnlyHighSeverity_When_AllEntitiesAreHigh() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("PASSWORD"),
                new PiiEntity("CREDITCARDNUMBER"),
                new PiiEntity("US_SSN")
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isEqualTo(3);
            softly.assertThat(result.medium()).isZero();
            softly.assertThat(result.low()).isZero();
            softly.assertThat(result.total()).isEqualTo(3);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should count only MEDIUM severity entities")
        void should_CountOnlyMediumSeverity_When_AllEntitiesAreMedium() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("DRIVERLICENSENUM"),
                new PiiEntity("US_PASSPORT"),
                new PiiEntity("DATEOFBIRTH")
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isZero();
            softly.assertThat(result.medium()).isEqualTo(3);
            softly.assertThat(result.low()).isZero();
            softly.assertThat(result.total()).isEqualTo(3);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should count only LOW severity entities")
        void should_CountOnlyLowSeverity_When_AllEntitiesAreLow() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("EMAIL"),
                new PiiEntity("TELEPHONENUM"),
                new PiiEntity("GIVENNAME"),
                new PiiEntity("SURNAME")
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isZero();
            softly.assertThat(result.medium()).isZero();
            softly.assertThat(result.low()).isEqualTo(4);
            softly.assertThat(result.total()).isEqualTo(4);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should count mixed severity entities correctly")
        void should_CountMixedSeverities_When_EntitiesHaveDifferentSeverities() {
            List<PiiEntity> entities = List.of(
                // HIGH
                new PiiEntity("PASSWORD"),
                new PiiEntity("CREDITCARDNUMBER"),
                // MEDIUM
                new PiiEntity("DRIVERLICENSENUM"),
                new PiiEntity("US_PASSPORT"),
                new PiiEntity("DATEOFBIRTH"),
                // LOW
                new PiiEntity("EMAIL"),
                new PiiEntity("TELEPHONENUM"),
                new PiiEntity("CITY"),
                new PiiEntity("STREET")
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isEqualTo(2);
            softly.assertThat(result.medium()).isEqualTo(3);
            softly.assertThat(result.low()).isEqualTo(4);
            softly.assertThat(result.total()).isEqualTo(9);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle unknown types as LOW severity")
        void should_CountUnknownTypesAsLow_When_Aggregating() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("PASSWORD"),          // HIGH
                new PiiEntity("UNKNOWN_TYPE_1"),    // LOW (default)
                new PiiEntity("DRIVERLICENSENUM"),  // MEDIUM
                new PiiEntity("UNKNOWN_TYPE_2"),    // LOW (default)
                new PiiEntity("EMAIL")              // LOW
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isEqualTo(1);
            softly.assertThat(result.medium()).isEqualTo(1);
            softly.assertThat(result.low()).isEqualTo(3); // 1 EMAIL + 2 unknown
            softly.assertThat(result.total()).isEqualTo(5);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle case-insensitive types when aggregating")
        void should_HandleCaseInsensitive_When_Aggregating() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("password"),      // HIGH
                new PiiEntity("Password"),      // HIGH
                new PiiEntity("EMAIL"),         // LOW
                new PiiEntity("email")          // LOW
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isEqualTo(2);
            softly.assertThat(result.medium()).isZero();
            softly.assertThat(result.low()).isEqualTo(2);
            softly.assertThat(result.total()).isEqualTo(4);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle duplicate types correctly")
        void should_CountDuplicates_When_SameTypeAppearsMultipleTimes() {
            List<PiiEntity> entities = List.of(
                new PiiEntity("PASSWORD"),
                new PiiEntity("PASSWORD"),
                new PiiEntity("PASSWORD")
            );

            SeverityCounts result = service.aggregateCounts(entities);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.high()).isEqualTo(3);
            softly.assertThat(result.medium()).isZero();
            softly.assertThat(result.low()).isZero();
            softly.assertThat(result.total()).isEqualTo(3);
            softly.assertAll();
        }
    }

    /**
     * Simple test entity to represent a detected PII.
     */
    private record PiiEntity(String piiType) {

    }
}

package pro.softcom.aisentinel.domain.pii.reporting;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DetectedPersonallyIdentifiableInformation} domain object.
 * 
 * <p>Focuses on testing the security-related business rule of masking sensitive data.</p>
 */
class DetectedPersonallyIdentifiableInformationTest {

    @Test
    void Should_MaskSensitiveValues_When_CallingWithMaskedSensitiveData() {
        // Given
        DetectedPersonallyIdentifiableInformation original = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(10)
                .endPosition(30)
                .piiType("EMAIL_ADDRESS")
                .piiTypeLabel("Email")
                .confidence(0.95)
                .sensitiveValue("john.doe@example.com")
                .sensitiveContext("Contact: john.doe@example.com for more info")
                .maskedContext("Contact: j***@e***.com for more info")
                .build();

        // When
        DetectedPersonallyIdentifiableInformation masked = original.withMaskedSensitiveData();

        // Then
        SoftAssertions softly = new SoftAssertions();
        
        // Sensitive data must be masked (null)
        softly.assertThat(masked.sensitiveValue())
                .as("Sensitive value should be masked (null)")
                .isNull();
        softly.assertThat(masked.sensitiveContext())
                .as("Sensitive context should be masked (null)")
                .isNull();
        
        // Other fields must remain unchanged
        softly.assertThat(masked.startPosition())
                .as("Start position should be preserved")
                .isEqualTo(10);
        softly.assertThat(masked.endPosition())
                .as("End position should be preserved")
                .isEqualTo(30);
        softly.assertThat(masked.piiType())
                .as("PII type should be preserved")
                .isEqualTo("EMAIL_ADDRESS");
        softly.assertThat(masked.piiTypeLabel())
                .as("PII type label should be preserved")
                .isEqualTo("Email");
        softly.assertThat(masked.confidence())
                .as("Confidence should be preserved")
                .isEqualTo(0.95);
        softly.assertThat(masked.maskedContext())
                .as("Masked context should be preserved")
                .isEqualTo("Contact: j***@e***.com for more info");
        
        softly.assertAll();
    }

    @Test
    void Should_PreserveNullValues_When_CallingWithMaskedSensitiveDataOnAlreadyMaskedObject() {
        // Given - already masked object
        DetectedPersonallyIdentifiableInformation alreadyMasked = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(10)
                .endPosition(30)
                .piiType("EMAIL_ADDRESS")
                .piiTypeLabel("Email")
                .confidence(0.95)
                .sensitiveValue(null)
                .sensitiveContext(null)
                .maskedContext("Contact: j***@e***.com for more info")
                .build();

        // When - calling mask again (idempotent operation)
        DetectedPersonallyIdentifiableInformation reMasked = alreadyMasked.withMaskedSensitiveData();

        // Then - should remain the same
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reMasked.sensitiveValue())
                .as("Sensitive value should remain null")
                .isNull();
        softly.assertThat(reMasked.sensitiveContext())
                .as("Sensitive context should remain null")
                .isNull();
        softly.assertThat(reMasked.maskedContext())
                .as("Masked context should be preserved")
                .isEqualTo("Contact: j***@e***.com for more info");
        softly.assertAll();
    }

    @Test
    void Should_CreateIndependentCopy_When_CallingWithMaskedSensitiveData() {
        // Given
        DetectedPersonallyIdentifiableInformation original = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(10)
                .endPosition(30)
                .piiType("CREDIT_CARD")
                .piiTypeLabel("Credit Card")
                .confidence(0.98)
                .sensitiveValue("4532-1234-5678-9012")
                .sensitiveContext("Card: 4532-1234-5678-9012")
                .maskedContext("Card: ****-****-****-9012")
                .build();

        // When
        DetectedPersonallyIdentifiableInformation masked = original.withMaskedSensitiveData();

        // Then - original should be unchanged (immutability)
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(original.sensitiveValue())
                .as("Original sensitive value should be unchanged")
                .isEqualTo("4532-1234-5678-9012");
        softly.assertThat(original.sensitiveContext())
                .as("Original sensitive context should be unchanged")
                .isEqualTo("Card: 4532-1234-5678-9012");
        
        // And masked should be different instance
        softly.assertThat(masked)
                .as("Masked object should be a different instance")
                .isNotSameAs(original);
        
        softly.assertAll();
    }
}

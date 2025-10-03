package pro.softcom.sentinelle.domain.pii.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection.SensitiveData;

/**
 * Unit tests for ContentAnalysis.SensitiveData masking rules.
 * These tests focus on business masking behavior to ensure that sensitive values
 * are not exposed when rendering or logging analysis results.
 */
class ContentPiiDetectionSensitiveDataTest {

    private ContentPiiDetection.SensitiveData sd(ContentPiiDetection.DataType t, String v) {
        return new ContentPiiDetection.SensitiveData(t, v, "ctx", 0, v != null ? v.length() : 0, 0.5, "sel");
    }

    @Test
    @DisplayName("Should mask email correctly when valid email provided")
    void Should_MaskEmail_When_ValidEmail() {
        // Given
        var data = sd(ContentPiiDetection.DataType.EMAIL, "john.doe@example.com");

        // When
        String masked = data.getMaskedValue();

        // Then
        assertThat(masked).isEqualTo("jo***@example.com");
    }

    @Test
    @DisplayName("Should fallback to generic mask for short email local part")
    void Should_ReturnGenericMask_When_EmailLocalPartTooShort() {
        var data = sd(ContentPiiDetection.DataType.EMAIL, "a@b.com");
        assertThat(data.getMaskedValue()).isEqualTo("***@***");
    }

    @Test
    @DisplayName("Should mask phone numbers across phone-related types")
    void Should_MaskPhone_For_AllPhoneTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.PHONE, "+41 22 123 45 67").getMaskedValue())
            .isEqualTo("+41***67");
        softly.assertThat(sd(ContentPiiDetection.DataType.PHONE_NUMBER, "079 456 78 90").getMaskedValue())
            .isEqualTo("079***90");
        softly.assertThat(sd(ContentPiiDetection.DataType.TELEPHONENUM, "022 345 67 89").getMaskedValue())
            .isEqualTo("022***89");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should mask AVS/SSN numbers")
    void Should_MaskAVS_For_SSNAndAVSTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.AVS, "756.1234.5678.90").getMaskedValue())
            .isEqualTo("756.****.****.XX");
        softly.assertThat(sd(ContentPiiDetection.DataType.SSN, "756.1234.5678.90").getMaskedValue())
            .isEqualTo("756.****.****.XX");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should mask credit card number with last 4 kept")
    void Should_MaskCreditCard_When_LongEnough() {
        var data = sd(ContentPiiDetection.DataType.CREDIT_CARD, "4532 1234 5678 9012");
        assertThat(data.getMaskedValue()).isEqualTo("****-****-****-9012");
    }

    @Test
    @DisplayName("Should mask bank account numbers keeping last 3 digits")
    void Should_MaskBankAccount_When_LongEnough() {
        var data = sd(ContentPiiDetection.DataType.BANK_ACCOUNT, "12-345678-9");
        assertThat(data.getMaskedValue()).isEqualTo("***8-9");
    }

    @Test
    @DisplayName("Should fully mask security-related secrets")
    void Should_ReturnMasked_When_SecurityTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.PASSWORD, "SecretPassword123!").getMaskedValue())
            .isEqualTo("***MASKED***");
        softly.assertThat(sd(ContentPiiDetection.DataType.API_KEY, "sk-123456").getMaskedValue())
            .isEqualTo("***MASKED***");
        softly.assertThat(sd(ContentPiiDetection.DataType.TOKEN, "jwt-token").getMaskedValue())
            .isEqualTo("***MASKED***");
        softly.assertThat(sd(ContentPiiDetection.DataType.SECURITY, "misc").getMaskedValue())
            .isEqualTo("***MASKED***");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should mask person names and variants")
    void Should_MaskPersonName_For_NameTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.PERSON, "Jean Dupont").getMaskedValue())
            .isEqualTo("J***t");
        softly.assertThat(sd(ContentPiiDetection.DataType.NAME, "Marie").getMaskedValue())
            .isEqualTo("M***e");
        softly.assertThat(sd(ContentPiiDetection.DataType.SURNAME, "Bernasconi").getMaskedValue())
            .isEqualTo("B***i");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should mask ID card numbers")
    void Should_MaskIdCard_When_LongEnough() {
        var data = sd(ContentPiiDetection.DataType.ID_CARD, "ID1234567");
        assertThat(data.getMaskedValue()).isEqualTo("ID***67");
    }

    @Test
    @DisplayName("Should mask IPv4 addresses by keeping first octet")
    void Should_MaskIpAddress_When_ValidIPv4() {
        var data = sd(ContentPiiDetection.DataType.IP_ADDRESS, "192.168.1.100");
        assertThat(data.getMaskedValue()).isEqualTo("192.***.***.***");
    }

    @Test
    @DisplayName("Should mask URL or attachment by hiding the authority and path")
    void Should_MaskUrl_For_UrlAndAttachmentTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.URL, "https://example.com/path").getMaskedValue())
            .isEqualTo("https://***");
        softly.assertThat(sd(ContentPiiDetection.DataType.ATTACHMENT, "http://host/resource").getMaskedValue())
            .isEqualTo("http://***");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should mask address-like values across location types")
    void Should_MaskAddress_For_LocationTypes() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(sd(ContentPiiDetection.DataType.LOCATION, "Rue de la Paix 15, 1201 Genève").getMaskedValue())
            .isEqualTo("Rue***ève");
        softly.assertThat(sd(ContentPiiDetection.DataType.ADDRESS, "Avenue des Alpes 42, 1820 Montreux").getMaskedValue())
            .isEqualTo("Ave***eux");
        softly.assertThat(sd(ContentPiiDetection.DataType.STREET, "Chemin des Vignes").getMaskedValue())
            .isEqualTo("Che***nes");
        softly.assertThat(sd(ContentPiiDetection.DataType.CITY, "Montreux").getMaskedValue())
            .isEqualTo("***");
        softly.assertThat(sd(ContentPiiDetection.DataType.ZIPCODE, "1201 Genève").getMaskedValue())
            .isEqualTo("120***ève");
        softly.assertThat(sd(ContentPiiDetection.DataType.BUILDINGNUM, "42Bâtiment").getMaskedValue())
            .isEqualTo("***");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should return unknown mask for UNKNOWN type")
    void Should_ReturnUnknownMask_When_UnknownType() {
        var data = sd(ContentPiiDetection.DataType.UNKNOWN, "whatever");
        assertThat(data.getMaskedValue()).isEqualTo("***UNKNOWN***");
    }

    @Test
    @DisplayName("Should mask usernames keeping first 2 and last 1 characters")
    void Should_MaskUsername_When_LongEnough() {
        var data = sd(ContentPiiDetection.DataType.USERNAME, "jdupont1");
        assertThat(data.getMaskedValue()).isEqualTo("jd***1");
    }
    @Test
    @DisplayName("Should return generic mask when phone number too short")
    void Should_ReturnGenericMask_When_PhoneTooShort() {
        var data = sd(ContentPiiDetection.DataType.PHONE, "12345");
        assertThat(data.getMaskedValue()).isEqualTo("***");
    }

    @Test
    @DisplayName("Should return generic mask when AVS is too short")
    void Should_ReturnGenericMask_When_AvsTooShort() {
        var data = sd(ContentPiiDetection.DataType.AVS, "1234567");
        assertThat(data.getMaskedValue()).isEqualTo("***");
    }

    @Test
    @DisplayName("Should return default mask when credit card is too short")
    void Should_ReturnDefaultMask_When_CreditCardTooShort() {
        var data = sd(ContentPiiDetection.DataType.CREDIT_CARD, "1234567");
        assertThat(data.getMaskedValue()).isEqualTo("****-****-****-****");
    }

    @Test
    @DisplayName("Should return generic mask when bank account is too short")
    void Should_ReturnGenericMask_When_BankAccountTooShort() {
        var data = sd(ContentPiiDetection.DataType.BANK_ACCOUNT, "12345");
        assertThat(data.getMaskedValue()).isEqualTo("***");
    }

    @Test
    @DisplayName("Should return generic mask when IP address is invalid")
    void Should_ReturnGenericMask_When_InvalidIpAddress() {
        var data = sd(ContentPiiDetection.DataType.IP_ADDRESS, "192.168.1");
        assertThat(data.getMaskedValue()).isEqualTo("***.***.***");
    }

    @Test
    @DisplayName("Should return generic mask when URL does not contain protocol")
    void Should_ReturnGenericMask_When_UrlWithoutProtocol() {
        var data = sd(ContentPiiDetection.DataType.URL, "example.com/path");
        assertThat(data.getMaskedValue()).isEqualTo("***");
    }

    @Test
    @DisplayName("Should return generic mask when username too short")
    void Should_ReturnGenericMask_When_UsernameTooShort() {
        var data = sd(ContentPiiDetection.DataType.USERNAME, "abc");
        assertThat(data.getMaskedValue()).isEqualTo("***");
    }



    @Test
    @DisplayName("Should compute correct risk score for mixed types")
    void Should_ComputeCorrectRiskScore_When_MixedTypes() {
        // PERSON (5) + PASSWORD (10) + URL (2) + UNKNOWN (1) + USERNAME (5) = 23
        ContentPiiDetection analysis = analysisWith(List.of(
            sd(ContentPiiDetection.DataType.PERSON),
            sd(ContentPiiDetection.DataType.PASSWORD),
            sd(ContentPiiDetection.DataType.URL),
            sd(ContentPiiDetection.DataType.UNKNOWN),
            sd(ContentPiiDetection.DataType.USERNAME)
        ));
        assertThat(analysis.getRiskScore()).isEqualTo(23);
    }

    @Test
    @DisplayName("Should return AUCUN when no sensitive data")
    void Should_ReturnAucun_When_NoSensitiveData() {
        ContentPiiDetection analysis = analysisWith(List.of());
        assertThat(analysis.getRiskLevel()).isEqualTo("AUCUN");
    }

    @Test
    @DisplayName("Should return FAIBLE for low total score")
    void Should_ReturnFaible_When_LowScore() {
        // One URL (2)
        ContentPiiDetection analysis = analysisWith(List.of(sd(ContentPiiDetection.DataType.URL)));
        assertThat(analysis.getRiskLevel()).isEqualTo("FAIBLE");
    }

    @Test
    @DisplayName("Should return MOYEN for medium total score")
    void Should_ReturnMoyen_When_MediumScore() {
        // Three phones (5 * 3 = 15)
        ContentPiiDetection analysis = analysisWith(List.of(
            sd(ContentPiiDetection.DataType.PHONE),
            sd(ContentPiiDetection.DataType.PHONE_NUMBER),
            sd(ContentPiiDetection.DataType.TELEPHONENUM)
        ));
        assertThat(analysis.getRiskLevel()).isEqualTo("MOYEN");
    }

    @Test
    @DisplayName("Should return ÉLEVÉ for high total score")
    void Should_ReturnEleve_When_HighScore() {
        // Three high-risk items (10 * 3 = 30)
        ContentPiiDetection analysis = analysisWith(List.of(
            sd(ContentPiiDetection.DataType.CREDIT_CARD),
            sd(ContentPiiDetection.DataType.BANK_ACCOUNT),
            sd(ContentPiiDetection.DataType.PASSWORD)
        ));
        assertThat(analysis.getRiskLevel()).isEqualTo("ÉLEVÉ");
    }

    @Test
    @DisplayName("Should return CRITIQUE for critical total score")
    void Should_ReturnCritique_When_CriticalScore() {
        // Five high-risk items (10 * 5 = 50)
        ContentPiiDetection analysis = analysisWith(List.of(
            sd(ContentPiiDetection.DataType.CREDIT_CARD),
            sd(ContentPiiDetection.DataType.BANK_ACCOUNT),
            sd(ContentPiiDetection.DataType.PASSWORD),
            sd(ContentPiiDetection.DataType.SSN),
            sd(ContentPiiDetection.DataType.ID_CARD)
        ));
        assertThat(analysis.getRiskLevel()).isEqualTo("CRITIQUE");
    }

    private ContentPiiDetection.SensitiveData sd(ContentPiiDetection.DataType t) {
        return new ContentPiiDetection.SensitiveData(t, "v", "ctx", 0, 1, 0.5, "sel");
    }

    private ContentPiiDetection analysisWith(List<SensitiveData> data) {
        return ContentPiiDetection.builder()
            .pageId("p")
            .pageTitle("t")
            .spaceKey("s")
            .analysisDate(LocalDateTime.now())
            .sensitiveDataFound(data)
            .statistics(null)
            .build();
    }
}

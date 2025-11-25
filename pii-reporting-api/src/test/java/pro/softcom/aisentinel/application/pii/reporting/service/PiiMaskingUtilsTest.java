package pro.softcom.aisentinel.application.pii.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

@DisplayName("PiiMaskingUtils - Utility methods for PII masking operations")
class PiiMaskingUtilsTest {

    // ========== token() Tests ==========

    @Nested
    @DisplayName("token() method tests")
    class TokenTests {

        @Test
        @DisplayName("Should_ReturnUnknownToken_When_TypeIsNull")
        void Should_ReturnUnknownToken_When_TypeIsNull() {
            // When
            String result = PiiMaskingUtils.token(null);

            // Then
            assertThat(result).isEqualTo("[UNKNOWN]");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should_ReturnUnknownToken_When_TypeIsBlankOrEmpty")
        void Should_ReturnUnknownToken_When_TypeIsBlankOrEmpty(String type) {
            // When
            String result = PiiMaskingUtils.token(type);

            // Then
            assertThat(result).isEqualTo("[UNKNOWN]");
        }

        @Test
        @DisplayName("Should_ReturnUnknownToken_When_TypeIsStringNull")
        void Should_ReturnUnknownToken_When_TypeIsStringNull() {
            // When
            String result = PiiMaskingUtils.token("null");

            // Then
            assertThat(result).isEqualTo("[UNKNOWN]");
        }

        @ParameterizedTest
        @ValueSource(strings = {"EMAIL", "PHONE", "SSN", "CREDIT_CARD"})
        @DisplayName("Should_ReturnFormattedToken_When_TypeIsValid")
        void Should_ReturnFormattedToken_When_TypeIsValid(String type) {
            // When
            String result = PiiMaskingUtils.token(type);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(result).startsWith("[");
                softly.assertThat(result).endsWith("]");
                softly.assertThat(result).contains(type);
                softly.assertThat(result).isEqualTo("[" + type + "]");
            });
        }
    }

    // ========== safeSub() Tests ==========

    @Nested
    @DisplayName("safeSub() method tests")
    class SafeSubTests {

        private static final String TEST_STRING = "Hello World";

        @ParameterizedTest
        @CsvSource({
                "0, 11, 'Hello World'",
                "0, 5, 'Hello'",
                "-5, 5, 'Hello'",
                "6, 100, 'World'",
                "5, 5, ''",
                "-10, 200, 'Hello World'",
                "10, 5, ''"
        })
        @DisplayName("Should_ReturnExpectedSubstring_When_GivenVariousRanges")
        void Should_ReturnExpectedSubstring_When_GivenVariousRanges(int start, int end, String expected) {
            // When
            String result = PiiMaskingUtils.safeSub(TEST_STRING, start, end);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should_HandleEmptyString_When_InputIsEmpty")
        void Should_HandleEmptyString_When_InputIsEmpty() {
            // When
            String result = PiiMaskingUtils.safeSub("", 0, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========== buildMaskedContent() Tests ==========

    @Nested
    @DisplayName("buildMaskedContent() method tests")
    class BuildMaskedContentTests {

        @Test
        @DisplayName("Should_ReturnNull_When_SourceIsNull")
        void Should_ReturnNull_When_SourceIsNull() {
            // Given
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(0, 5, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(null, entities, 100);

            // Then
            assertThat(result).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should_ReturnNull_When_SourceIsBlankOrEmpty")
        void Should_ReturnNull_When_SourceIsBlankOrEmpty(String source) {
            // Given
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(0, 5, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 100);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should_ReturnNull_When_EntitiesIsNull")
        void Should_ReturnNull_When_EntitiesIsNull() {
            // Given
            String source = "test@example.com";

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, null, 100);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should_ReturnNull_When_EntitiesIsEmpty")
        void Should_ReturnNull_When_EntitiesIsEmpty() {
            // Given
            String source = "test@example.com";

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, Collections.emptyList(), 100);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should_MaskSingleEntity_When_OneEntityPresent")
        void Should_MaskSingleEntity_When_OneEntityPresent() {
            // Given
            String source = "Contact: john@example.com";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(9, 25, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 100);

            // Then
            assertThat(result).isEqualTo("Contact: [EMAIL]");
        }

        @Test
        @DisplayName("Should_MaskMultipleEntities_When_MultipleEntitiesPresent")
        void Should_MaskMultipleEntities_When_MultipleEntitiesPresent() {
            // Given
            String source = "Email: john@example.com, Phone: 555-1234";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(
                    createEntity(7, 23, "EMAIL"),
                    createEntity(32, 40, "PHONE")
            );

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("Email: [EMAIL], Phone: [PHONE]");
        }

        @Test
        @DisplayName("Should_SortEntitiesByPosition_When_EntitiesAreUnordered")
        void Should_SortEntitiesByPosition_When_EntitiesAreUnordered() {
            // Given
            String source = "Email: john@example.com, Phone: 555-1234";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(
                    createEntity(32, 40, "PHONE"),
                    createEntity(7, 23, "EMAIL")
            );

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("Email: [EMAIL], Phone: [PHONE]");
        }

        @Test
        @DisplayName("Should_TruncateWithEllipsis_When_ContentExceedsMaxLength")
        void Should_TruncateWithEllipsis_When_ContentExceedsMaxLength() {
            // Given
            String source = "This is a very long email: john@example.com with lots of text";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(27, 43, "EMAIL"));
            int maxLen = 30;

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, maxLen);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(result).hasSize(maxLen + 1);
                softly.assertThat(result).endsWith("…");
                softly.assertThat(result).startsWith("This is a very long email: [EM");
            });
        }

        @Test
        @DisplayName("Should_NotTruncate_When_MaxLengthIsZero")
        void Should_NotTruncate_When_MaxLengthIsZero() {
            // Given
            String source = "Email: john@example.com";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(7, 23, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 0);

            // Then
            assertThat(result).isEqualTo("Email: [EMAIL]");
        }

        @Test
        @DisplayName("Should_NotTruncate_When_MaxLengthIsNegative")
        void Should_NotTruncate_When_MaxLengthIsNegative() {
            // Given
            String source = "Email: john@example.com";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(7, 23, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, -1);

            // Then
            assertThat(result).isEqualTo("Email: [EMAIL]");
        }

        @Test
        @DisplayName("Should_PreserveTextBetweenEntities_When_EntitiesAreNotContiguous")
        void Should_PreserveTextBetweenEntities_When_EntitiesAreNotContiguous() {
            // Given
            String source = "Start EMAIL middle PHONE end";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(
                    createEntity(6, 11, "EMAIL"),
                    createEntity(19, 24, "PHONE")
            );

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("Start [EMAIL] middle [PHONE] end");
        }

        @Test
        @DisplayName("Should_HandleEntityAtStart_When_FirstEntityStartsAtZero")
        void Should_HandleEntityAtStart_When_FirstEntityStartsAtZero() {
            // Given
            String source = "john@example.com is the contact";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(0, 16, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("[EMAIL] is the contact");
        }

        @Test
        @DisplayName("Should_HandleEntityAtEnd_When_LastEntityEndsAtSourceLength")
        void Should_HandleEntityAtEnd_When_LastEntityEndsAtSourceLength() {
            // Given
            String source = "Contact email: john@example.com";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(15, 31, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("Contact email: [EMAIL]");
        }

        @Test
        @DisplayName("Should_HandleMultipleConsecutiveEntities_When_NoTextBetween")
        void Should_HandleMultipleConsecutiveEntities_When_NoTextBetween() {
            // Given
            String source = "EMAILPHONE";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(
                    createEntity(0, 5, "EMAIL"),
                    createEntity(5, 10, "PHONE")
            );

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("[EMAIL][PHONE]");
        }

        @Test
        @DisplayName("Should_HandleEntitiesWithUnknownType_When_TypeIsNull")
        void Should_HandleEntitiesWithUnknownType_When_TypeIsNull() {
            // Given
            String source = "Some sensitive data here";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(5, 14, null));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("Some [UNKNOWN] data here");
        }

        @Test
        @DisplayName("Should_ClampEntityPositions_When_PositionsExceedSourceLength")
        void Should_ClampEntityPositions_When_PositionsExceedSourceLength() {
            // Given
            String source = "Short";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(createEntity(0, 100, "EMAIL"));

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 200);

            // Then
            assertThat(result).isEqualTo("[EMAIL]");
        }

        @Test
        @DisplayName("Should_HandleComplexScenario_When_MultipleEntitiesWithVariousTypes")
        void Should_HandleComplexScenario_When_MultipleEntitiesWithVariousTypes() {
            // Given
            String source = "User: John Doe, Email: john@example.com, SSN: 123-45-6789, Phone: 555-1234";
            List<DetectedPersonallyIdentifiableInformation> entities = List.of(
                    createEntity(6, 14, "PERSON"),
                    createEntity(23, 39, "EMAIL"),
                    createEntity(46, 57, "SSN"),
                    createEntity(66, 74, "PHONE")
            );

            // When
            String result = PiiMaskingUtils.buildMaskedContent(source, entities, 500);

            // Then
            assertThat(result).isEqualTo("User: [PERSON], Email: [EMAIL], SSN: [SSN], Phone: [PHONE]");
        }
    }

    // ========== Helper Methods ==========

    private static DetectedPersonallyIdentifiableInformation createEntity(int start, int end, String type) {
        return DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(start)
                .endPosition(end)
                .piiType(type)
                .piiTypeLabel(type)
                .confidence(0.9)
                .sensitiveValue("dummy")
                .sensitiveContext(null)
                .maskedContext(null)
                .build();
    }
}

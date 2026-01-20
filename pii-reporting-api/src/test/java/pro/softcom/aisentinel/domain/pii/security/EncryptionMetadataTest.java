package pro.softcom.aisentinel.domain.pii.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EncryptionMetadataTest {

    private static Stream<Arguments> serializationTestData() {
        return Stream.of(
            Arguments.of("EMAIL", 10, 25, "EMAIL|10|25", "all fields provided"),
            Arguments.of(null, 10, 25, "|10|25", "null type"),
            Arguments.of("PHONE", null, null, "PHONE|0|0", "null positions"),
            Arguments.of(null, null, null, "|0|0", "all null fields"),
            Arguments.of("EMAIL", 5, 10, "EMAIL|5|10", "UTF-8 encoding verification")
        );
    }

    @ParameterizedTest(name = "[{index}] Should serialize correctly when {4}")
    @MethodSource("serializationTestData")
    @DisplayName("Should_SerializeToCorrectFormat_VariousInputs")
    void should_SerializeToCorrectFormat_VariousInputs(String type, Integer posBegin, Integer posEnd, String expectedOutput) {
        // Given
        EncryptionMetadata metadata = new EncryptionMetadata(type, posBegin, posEnd);

        // When
        byte[] aadBytes = metadata.toAadBytes();
        String aadString = new String(aadBytes, StandardCharsets.UTF_8);

        // Then
        assertThat(aadBytes).isNotNull();
        assertThat(aadString).isEqualTo(expectedOutput);
    }

    @Test
    @DisplayName("Should_ProduceDifferentBytes_When_MetadataIsDifferent")
    void should_ProduceDifferentBytes_When_MetadataIsDifferent() {
        // Given
        EncryptionMetadata metadata1 = new EncryptionMetadata("EMAIL", 10, 25);
        EncryptionMetadata metadata2 = new EncryptionMetadata("PHONE", 10, 25);

        // When
        byte[] aad1 = metadata1.toAadBytes();
        byte[] aad2 = metadata2.toAadBytes();

        // Then
        assertThat(aad1).isNotEqualTo(aad2);
    }

    @Test
    @DisplayName("Should_ProduceSameBytes_When_MetadataIsIdentical")
    void should_ProduceSameBytes_When_MetadataIsIdentical() {
        // Given
        EncryptionMetadata metadata1 = new EncryptionMetadata("SSN", 15, 30);
        EncryptionMetadata metadata2 = new EncryptionMetadata("SSN", 15, 30);

        // When
        byte[] aad1 = metadata1.toAadBytes();
        byte[] aad2 = metadata2.toAadBytes();

        // Then
        assertThat(aad1).isEqualTo(aad2);
    }
}

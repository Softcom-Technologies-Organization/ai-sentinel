package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.security.EncryptionException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.config.EncryptionConfig;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.config.EncryptionKeyProvider;

@ExtendWith(MockitoExtension.class)
class AesGcmEncryptionAdapterTest {

    private AesGcmEncryptionAdapter adapter;

    @BeforeEach
    void setUp() {
        byte[] testKey = new byte[32];
        new SecureRandom().nextBytes(testKey);
        String keyBase64 = Base64.getEncoder().encodeToString(testKey);

        EncryptionConfig cfg = new EncryptionConfig(keyBase64);
        EncryptionKeyProvider keyProvider = new EncryptionKeyProvider(cfg);
        adapter = new AesGcmEncryptionAdapter(keyProvider);
    }

    private static Stream<Arguments> encryptDecryptTestData() {
        return Stream.of(
            Arguments.of("john.doe@example.com", new EncryptionMetadata("EMAIL", 0, 20), "regular email"),
            Arguments.of("", new EncryptionMetadata("TEXT", 0, 0), "empty string"),
            Arguments.of("Héllo 世界 🌍 Émoji", null, "unicode with special characters"),
            Arguments.of("a".repeat(10000), null, "long text (10000 chars)")
        );
    }

    @ParameterizedTest(name = "[{index}] Should encrypt and decrypt: {2}")
    @MethodSource("encryptDecryptTestData")
    @DisplayName("Should_EncryptAndDecrypt_VariousDataTypes")
    void should_EncryptAndDecrypt_VariousDataTypes(String plaintext, EncryptionMetadata metadata) {
        // When
        String encrypted = adapter.encrypt(plaintext, metadata);
        String decrypted = adapter.decrypt(encrypted, metadata);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted).isNotNull();
        softly.assertThat(encrypted).startsWith("ENC:v1:");
        softly.assertThat(encrypted).isNotEqualTo(plaintext);
        softly.assertThat(decrypted).isEqualTo(plaintext);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_ProduceDifferentCiphertext_When_EncryptingSamePlaintextTwice")
    void should_ProduceDifferentCiphertext_When_EncryptingSamePlaintextTwice() {
        // Given
        String plaintext = "sensitive data";
        EncryptionMetadata metadata = new EncryptionMetadata("SSN", 5, 15);

        // When
        String encrypted1 = adapter.encrypt(plaintext, metadata);
        String encrypted2 = adapter.encrypt(plaintext, metadata);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("Should_ThrowException_When_CiphertextTampered")
    void should_ThrowException_When_CiphertextTampered() {
        // Given
        String plaintext = "secret";
        EncryptionMetadata metadata = new EncryptionMetadata("PASSWORD", 0, 6);
        String encrypted = adapter.encrypt(plaintext, metadata);

        // Modify the ciphertext
        String[] parts = encrypted.substring("ENC:v1:".length()).split(":");
        String tamperedCt = "AAAA" + parts[2].substring(4);
        String tampered = "ENC:v1:" + parts[0] + ":" + parts[1] + ":" + tamperedCt;

        // When/Then - GCM will automatically detect via its tag
        assertThatThrownBy(() -> adapter.decrypt(tampered, metadata))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("decrypt");
    }

    @Test
    @DisplayName("Should_ThrowException_When_WrongMetadataUsedForDecryption")
    void should_ThrowException_When_WrongMetadataUsedForDecryption() {
        // Given
        String plaintext = "data";
        EncryptionMetadata correctMetadata = new EncryptionMetadata("EMAIL", 0, 10);
        EncryptionMetadata wrongMetadata = new EncryptionMetadata("PHONE", 0, 10);

        String encrypted = adapter.encrypt(plaintext, correctMetadata);

        // When/Then
        assertThatThrownBy(() -> adapter.decrypt(encrypted, wrongMetadata))
            .isInstanceOf(EncryptionException.class);
    }

    @Test
    @DisplayName("Should_ReturnOriginal_When_DecryptingNonEncryptedValue")
    void should_ReturnOriginal_When_DecryptingNonEncryptedValue() {
        // Given
        String plaintext = "not encrypted";
        EncryptionMetadata metadata = new EncryptionMetadata("TEXT", 0, 5);

        // When
        String result = adapter.decrypt(plaintext, metadata);

        // Then
        assertThat(result).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should_HandleNullMetadata_When_EncryptingWithoutAAD")
    void should_HandleNullMetadata_When_EncryptingWithoutAAD() {
        // Given
        String plaintext = "data without metadata";

        // When
        String encrypted = adapter.encrypt(plaintext, null);
        String decrypted = adapter.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should_ThrowException_When_InvalidEncryptedFormat")
    void should_ThrowException_When_InvalidEncryptedFormat() {
        // Given
        String invalid = "ENC:v1:tooshort";
        EncryptionMetadata metadata = new EncryptionMetadata("EMAIL", 0, 5);

        // When/Then
        assertThatThrownBy(() -> adapter.decrypt(invalid, metadata))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Invalid encrypted format");
    }

    @Test
    @DisplayName("Should_ProduceCorrectFormat_When_Encrypting")
    void should_ProduceCorrectFormat_When_Encrypting() {
        // Given
        String plaintext = "test";
        
        // When
        String encrypted = adapter.encrypt(plaintext, null);
        
        // Then - Format: ENC:v1:<salt>:<iv>:<ct> (3 parties)
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted).startsWith("ENC:v1:");
        softly.assertThat(encrypted).matches("^ENC:v1:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+$");
        
        String[] parts = encrypted.substring("ENC:v1:".length()).split(":");
        softly.assertThat(parts).hasSize(3);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_DetectTampering_When_GcmTagModified")
    void should_DetectTampering_When_GcmTagModified() {
        // Given
        String plaintext = "sensitive";
        EncryptionMetadata metadata = new EncryptionMetadata("DATA", 0, 9);
        String encrypted = adapter.encrypt(plaintext, metadata);
        
        // Modify the last bytes (GCM tag)
        String[] parts = encrypted.substring("ENC:v1:".length()).split(":");
        byte[] ctBytes = Base64.getDecoder().decode(parts[2]);
        ctBytes[ctBytes.length - 1] ^= (byte) 0xFF; // Flip last byte
        String tamperedCt = Base64.getEncoder().encodeToString(ctBytes);
        String tampered = "ENC:v1:" + parts[0] + ":" + parts[1] + ":" + tamperedCt;
        
        // When/Then - GCM throws an exception
        assertThatThrownBy(() -> adapter.decrypt(tampered, metadata))
            .isInstanceOf(EncryptionException.class);
    }

    @Test
    @DisplayName("Should_FailDecryption_When_WrongAadProvided")
    void should_FailDecryption_When_WrongAadProvided() {
        // Given
        String plaintext = "data";
        EncryptionMetadata correctMetadata = new EncryptionMetadata("EMAIL", 0, 10);
        EncryptionMetadata wrongMetadata = new EncryptionMetadata("PHONE", 0, 10);

        String encrypted = adapter.encrypt(plaintext, correctMetadata);

        // When/Then - GCM will automatically verify the AAD
        assertThatThrownBy(() -> adapter.decrypt(encrypted, wrongMetadata))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("decrypt");
    }

    @ParameterizedTest(name = "[{index}] isEncrypted(''{0}'') should return {1}")
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
        ENC:v1:abc:def:ghi     | true
        plaintext              | false
        NULL                   | false
        """)
    @DisplayName("Should_IdentifyEncryptedValue_When_CheckingIsEncrypted")
    void should_IdentifyEncryptedValue_When_CheckingIsEncrypted(String value, boolean expected) {
        assertThat(adapter.isEncrypted(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should_FailDecryption_When_TamperedWithSalt")
    void should_FailDecryption_When_TamperedWithSalt() {
        // Given
        String plaintext = "secret message";
        EncryptionMetadata metadata = new EncryptionMetadata("SECRET", 0, 14);
        String encrypted = adapter.encrypt(plaintext, metadata);

        String[] parts = encrypted.substring("ENC:v1:".length()).split(":");
        String tamperedSalt = "AAAA" + parts[0].substring(4);
        String tampered = "ENC:v1:" + tamperedSalt + ":" + parts[1] + ":" + parts[2];

        // When/Then - Will fail because DEK will be different
        assertThatThrownBy(() -> adapter.decrypt(tampered, metadata))
            .isInstanceOf(EncryptionException.class);
    }

}

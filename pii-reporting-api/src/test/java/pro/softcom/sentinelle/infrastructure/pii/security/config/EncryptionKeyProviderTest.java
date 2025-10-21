package pro.softcom.sentinelle.infrastructure.pii.security.config;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptionKeyProviderTest {

    @Mock
    private EncryptionConfig mockConfig;

    @Mock
    private KeyLoader mockKeyLoader;

    @Test
    @DisplayName("Should_ReturnValidSecretKey_When_ValidBase64KeyProvided")
    void should_ReturnValidSecretKey_When_ValidBase64KeyProvided() {
        // Given
        String validKey = generateValidBase64Key();
        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(validKey);

        // When
        EncryptionKeyProvider provider = new EncryptionKeyProvider(mockConfig, mockKeyLoader);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(provider.getKey()).isNotNull();
        softly.assertThat(provider.getKey().getAlgorithm()).isEqualTo("AES");
        softly.assertThat(provider.getKey().getEncoded()).hasSize(32);
        softly.assertAll();
    }

    @ParameterizedTest(name = "[{index}] Should throw exception when key is: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "  \t  "})
    @DisplayName("Should_ThrowException_When_KeyIsNullEmptyOrBlank")
    void should_ThrowException_When_KeyIsNullEmptyOrBlank(String invalidKey) {
        // Given
        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(invalidKey);

        // When/Then
        assertThatThrownBy(() -> new EncryptionKeyProvider(mockConfig, mockKeyLoader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing KEK environment variable");
    }

    @Test
    @DisplayName("Should_ThrowException_When_InvalidBase64Format")
    void should_ThrowException_When_InvalidBase64Format() {
        // Given
        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn("not-valid-base64!@#$");

        // When/Then
        assertThatThrownBy(() -> new EncryptionKeyProvider(mockConfig, mockKeyLoader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid base64 encoding");
    }

    private static Stream<Arguments> invalidKeyLengthTestData() {
        return Stream.of(
            Arguments.of(16, "too short (128 bits)"),
            Arguments.of(24, "incorrect (192 bits)"),
            Arguments.of(64, "too long (512 bits)")
        );
    }

    @ParameterizedTest(name = "[{index}] Should throw exception when key length is {1}")
    @MethodSource("invalidKeyLengthTestData")
    @DisplayName("Should_ThrowException_When_KeyLengthIsInvalid")
    void should_ThrowException_When_KeyLengthIsInvalid(int keyLength) {
        // Given
        byte[] invalidKey = new byte[keyLength];
        new SecureRandom().nextBytes(invalidKey);
        String invalidKeyBase64 = Base64.getEncoder().encodeToString(invalidKey);

        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(invalidKeyBase64);

        // When/Then
        assertThatThrownBy(() -> new EncryptionKeyProvider(mockConfig, mockKeyLoader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid key length")
                .hasMessageContaining(keyLength + " bytes")
                .hasMessageContaining("Expected 32 bytes");
    }

    @Test
    @DisplayName("Should_TrimWhitespace_When_KeyHasLeadingOrTrailingSpaces")
    void should_TrimWhitespace_When_KeyHasLeadingOrTrailingSpaces() {
        // Given
        String validKey = generateValidBase64Key();
        String keyWithSpaces = "  " + validKey + "  ";

        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(keyWithSpaces);

        // When
        EncryptionKeyProvider provider = new EncryptionKeyProvider(mockConfig, mockKeyLoader);

        // Then
        assertThat(provider.getKey()).isNotNull();
        assertThat(provider.getKey().getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("Should_LoadAndParseKey_When_ValidEnvironmentVariable")
    void should_LoadAndParseKey_When_ValidEnvironmentVariable() {
        // Given
        String validKey = generateValidBase64Key();
        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(validKey);

        // When
        EncryptionKeyProvider provider = new EncryptionKeyProvider(mockConfig, mockKeyLoader);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(provider.getKey()).isNotNull();
        softly.assertThat(provider.getKey().getAlgorithm()).isEqualTo("AES");
        softly.assertThat(provider.getKey().getEncoded()).hasSize(32);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_CreateAesSecretKey_When_ValidKeyAndAlgorithm")
    void should_CreateAesSecretKey_When_ValidKeyAndAlgorithm() {
        // Given
        String validKey = generateValidBase64Key();
        when(mockConfig.kekEnvVariable()).thenReturn("MOCK_KEK_VAR");
        when(mockKeyLoader.loadKey("MOCK_KEK_VAR")).thenReturn(validKey);

        // When
        EncryptionKeyProvider provider = new EncryptionKeyProvider(mockConfig, mockKeyLoader);

        // Then
        assertThat(provider.getKey().getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("Should_UseKeyLoader_When_LoadingKey")
    void should_UseKeyLoader_When_LoadingKey() {
        // Given
        String validKey = generateValidBase64Key();
        when(mockConfig.kekEnvVariable()).thenReturn("TEST_VAR");
        when(mockKeyLoader.loadKey("TEST_VAR")).thenReturn(validKey);

        // When
        EncryptionKeyProvider provider = new EncryptionKeyProvider(mockConfig, mockKeyLoader);

        // Then
        assertThat(provider.getKey()).isNotNull();
    }

    /**
     * Generates a valid 256-bit (32 bytes) Base64 key for tests.
     */
    private String generateValidBase64Key() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}

package pro.softcom.sentinelle.application.pii.security;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanResultEncryptorTest {

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ScanResultEncryptor encryptor;

    @Test
    @DisplayName("Should_EncryptAllEntities_When_ScanResultHasMultipleEntities")
    void should_EncryptAllEntities_When_ScanResultHasMultipleEntities() {
        // Given
        List<PiiEntity> entities = List.of(
            createEntity("EMAIL", 0, 20, "john@example.com"),
            createEntity("PHONE", 25, 35, "1234567890")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.encrypt(eq("john@example.com"), any()))
            .thenReturn("ENC:v1:encrypted_email");
        when(encryptionService.encrypt(eq("1234567890"), any()))
            .thenReturn("ENC:v1:encrypted_phone");

        // When
        ScanResult encrypted = encryptor.encrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted.entities()).hasSize(2);
        softly.assertThat(encrypted.entities().get(0).text())
            .isEqualTo("ENC:v1:encrypted_email");
        softly.assertThat(encrypted.entities().get(1).text())
            .isEqualTo("ENC:v1:encrypted_phone");
        softly.assertAll();

        verify(encryptionService, times(2)).encrypt(anyString(), any(EncryptionMetadata.class));
    }

    private static Stream<Arguments> noEntitiesTestData() {
        return Stream.of(
            Arguments.of(null, "null entities"),
            Arguments.of(List.of(), "empty entities list")
        );
    }

    @ParameterizedTest(name = "[{index}] Should return unchanged when encrypting: {1}")
    @MethodSource("noEntitiesTestData")
    @DisplayName("Should_ReturnUnchanged_When_EncryptingWithNoEntities")
    void should_ReturnUnchanged_When_EncryptingWithNoEntities(List<PiiEntity> entities) {
        // Given
        ScanResult scanResult = createScanResult(entities);
        
        // When
        ScanResult result = encryptor.encrypt(scanResult);
        
        // Then
        if (entities == null) {
            assertThat(result).isEqualTo(scanResult);
        } else {
            assertThat(result.entities()).isEmpty();
        }
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("Should_BuildCorrectMetadata_When_EncryptingEntity")
    void should_BuildCorrectMetadata_When_EncryptingEntity() {
        // Given
        PiiEntity entity = createEntity("SSN", 10, 20, "123-45-6789");
        ScanResult scanResult = createScanResult(List.of(entity));

        ArgumentCaptor<EncryptionMetadata> metadataCaptor =
            ArgumentCaptor.forClass(EncryptionMetadata.class);

        when(encryptionService.encrypt(anyString(), any()))
            .thenReturn("ENC:encrypted");

        // When
        encryptor.encrypt(scanResult);

        // Then
        verify(encryptionService).encrypt(eq("123-45-6789"), metadataCaptor.capture());

        EncryptionMetadata captured = metadataCaptor.getValue();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captured.type()).isEqualTo("SSN");
        softly.assertThat(captured.positionBegin()).isEqualTo(10);
        softly.assertThat(captured.positionEnd()).isEqualTo(20);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_DecryptOnlyEncryptedEntities_When_MixedEntities")
    void should_DecryptOnlyEncryptedEntities_When_MixedEntities() {
        // Given
        List<PiiEntity> entities = List.of(
            createEntity("EMAIL", 0, 10, "ENC:v1:encrypted"),
            createEntity("NAME", 15, 25, "plaintext")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.isEncrypted("ENC:v1:encrypted")).thenReturn(true);
        when(encryptionService.isEncrypted("plaintext")).thenReturn(false);
        when(encryptionService.decrypt(eq("ENC:v1:encrypted"), any()))
            .thenReturn("decrypted@email.com");

        // When
        ScanResult decrypted = encryptor.decrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(decrypted.entities().get(0).text())
            .isEqualTo("decrypted@email.com");
        softly.assertThat(decrypted.entities().get(1).text())
            .isEqualTo("plaintext");
        softly.assertAll();

        verify(encryptionService, times(1)).decrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should_ReturnUnchanged_When_DecryptingWithNoEntities")
    void should_ReturnUnchanged_When_DecryptingWithNoEntities() {
        // Given
        ScanResult scanResult = createScanResult(null);
        
        // When
        ScanResult result = encryptor.decrypt(scanResult);
        
        // Then
        assertThat(result).isEqualTo(scanResult);
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("Should_PreserveOtherFields_When_EncryptingEntities")
    void should_PreserveOtherFields_When_EncryptingEntities() {
        // Given
        PiiEntity entity = createEntity("EMAIL", 0, 10, "email@test.com");
        ScanResult original = ScanResult.builder()
            .scanId("scan-123")
            .spaceKey("SPACE")
            .pageId("page-456")
            .entities(List.of(entity))
            .build();

        when(encryptionService.encrypt(anyString(), any()))
            .thenReturn("ENC:encrypted");

        // When
        ScanResult encrypted = encryptor.encrypt(original);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted.scanId()).isEqualTo("scan-123");
        softly.assertThat(encrypted.spaceKey()).isEqualTo("SPACE");
        softly.assertThat(encrypted.pageId()).isEqualTo("page-456");
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_DecryptAllEncryptedEntities_When_AllAreEncrypted")
    void should_DecryptAllEncryptedEntities_When_AllAreEncrypted() {
        // Given
        List<PiiEntity> entities = List.of(
            createEntity("EMAIL", 0, 20, "ENC:v1:enc1"),
            createEntity("PHONE", 25, 35, "ENC:v1:enc2")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.isEncrypted(anyString())).thenReturn(true);
        when(encryptionService.decrypt(eq("ENC:v1:enc1"), any()))
            .thenReturn("email@test.com");
        when(encryptionService.decrypt(eq("ENC:v1:enc2"), any()))
            .thenReturn("555-1234");

        // When
        ScanResult decrypted = encryptor.decrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(decrypted.entities()).hasSize(2);
        softly.assertThat(decrypted.entities().get(0).text())
            .isEqualTo("email@test.com");
        softly.assertThat(decrypted.entities().get(1).text())
            .isEqualTo("555-1234");
        softly.assertAll();

        verify(encryptionService, times(2)).decrypt(anyString(), any());
    }

    private PiiEntity createEntity(String type, int start, int end, String text) {
        return PiiEntity.builder()
            .type(type)
            .start(start)
            .end(end)
            .text(text)
            .score(0.9)
            .build();
    }

    private ScanResult createScanResult(List<PiiEntity> entities) {
        return ScanResult.builder()
            .scanId("test-scan")
            .entities(entities)
            .build();
    }
}

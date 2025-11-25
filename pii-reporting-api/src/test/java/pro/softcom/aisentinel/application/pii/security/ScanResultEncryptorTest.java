package pro.softcom.aisentinel.application.pii.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.domain.pii.security.EncryptionException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;

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
        List<DetectedPersonallyIdentifiableInformation> entities = List.of(
            createEntity("EMAIL", 0, 20, "john@example.com"),
            createEntity("PHONE", 25, 35, "1234567890")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.encrypt(eq("john@example.com"), any()))
            .thenReturn("ENC:v1:encrypted_email");
        when(encryptionService.encrypt(eq("john@example.com context"), any()))
            .thenReturn("ENC:v1:encrypted_email context");
        when(encryptionService.encrypt(eq("1234567890"), any()))
            .thenReturn("ENC:v1:encrypted_phone");
        when(encryptionService.encrypt(eq("1234567890 context"), any()))
            .thenReturn("ENC:v1:encrypted_phone context");

        // When
        ScanResult encrypted = encryptor.encrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted.detectedPIIs()).hasSize(2);
        softly.assertThat(encrypted.detectedPIIs().get(0).sensitiveValue())
            .isEqualTo("ENC:v1:encrypted_email");
        softly.assertThat(encrypted.detectedPIIs().get(1).sensitiveValue())
            .isEqualTo("ENC:v1:encrypted_phone");
        softly.assertAll();

        verify(encryptionService, times(2 * 2)).encrypt(anyString(), any(EncryptionMetadata.class));
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
    void should_ReturnUnchanged_When_EncryptingWithNoEntities(List<DetectedPersonallyIdentifiableInformation> entities) {
        // Given
        ScanResult scanResult = createScanResult(entities);
        
        // When
        ScanResult result = encryptor.encrypt(scanResult);
        
        // Then
        if (entities == null) {
            assertThat(result).isEqualTo(scanResult);
        } else {
            assertThat(result.detectedPIIs()).isEmpty();
        }
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("Should_BuildCorrectMetadata_When_EncryptingEntity")
    void should_BuildCorrectMetadata_When_EncryptingEntity() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createEntity("SSN", 10, 20, "123-45-6789");
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
        softly.assertThat(captured.piiType()).isEqualTo("SSN");
        softly.assertThat(captured.startPosition()).isEqualTo(10);
        softly.assertThat(captured.endPosition()).isEqualTo(20);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_DecryptOnlyEncryptedEntities_When_MixedEntities")
    void should_DecryptOnlyEncryptedEntities_When_MixedEntities() {
        // Given
        List<DetectedPersonallyIdentifiableInformation> entities = List.of(
            createEntity("EMAIL", 0, 10, "ENC:v1:encrypted"),
            createEntity("NAME", 15, 25, "plaintext")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.isEncrypted("ENC:v1:encrypted")).thenReturn(true);
        when(encryptionService.isEncrypted("ENC:v1:encrypted context")).thenReturn(true);
        when(encryptionService.isEncrypted("plaintext")).thenReturn(false);
        when(encryptionService.isEncrypted("plaintext context")).thenReturn(false);
        when(encryptionService.decrypt(eq("ENC:v1:encrypted"), any()))
            .thenReturn("decrypted@email.com");
        when(encryptionService.decrypt(eq("ENC:v1:encrypted context"), any()))
            .thenReturn("decrypted context");

        // When
        ScanResult decrypted = encryptor.decrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(decrypted.detectedPIIs().get(0).sensitiveValue())
            .isEqualTo("decrypted@email.com");
        softly.assertThat(decrypted.detectedPIIs().get(1).sensitiveValue())
            .isEqualTo("plaintext");
        softly.assertAll();

        verify(encryptionService, times(1 * 2)).decrypt(anyString(), any());
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
        DetectedPersonallyIdentifiableInformation entity = createEntity("EMAIL", 0, 10, "email@test.com");
        ScanResult original = ScanResult.builder()
            .scanId("scan-123")
            .spaceKey("SPACE")
            .pageId("page-456")
            .detectedEntities(List.of(entity))
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
        List<DetectedPersonallyIdentifiableInformation> entities = List.of(
            createEntity("EMAIL", 0, 20, "ENC:v1:enc1"),
            createEntity("PHONE", 25, 35, "ENC:v1:enc2")
        );
        ScanResult scanResult = createScanResult(entities);

        when(encryptionService.isEncrypted(anyString())).thenReturn(true);
        when(encryptionService.decrypt(eq("ENC:v1:enc1"), any()))
            .thenReturn("email@test.com");
        when(encryptionService.decrypt(eq("ENC:v1:enc2"), any()))
            .thenReturn("555-1234");
        when(encryptionService.decrypt(eq("ENC:v1:enc1 context"), any()))
            .thenReturn("email@test.com context");
        when(encryptionService.decrypt(eq("ENC:v1:enc2 context"), any()))
            .thenReturn("555-1234 context");

        // When
        ScanResult decrypted = encryptor.decrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(decrypted.detectedPIIs()).hasSize(2);
        softly.assertThat(decrypted.detectedPIIs().get(0).sensitiveValue())
            .isEqualTo("email@test.com");
        softly.assertThat(decrypted.detectedPIIs().get(1).sensitiveValue())
            .isEqualTo("555-1234");
        softly.assertAll();

        verify(encryptionService, times(2 * 2)).decrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should_PropagateEncryptionException_When_EncryptionFails")
    void should_PropagateEncryptionException_When_EncryptionFails() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createEntity("EMAIL", 0, 20, "test@example.com");
        ScanResult scanResult = createScanResult(List.of(entity));

        when(encryptionService.encrypt(anyString(), any()))
            .thenThrow(new EncryptionException("Key not found"));

        // When & Then
        assertThatThrownBy(() -> encryptor.encrypt(scanResult))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Key not found");
    }

    @Test
    @DisplayName("Should_PropagateEncryptionException_When_DecryptionFails")
    void should_PropagateEncryptionException_When_DecryptionFails() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createEntity("EMAIL", 0, 20, "ENC:v1:corrupted");
        ScanResult scanResult = createScanResult(List.of(entity));

        when(encryptionService.isEncrypted("ENC:v1:corrupted")).thenReturn(true);
        when(encryptionService.decrypt(anyString(), any()))
            .thenThrow(new EncryptionException("Integrity check failed"));

        // When & Then
        assertThatThrownBy(() -> encryptor.decrypt(scanResult))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Integrity check failed");
    }

    @Test
    @DisplayName("Should_EncryptSuccessfully_When_EncryptingMoreThan500Entities")
    void should_EncryptSuccessfully_When_EncryptingMoreThan500Entities() {
        // Given
        List<DetectedPersonallyIdentifiableInformation> largeEntityList = IntStream.range(0, 501)
            .mapToObj(i -> createEntity("EMAIL", i * 10, i * 10 + 5, "email" + i + "@test.com"))
            .toList();

        ScanResult scanResult = createScanResult(largeEntityList);

        when(encryptionService.encrypt(anyString(), any()))
            .thenReturn("ENC:v1:encrypted");

        // When
        ScanResult encrypted = encryptor.encrypt(scanResult);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(encrypted.detectedPIIs()).hasSize(501);
        softly.assertThat(encrypted.detectedPIIs().getFirst().sensitiveValue())
            .isEqualTo("ENC:v1:encrypted");
        softly.assertAll();

        verify(encryptionService, times(501 * 2)).encrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should_PreserveScanResultIntegrity_When_EncryptionExceptionOccurs")
    void should_PreserveScanResultIntegrity_When_EncryptionExceptionOccurs() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createEntity("EMAIL", 0, 20, "test@example.com");
        ScanResult original = ScanResult.builder()
            .scanId("scan-123")
            .spaceKey("SPACE")
            .pageId("page-456")
            .detectedEntities(List.of(entity))
            .build();

        when(encryptionService.encrypt(anyString(), any()))
            .thenThrow(new EncryptionException("Test failure"));

        // When & Then
        assertThatThrownBy(() -> encryptor.encrypt(original))
            .isInstanceOf(EncryptionException.class);

        // Verify original scanResult is not modified (immutability)
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(original.scanId()).isEqualTo("scan-123");
        softly.assertThat(original.detectedPIIs().getFirst().sensitiveValue())
            .isEqualTo("test@example.com");
        softly.assertAll();
    }

    private DetectedPersonallyIdentifiableInformation createEntity(String type, int start, int end, String text) {
        return DetectedPersonallyIdentifiableInformation.builder()
            .piiType(type)
            .startPosition(start)
            .endPosition(end)
            .sensitiveValue(text)
            .sensitiveContext(text + " context")
            .confidence(0.9)
            .build();
    }

    private ScanResult createScanResult(List<DetectedPersonallyIdentifiableInformation> entities) {
        return ScanResult.builder()
            .scanId("test-scan")
            .detectedEntities(entities)
            .build();
    }
}

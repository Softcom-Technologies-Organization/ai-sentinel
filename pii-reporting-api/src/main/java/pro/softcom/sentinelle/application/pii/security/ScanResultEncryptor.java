package pro.softcom.sentinelle.application.pii.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;

/**
 * Processor for encrypting/decrypting detectedEntities in ScanResult.
 * Business intent: orchestrate PII encryption in the business flow.
 */
@Component
@RequiredArgsConstructor
public class ScanResultEncryptor {
    private final EncryptionService encryptionService;

    public ScanResult encrypt(ScanResult scanResult) {
        var entities = scanResult.detectedEntities();
        if (entities == null) {
            return scanResult;
        }

        var encryptedEntities = entities.stream()
                .map(this::encryptEntity)
                .toList();

        return scanResult.toBuilder()
                .detectedEntities(encryptedEntities)
                .build();
    }

    public ScanResult decrypt(ScanResult scanResult) {
        var entities = scanResult.detectedEntities();
        if (entities == null) {
            return scanResult;
        }

        var decryptedEntities = entities.stream()
                .map(this::decryptEntity)
                .toList();

        return scanResult.toBuilder()
                .detectedEntities(decryptedEntities)
                .build();
    }

    private PiiEntity encryptEntity(PiiEntity entity) {
        EncryptionMetadata metadata = buildMetadata(entity);
        var encryptedText = encryptionService.encrypt(entity.detectedValue(), metadata);
        return entity.toBuilder().detectedValue(encryptedText).build();
    }

    private PiiEntity decryptEntity(PiiEntity entity) {
        var decryptedText = entity.detectedValue();
        if (encryptionService.isEncrypted(entity.detectedValue())) {
            EncryptionMetadata metadata = buildMetadata(entity);
            decryptedText = encryptionService.decrypt(entity.detectedValue(), metadata);
        }
        return entity.toBuilder().detectedValue(decryptedText).build();
    }

    private EncryptionMetadata buildMetadata(PiiEntity entity) {
        return new EncryptionMetadata(entity.piiType(), entity.startPosition(), entity.endPosition());
    }
}

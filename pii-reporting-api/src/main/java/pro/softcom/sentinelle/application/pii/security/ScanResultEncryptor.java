package pro.softcom.sentinelle.application.pii.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;

/**
 * Processor for encrypting/decrypting entities in ScanResult.
 * Business intent: orchestrate PII encryption in the business flow.
 */
@Component
@RequiredArgsConstructor
public class ScanResultEncryptor {
    private final EncryptionService encryptionService;

    public ScanResult encrypt(ScanResult scanResult) {
        var entities = scanResult.entities();
        if (entities == null) {
            return scanResult;
        }

        var encryptedEntities = entities.stream()
                .map(this::encryptEntity)
                .toList();

        return scanResult.toBuilder()
                .entities(encryptedEntities)
                .build();
    }

    public ScanResult decrypt(ScanResult scanResult) {
        var entities = scanResult.entities();
        if (entities == null) {
            return scanResult;
        }

        var decryptedEntities = entities.stream()
                .map(this::decryptEntity)
                .toList();

        return scanResult.toBuilder()
                .entities(decryptedEntities)
                .build();
    }

    private PiiEntity encryptEntity(PiiEntity entity) {
        EncryptionMetadata metadata = buildMetadata(entity);

        var encryptedText = encryptionService.encrypt(entity.text(), metadata);
        var encryptedContext = encryptionService.encrypt(entity.context(), metadata);

        return entity.toBuilder()
                .text(encryptedText)
                .context(encryptedContext)
                .build();
    }

    private PiiEntity decryptEntity(PiiEntity entity) {
        EncryptionMetadata metadata = buildMetadata(entity);

        var decryptedText = entity.text();
        if (encryptionService.isEncrypted(entity.text())) {
            decryptedText = encryptionService.decrypt(entity.text(), metadata);
        }

        var decryptedContext = entity.context();
        if (encryptionService.isEncrypted(entity.context())) {
            decryptedContext = encryptionService.decrypt(entity.context(), metadata);
        }

        return entity.toBuilder()
                .text(decryptedText)
                .context(decryptedContext)
                .build();
    }

    private EncryptionMetadata buildMetadata(PiiEntity entity) {
        return new EncryptionMetadata(entity.type(), entity.start(), entity.end());
    }
}

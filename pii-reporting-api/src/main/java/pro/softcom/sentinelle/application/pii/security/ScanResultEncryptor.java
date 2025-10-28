package pro.softcom.sentinelle.application.pii.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionException;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;

import java.util.List;

/**
 * Processor for encrypting/decrypting detectedEntities in ScanResult.
 * Business intent: orchestrate PII encryption in the business flow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScanResultEncryptor {
    private static final int MAX_ENTITIES_SOFT_CAP = 500;

    private final EncryptionService encryptionService;

    /**
     * Encrypts all detected PII entities in the scan result.
     * Business purpose: Secure sensitive data before storage or transmission.
     *
     * @param scanResult the scan result with plaintext PII values
     * @return scan result with encrypted PII values
     * @throws EncryptionException if encryption fails for any entity
     */
    public ScanResult encrypt(ScanResult scanResult) {
        var entities = scanResult.detectedEntities();
        if (entities == null) {
            return scanResult;
        }

        try {
            var encryptedEntities = encryptEntities(entities);
            return scanResult.toBuilder()
                    .detectedEntities(encryptedEntities)
                    .build();
        } catch (EncryptionException e) {
            log.error("Failed to encrypt PII entities for scanId={}, entityCount={}",
                    scanResult.scanId(), entities.size(), e);
            throw e;
        }
    }

    /**
     * Decrypts encrypted PII entities in the scan result.
     * Business purpose: Restore original values for authorized access and display.
     *
     * @param scanResult the scan result with encrypted PII values
     * @return scan result with decrypted PII values
     * @throws EncryptionException if decryption fails for any entity
     */
    public ScanResult decrypt(ScanResult scanResult) {
        var entities = scanResult.detectedEntities();
        if (entities == null) {
            return scanResult;
        }

        try {
            var decryptedEntities = entities.stream()
                    .map(this::decryptEntity)
                    .toList();

            return scanResult.toBuilder()
                    .detectedEntities(decryptedEntities)
                    .build();
        } catch (EncryptionException e) {
            log.error("Failed to decrypt PII entities for scanId={}, entityCount={}",
                    scanResult.scanId(), entities.size(), e);
            throw e;
        }
    }

    /**
     * Encrypts a batch of PII entities.
     * Logs a warning if batch size exceeds soft cap for performance monitoring.
     */
    private List<PiiEntity> encryptEntities(List<PiiEntity> entities) {
        if (entities.size() > MAX_ENTITIES_SOFT_CAP) {
            log.warn("Encrypting {} entities (exceeds soft cap of {}). Consider reviewing batch size or enabling parallelization.",
                    entities.size(), MAX_ENTITIES_SOFT_CAP);
        }

        return entities.stream()
                .map(this::encryptEntity)
                .toList();
    }

    /**
     * Encrypts a single PII entity while preserving its metadata.
     * The metadata is used as Additional Authenticated Data (AAD) to ensure integrity.
     * Note: maskedContext is not encrypted as it contains only masked tokens, not real PII values.
     */
    private PiiEntity encryptEntity(PiiEntity entity) {
        EncryptionMetadata metadata = buildMetadata(entity);

        var encryptedText = encryptionService.encrypt(entity.detectedValue(), metadata);
        var encryptedContext = encryptionService.encrypt(entity.context(), metadata);

        return entity.toBuilder()
                .detectedValue(encryptedText)
                .context(encryptedContext)
                .maskedContext(entity.maskedContext()) // Keep masked context in clear text
                .build();
    }

    /**
     * Decrypts a single PII entity if it's encrypted, otherwise returns it unchanged.
     * The metadata is verified during decryption to ensure integrity.
     * Note: maskedContext is never encrypted, so it's preserved as-is.
     */
    private PiiEntity decryptEntity(PiiEntity entity) {
        EncryptionMetadata metadata = buildMetadata(entity);

        var decryptedText = entity.detectedValue();
        if (encryptionService.isEncrypted(entity.detectedValue())) {
            decryptedText = encryptionService.decrypt(entity.detectedValue(), metadata);
        }

        var decryptedContext = entity.context();
        if (encryptionService.isEncrypted(entity.context())) {
            decryptedContext = encryptionService.decrypt(entity.context(), metadata);
        }

        return entity.toBuilder()
                .detectedValue(decryptedText)
                .context(decryptedContext)
                .maskedContext(entity.maskedContext()) // Preserve masked context unchanged
                .build();
    }

    /**
     * Builds encryption metadata from PII entity for Additional Authenticated Data.
     * This metadata is cryptographically bound to ensure data integrity.
     */
    private EncryptionMetadata buildMetadata(PiiEntity entity) {
        return new EncryptionMetadata(entity.piiType(), entity.startPosition(), entity.endPosition());
    }
}

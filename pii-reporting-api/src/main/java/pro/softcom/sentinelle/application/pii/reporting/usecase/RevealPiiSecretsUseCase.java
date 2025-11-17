package pro.softcom.sentinelle.application.pii.reporting.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.sentinelle.application.pii.reporting.port.in.RevealPiiSecretsPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ReadPiiConfigPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.domain.pii.reporting.PageSecretsResponse;
import pro.softcom.sentinelle.domain.pii.reporting.RevealedSecret;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Use case for revealing PII secrets from scan results.
 * Implements the business logic for controlled access to decrypted PII data.
 */
public class RevealPiiSecretsUseCase implements
    RevealPiiSecretsPort {

    private static final Logger log = LoggerFactory.getLogger(RevealPiiSecretsUseCase.class);

    private final ReadPiiConfigPort readPiiConfigPort;
    private final ScanResultQuery scanResultQuery;

    public RevealPiiSecretsUseCase(
            ReadPiiConfigPort readPiiConfigPort,
            ScanResultQuery scanResultQuery
    ) {
        this.readPiiConfigPort = readPiiConfigPort;
        this.scanResultQuery = scanResultQuery;
    }

    @Override
    public boolean isRevealAllowed() {
        return readPiiConfigPort.isAllowSecretReveal();
    }

    @Override
    public Optional<PageSecretsResponse> revealPageSecrets(String scanId, String pageId) {
        // Business Rule: Check authorization
        if (!readPiiConfigPort.isAllowSecretReveal()) {
            log.warn("[PII_ACCESS] Reveal attempt denied by configuration for pageId={}", pageId);
            throw new SecurityException("Secret revelation is not allowed by configuration");
        }

        log.info("[PII_ACCESS] Reveal request for pageId={}", pageId);

        // Query with automatic decryption (AccessPurpose.USER_DISPLAY)
        List<ScanResult> results = scanResultQuery.listItemEventsDecrypted(
                scanId,
                pageId,
                AccessPurpose.USER_DISPLAY
        );

        if (results.isEmpty()) {
            log.warn("[PII_ACCESS] No results found for pageId={}", pageId);
            return Optional.empty();
        }

        // Extract decrypted secrets
        List<RevealedSecret> secrets = results.stream()
                .filter(Objects::nonNull)
                .flatMap(sr -> Optional.ofNullable(sr.detectedEntities())
                        .orElseGet(List::of)
                        .stream())
                .filter(Objects::nonNull)
                .map(e -> new RevealedSecret(
                        e.startPosition(),
                        e.endPosition(),
                        e.sensitiveValue(),
                        e.sensitiveContext(),
                        e.maskedContext()
                ))
                .toList();

        // Take the first result (should be unique per pageId)
        ScanResult result = results.getFirst();
        log.info("[PII_ACCESS] Revealed {} secrets for pageId={} (scanId={})",
                secrets.size(), result.pageId(), result.scanId());

        return Optional.of(new PageSecretsResponse(
                result.scanId(),
                result.pageId(),
                result.pageTitle(),
                secrets
        ));
    }
}

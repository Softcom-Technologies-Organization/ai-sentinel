package pro.softcom.aisentinel.domain.pii.security;

import java.nio.charset.StandardCharsets;

/**
 * PII entity metadata for Additional Authenticated Data (AAD).
 * This metadata is cryptographically bound to the ciphertext via HMAC.
 */
public record EncryptionMetadata(
        String piiType,      // PII type (EMAIL, PHONE, etc.)
        Integer startPosition, // Start position in the text
        Integer endPosition // End position in the text
) {
    /**
     * Serializes the metadata for AAD.
     * Format: piiType|startPosition|endPosition
     * <p>
     * Note: Using pipe delimiter instead of structured format (JSON) because:
     * - AAD is not meant to be parsed, only verified
     * - Simple format reduces attack surface
     * - Deterministic serialization ensures consistency
     */
    public byte[] toAadBytes() {
        String aad = String.format("%s|%d|%d",
                piiType != null ? piiType : "",
                startPosition != null ? startPosition : 0,
                endPosition != null ? endPosition : 0);
        return aad.getBytes(StandardCharsets.UTF_8);
    }
}

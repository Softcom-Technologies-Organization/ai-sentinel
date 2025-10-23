package pro.softcom.sentinelle.domain.pii.security;

import java.nio.charset.StandardCharsets;

/**
 * PII entity metadata for Additional Authenticated Data (AAD).
 * This metadata is cryptographically bound to the ciphertext via HMAC.
 */
public record EncryptionMetadata(
        String type,      // PII type (EMAIL, PHONE, etc.)
        Integer positionBegin, // Start position in the text
        Integer positionEnd // End position in the text
) {
    /**
     * Serializes the metadata for AAD.
     * Format: type|positionBegin|positionEnd
     * <p>
     * Note: Using pipe delimiter instead of structured format (JSON) because:
     * - AAD is not meant to be parsed, only verified
     * - Simple format reduces attack surface
     * - Deterministic serialization ensures consistency
     */
    public byte[] toAadBytes() {
        String aad = String.format("%s|%d|%d",
                type != null ? type : "",
                positionBegin != null ? positionBegin : 0,
                positionEnd != null ? positionEnd : 0);
        return aad.getBytes(StandardCharsets.UTF_8);
    }
}

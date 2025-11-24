package pro.softcom.aisentinel.domain.pii.security;

/**
 * Port for encryption/decryption of sensitive data with strong authentication.
 */
public interface EncryptionService {

    /**
     * Encrypts a sensitive value with authenticated metadata.
     *
     * @param plaintext plaintext value
     * @param metadata entity metadata (type, position, selector) for AAD
     * @return encrypted text in format ENC:v{version}:...
     * @throws EncryptionException if encryption fails
     */
    String encrypt(String plaintext, EncryptionMetadata metadata) throws EncryptionException;

    /**
     * Decrypts a sensitive value while verifying integrity.
     *
     * @param ciphertext encrypted text (format ENC:v{version}:...)
     * @param metadata entity metadata for AAD verification
     * @return plaintext value
     * @throws EncryptionException if decryption or verification fails
     */
    String decrypt(String ciphertext, EncryptionMetadata metadata) throws EncryptionException;

    /**
     * Checks if a value is encrypted.
     */
    default boolean isEncrypted(String value) {
        return value != null && value.startsWith("ENC:");
    }
}

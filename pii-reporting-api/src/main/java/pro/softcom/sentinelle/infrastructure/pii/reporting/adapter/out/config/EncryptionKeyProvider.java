package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.config;

import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Provides the AES encryption key (KEK) used for encrypting and decrypting PII data.
 *
 * <p>This component loads the key from a configured environment variable and validates
 * its integrity and size (256 bits / 32 bytes). It represents the default production
 * strategy for securely loading encryption keys.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>The encyption key is provided via {@link EncryptionConfig#kekPiiEncryptionKey()}.</li>
 *   <li>The expected format is Base64-encoded 256-bit key.</li>
 * </ul>
 *
 * <p><strong>Throws:</strong></p>
 * <ul>
 *   <li>{@link IllegalStateException} if the key is missing, malformed, or has an invalid length.</li>
 * </ul>
 */
@Component
public class EncryptionKeyProvider {
    private static final int REQUIRED_KEY_LENGTH = 32; // 256 bits
    private final EncryptionConfig config;
    @Getter
    private final SecretKey key;

    public EncryptionKeyProvider(EncryptionConfig config) {
        this.config = config;
        this.key = parseKeyFromBase64(config.kekPiiEncryptionKey());
    }

    private SecretKey parseKeyFromBase64(String keyBase64) {
        if (StringUtils.isBlank(keyBase64)) {
            throw new IllegalStateException("Missing KEK environment variable: " + this.config.kekPiiEncryptionKey());
        }

        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64.trim());
            if (keyBytes.length != REQUIRED_KEY_LENGTH) {
                throw new IllegalStateException(
                        "Invalid key length: " + keyBytes.length + " bytes. Expected " + REQUIRED_KEY_LENGTH + " bytes (256 bits)"
                );
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid base64 encoding for encryption key", e);
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

}

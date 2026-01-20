package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.security.CryptographicOperationException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.config.EncryptionKeyProvider;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption adapter with HKDF key derivation.
 * 
 * <h2>Security Architecture</h2>
 * <ul>
 *   <li><b>Encryption</b>: AES-256-GCM (authentication and confidentiality)</li>
 *   <li><b>Key Derivation</b>: HKDF-SHA256 with unique salt</li>
 *   <li><b>Integrity Protection</b>: 128-bit GCM tag + AAD</li>
 * </ul>
 * 
 * <h2>Token Format</h2>
 * <pre>
 * ENC:v1:&lt;salt_base64&gt;:&lt;iv_base64&gt;:&lt;ciphertext_with_tag_base64&gt;
 * </pre>
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li>Unique 256-bit salt per encrypted value</li>
 *   <li>Random 96-bit IV (recommended for GCM)</li>
 *   <li>DEK derived via HKDF = per-record isolation</li>
 *   <li>AAD cryptographically binds metadata (type, position) to ciphertext</li>
 *   <li>GCM tag automatically verifies integrity</li>
 * </ul>
 */
@Component
@Slf4j
public class AesGcmEncryptionAdapter implements EncryptionService {
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HKDF_MAC_ALGORITHM = "HmacSHA256";

    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes (recommended)
    private static final int SALT_LENGTH = 32; // bytes (256 bits)
    private static final int DEK_LENGTH = 32; // bytes (256 bits)
    private static final String PREFIX = "ENC:v1:";

    // HKDF context
    private static final byte[] HKDF_INFO_DEK = "pii-encryption-dek".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionAdapter(EncryptionKeyProvider keyProvider) {
        this.key = keyProvider.getKey();
    }

    @Override
    public String encrypt(String plaintext, EncryptionMetadata metadata) throws EncryptionException {
        byte[] dek = null;
        byte[] kekBytes = null;
        try {
            kekBytes = this.key.getEncoded();
            if (kekBytes == null) {
                throw new EncryptionException("KEK is not extractable");
            }

            byte[] salt = generateSalt();
            byte[] iv = generateIv();
            dek = hkdf(kekBytes, salt);
            byte[] aad = buildAad(metadata);
            byte[] ciphertext = encryptWithGcm(dek, iv, aad, plaintext);

            return formatToken(salt, iv, ciphertext);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getClass().getSimpleName());
            throw new EncryptionException("Failed to encrypt value", e);
        } finally {
            wipeMemory(dek, kekBytes);
        }
    }

    @Override
    public String decrypt(String ciphertext, EncryptionMetadata metadata) throws EncryptionException {
        byte[] dek = null;
        byte[] kekBytes = null;
        try {
            if (!isEncrypted(ciphertext)) {
                return ciphertext;
            }

            EncryptedData data = parseToken(ciphertext);

            kekBytes = this.key.getEncoded();
            if (kekBytes == null) {
                throw new EncryptionException("KEK is not extractable");
            }

            dek = hkdf(kekBytes, data.salt);
            byte[] aad = buildAad(metadata);
            byte[] plaintext = decryptWithGcm(dek, data.iv, aad, data.ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getClass().getSimpleName());
            throw new EncryptionException("Failed to decrypt value", e);
        } finally {
            wipeMemory(dek, kekBytes);
        }
    }

    /**
     * HKDF (RFC 5869): derive a sub-key from KEK, salt, and context (info).
     */
    private byte[] hkdf(byte[] ikm, byte[] salt) throws CryptographicOperationException {
        try {
            // Extract: PRK = HMAC(salt, IKM)
            Mac mac = Mac.getInstance(HKDF_MAC_ALGORITHM);
            mac.init(new SecretKeySpec(salt, HKDF_MAC_ALGORITHM));
            byte[] prk = mac.doFinal(ikm);

            try {
                // Expand (single block): OKM = HMAC(PRK, info || 0x01)
                // Single block is sufficient for AES-256 (32 bytes)
                mac.init(new SecretKeySpec(prk, HKDF_MAC_ALGORITHM));
                mac.update(AesGcmEncryptionAdapter.HKDF_INFO_DEK);
                mac.update((byte) 0x01);
                byte[] okm = mac.doFinal();

                return okm.length == AesGcmEncryptionAdapter.DEK_LENGTH
                    ? okm : Arrays.copyOf(okm, AesGcmEncryptionAdapter.DEK_LENGTH);
            } finally {
                Arrays.fill(prk, (byte) 0);
            }
        } catch (Exception e) {
            throw new CryptographicOperationException("HKDF key derivation failed", e);
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    /**
     * Generates a random IV for GCM mode.
     */
    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Builds AAD from PII metadata.
     */
    private byte[] buildAad(EncryptionMetadata metadata) {
        return metadata != null ? metadata.toAadBytes() : new byte[0];
    }

    /**
     * Encrypts plaintext using AES-GCM.
     */
    private byte[] encryptWithGcm(byte[] dek, byte[] iv, byte[] aad, String plaintext) throws CryptographicOperationException {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKey dekKey = new SecretKeySpec(dek, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, dekKey, spec);
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CryptographicOperationException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Decrypts ciphertext using AES-GCM (tag verification is automatic).
     */
    private byte[] decryptWithGcm(byte[] dek, byte[] iv, byte[] aad, byte[] ciphertext) throws CryptographicOperationException {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKey dekKey = new SecretKeySpec(dek, "AES");
            cipher.init(Cipher.DECRYPT_MODE, dekKey, spec);
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptographicOperationException("AES-GCM decryption failed", e);
        }
    }

    /**
     * Formats the encrypted token.
     * Format: ENC:v1:&lt;salt_b64&gt;:&lt;iv_b64&gt;:&lt;ciphertext_b64&gt;
     */
    private String formatToken(byte[] salt, byte[] iv, byte[] ciphertext) {
        Base64.Encoder encoder = Base64.getEncoder();
        return String.format("%s%s:%s:%s",
                PREFIX,
                encoder.encodeToString(salt),
                encoder.encodeToString(iv),
                encoder.encodeToString(ciphertext)
        );
    }

    /**
     * Parses the encrypted token: ENC:v1:&lt;salt&gt;:&lt;iv&gt;:&lt;ciphertext&gt;.
     */
    private EncryptedData parseToken(String encrypted) {
        if (!encrypted.startsWith(PREFIX)) {
            throw new EncryptionException("Invalid encrypted format: missing prefix");
        }

        String[] parts = encrypted.substring(PREFIX.length()).split(":", 3);
        if (parts.length != 3) {
            throw new EncryptionException("Invalid encrypted format - expected 3 parts");
        }

        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[0]);
            byte[] iv = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);

            validateParsedData(salt, iv, ciphertext);

            return new EncryptedData(salt, iv, ciphertext);
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("Failed to parse encrypted data", e);
        }
    }

    /**
     * Validates parsed data lengths.
     */
    private void validateParsedData(byte[] salt, byte[] iv, byte[] ciphertext) {
        if (salt.length != SALT_LENGTH) {
            throw new EncryptionException("Invalid salt length: " + salt.length);
        }
        if (iv.length != GCM_IV_LENGTH) {
            throw new EncryptionException("Invalid IV length: " + iv.length);
        }
        if (ciphertext.length < 16) {
            throw new EncryptionException("Invalid ciphertext length: " + ciphertext.length);
        }
    }

    /**
     * Securely wipes sensitive keys from memory.
     */
    private void wipeMemory(byte[]... arrays) {
        for (byte[] array : arrays) {
            if (array != null) {
                Arrays.fill(array, (byte) 0);
            }
        }
    }

    private record EncryptedData(
            byte[] salt,
            byte[] iv,
            byte[] ciphertext
    ) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EncryptedData(byte[] salt1, byte[] iv1, byte[] ciphertext1))) return false;
            return Arrays.equals(salt, salt1) &&
                   Arrays.equals(iv, iv1) &&
                   Arrays.equals(ciphertext, ciphertext1);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(salt);
            result = 31 * result + Arrays.hashCode(iv);
            result = 31 * result + Arrays.hashCode(ciphertext);
            return result;
        }

        @Override
        public @NonNull String toString() {
            return "EncryptedData{" +
                    "salt=" + Arrays.toString(salt) +
                    ", iv=" + Arrays.toString(iv) +
                    ", ciphertext=" + Arrays.toString(ciphertext) +
                    '}';
        }
    }
}

package pro.softcom.sentinelle.infrastructure.pii.security.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test implementation of KeyLoader that provides a fixed encryption key for testing.
 * <p>
 * Business intent: Enable reliable, reproducible integration tests without requiring
 * external environment configuration.
 * </p>
 * <p>
 * This implementation is only active when the "test" profile is enabled and provides
 * a valid 256-bit (32-byte) AES key for testing encryption functionality.
 * </p>
 */
@Component
@Profile("test")
public class TestKeyLoader implements KeyLoader {

    // Fixed 32-byte test key (256 bits for AES-256)
    // Base64 encoding of: 0123456789ABCDEF0123456789ABCDEF (32 bytes)
    @SuppressWarnings("java:S2068") // this is a non-sensitive test key
    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";

    @Override
    public String loadKey(String envVariable) {
        // Ignore the environment variable in test mode
        // Always return the fixed test key
        return TEST_KEY_BASE64;
    }
}

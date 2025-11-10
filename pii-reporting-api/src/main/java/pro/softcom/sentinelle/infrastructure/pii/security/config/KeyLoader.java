package pro.softcom.sentinelle.infrastructure.pii.security.config;

/**
 * Interface for loading an encryption key from various sources.
 * Business intent: enables injection of different key loading strategies
 * (environment variables, files, vaults, etc.) without changing business logic.
 */
@FunctionalInterface
public interface KeyLoader {
    /**
     * Loads a key from a configured source.
     *
     * @param variableName the name of the variable or key to load
     * @return the Base64-encoded key value
     */
    String loadKey(String variableName);
}
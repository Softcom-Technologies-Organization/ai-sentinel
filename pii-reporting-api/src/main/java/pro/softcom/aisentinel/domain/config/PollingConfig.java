package pro.softcom.aisentinel.domain.config;

/**
 * Domain model representing polling configuration for the frontend.
 * 
 * <p>Business Rule: The frontend polling interval should be coordinated with 
 * the backend refresh interval to avoid unnecessary requests.</p>
 */
public record PollingConfig(
        long backendRefreshIntervalMs,
        long frontendPollingIntervalMs
) {
    public PollingConfig {
        if (backendRefreshIntervalMs <= 0) {
            throw new IllegalArgumentException("backendRefreshIntervalMs must be positive");
        }
        if (frontendPollingIntervalMs <= 0) {
            throw new IllegalArgumentException("frontendPollingIntervalMs must be positive");
        }
    }
}

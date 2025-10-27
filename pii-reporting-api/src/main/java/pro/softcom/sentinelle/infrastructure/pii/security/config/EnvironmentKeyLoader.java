package pro.softcom.sentinelle.infrastructure.pii.security.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementation of KeyLoader that loads keys from environment variables.
 * Business intent: default strategy for loading keys in production via System.getenv().
 * <p>
 * This implementation is active in all profiles except "test", where TestKeyLoader
 * is used instead to provide a fixed test key.
 * </p>
 */
@Component
@Profile("!test")
public class EnvironmentKeyLoader implements KeyLoader {
    
    @Override
    public String loadKey(String variableName) {
        return System.getenv(variableName);
    }
}

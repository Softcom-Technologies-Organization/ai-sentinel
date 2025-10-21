package pro.softcom.sentinelle.infrastructure.pii.security.config;

import org.springframework.stereotype.Component;

/**
 * Implementation of KeyLoader that loads keys from environment variables.
 * Business intent: default strategy for loading keys in production via System.getenv().
 */
@Component
public class EnvironmentKeyLoader implements KeyLoader {
    
    @Override
    public String loadKey(String variableName) {
        return System.getenv(variableName);
    }
}

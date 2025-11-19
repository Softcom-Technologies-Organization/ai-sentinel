package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pii-encryption")
public record EncryptionConfig(
        String kekPiiEncryptionKey
) { }

package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "pii-encryption")
@Validated
public record EncryptionConfig(
        String kekPiiEncryptionKey
) { }

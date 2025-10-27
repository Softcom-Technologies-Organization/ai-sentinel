package pro.softcom.sentinelle.infrastructure.pii.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "pii-encryption")
@Validated
public record EncryptionConfig(
        @NotBlank(message = "KEK env variable cannot be blank")
        String kekEnvVariable
) {
}

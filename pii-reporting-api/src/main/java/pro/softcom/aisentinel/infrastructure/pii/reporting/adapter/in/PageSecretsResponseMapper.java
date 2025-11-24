package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import java.util.List;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.PageSecretsResponse;
import pro.softcom.aisentinel.domain.pii.reporting.RevealedSecret;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageSecretsResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.PiiAccessController.RevealedSecretDto;

/**
 * Mapper for transforming domain models to REST DTOs.
 * Responsibility: Convert PageSecretsResponse (domain) to PageSecretsResponseDto (infrastructure/REST).
 */
@Component
public class PageSecretsResponseMapper {

    /**
     * Maps a domain PageSecretsResponse to a REST DTO.
     *
     * @param response the domain response
     * @return the mapped DTO
     */
    public PageSecretsResponseDto toDto(PageSecretsResponse response) {
        List<RevealedSecretDto> secretDtos = response.secrets().stream()
                .map(this::toSecretDto)
                .toList();

        return new PageSecretsResponseDto(
                response.scanId(),
                response.pageId(),
                response.pageTitle(),
                secretDtos
        );
    }

    /**
     * Maps a domain RevealedSecret to a REST DTO.
     *
     * @param secret the domain secret
     * @return the mapped DTO
     */
    private RevealedSecretDto toSecretDto(RevealedSecret secret) {
        return new RevealedSecretDto(
                secret.startPosition(),
                secret.endPosition(),
                secret.sensitiveValue(),
                secret.sensitiveContext(),
                secret.maskedContext()
        );
    }
}

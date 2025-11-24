package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluencePageDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSearchResult;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpacesResponseDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluencePageMapper;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluenceSpaceMapper;

@Slf4j
public class ConfluenceResponseParser {

    private final ObjectMapper objectMapper;

    public ConfluenceResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ConfluencePage> deserializePageDto(String responseBody) {
        try {
            var pageDto = objectMapper.readValue(responseBody, ConfluencePageDto.class);
            return Optional.of(ConfluencePageMapper.toDomain(pageDto));
        } catch (Exception e) {
            log.error("Erreur lors de la désérialisation de la page", e);
            return Optional.empty();
        }
    }

    public List<ConfluencePage> deserializeSearchResults(String responseBody) {
        try {
            var searchResult = objectMapper.readValue(responseBody, ConfluenceSearchResult.class);
            return searchResult.results().stream()
                .map(ConfluencePageMapper::toDomain)
                .toList();
        } catch (Exception e) {
            log.error("Erreur lors de la désérialisation des résultats", e);
            return List.of();
        }
    }

    public ConfluencePage deserializeUpdatedPage(String responseBody) {
        try {
            var updatedPage = objectMapper.readValue(responseBody, ConfluencePageDto.class);
            return ConfluencePageMapper.toDomain(updatedPage);
        } catch (Exception e) {
            log.error("Erreur lors de la désérialisation de la page mise à jour", e);
            throw new ConfluenceDeserializationException("Désérialisation impossible", e);
        }
    }

    public Optional<ConfluenceSpace> deserializeSpaceDto(String responseBody) {
        try {
            var spaceDto = objectMapper.readValue(responseBody, ConfluenceSpaceDto.class);
            return Optional.of(ConfluenceSpaceMapper.toDomain(spaceDto));
        } catch (Exception e) {
            log.error("Erreur lors de la désérialisation de l'espace", e);
            return Optional.empty();
        }
    }

    public ConfluenceSpacesResponseDto deserializeSpacesResponse(String responseBody) throws IOException {
        return objectMapper.readValue(responseBody, ConfluenceSpacesResponseDto.class);
    }

    public List<ConfluenceSpace> convertSpaceDtosToSpaces(List<ConfluenceSpaceDto> spaceDtos) {
        return spaceDtos.stream()
            .map(ConfluenceSpaceMapper::toDomain)
            .toList();
    }
}

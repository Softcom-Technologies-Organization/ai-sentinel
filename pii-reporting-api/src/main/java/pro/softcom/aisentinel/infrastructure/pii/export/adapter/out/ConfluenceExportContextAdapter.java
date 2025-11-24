package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceNotFoundException;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpaceDataOwner;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.export.DataSourceContact;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

/**
 * Adapts Confluence spaces to export contexts.
 * This adapter converts Confluence-specific domain objects into platform-agnostic export contexts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfluenceExportContextAdapter implements ReadExportContextPort {

    private final ConfluenceSpaceRepository confluenceSpaceRepository;
    private final ConfluenceClient confluenceClient;

    @Override
    public ExportContext findContext(SourceType sourceType, String sourceIdentifier) {
        if (sourceType != SourceType.CONFLUENCE) {
            throw new UnsupportedSourceTypeException(sourceType.getValue());
        }

        log.debug("Retrieving export context for Confluence space: {}", sourceIdentifier);

        try {
            ConfluenceSpace confluenceSpace = confluenceSpaceRepository.findByKey(sourceIdentifier)
                    .orElseThrow(() -> new ConfluenceSpaceNotFoundException(sourceIdentifier));

            List<DataSourceContact> contacts = extractDataSourceContacts(confluenceSpace, sourceIdentifier);
            
            return ExportContext.builder()
                    .reportName(confluenceSpace.name())
                    .reportIdentifier(confluenceSpace.key())
                    .sourceUrl(confluenceSpace.url())
                    .contacts(contacts)
                    .additionalMetadata(buildMetadata(confluenceSpace))
                    .build();
        } catch (ConfluenceSpaceNotFoundException e) {
            throw new ExportContextNotFoundException(sourceType.getValue(), sourceIdentifier, e);
        }
    }

    /**
     * Extracts contacts from space, loading data owners from API if not already loaded.
     * Uses pattern matching to handle the two states of DataOwners.
     */
    private List<DataSourceContact> extractDataSourceContacts(ConfluenceSpace confluenceSpace, String sourceIdentifier) {
        return switch (confluenceSpace.dataOwners()) {
            case DataOwners.NotLoaded() -> {
                log.debug("Data owners not loaded, fetching from Confluence API for space: {}", sourceIdentifier);
                List<ConfluenceSpaceDataOwner> owners = loadDataOwnersFromApi(sourceIdentifier);
                yield mapToContacts(owners);
            }
            case DataOwners.Loaded(var owners) -> {
                log.debug("Using already loaded data owners for space: {}", sourceIdentifier);
                yield mapToContacts(owners);
            }
        };
    }

    /**
     * Loads data owners from Confluence API with permissions expansion.
     */
    private List<ConfluenceSpaceDataOwner> loadDataOwnersFromApi(String spaceKey) {
        return confluenceClient.getSpaceWithPermissions(spaceKey)
                .thenApply(optionalConfluenceSpace -> optionalConfluenceSpace
                        .map(confluenceSpace -> switch (confluenceSpace.dataOwners()) {
                            case DataOwners.NotLoaded() -> List.<ConfluenceSpaceDataOwner>of();
                            case DataOwners.Loaded(var owners) -> owners;
                        })
                        .orElse(List.of())
                )
                .join();
    }

    private List<DataSourceContact> mapToContacts(List<ConfluenceSpaceDataOwner> dataOwners) {
        if (dataOwners == null || dataOwners.isEmpty()) {
            return List.of();
        }

        return dataOwners.stream()
                .map(owner -> new DataSourceContact(owner.displayName(), owner.email()))
                .toList();
    }

    private Map<String, String> buildMetadata(ConfluenceSpace confluenceSpace) {
        return Map.of(
                "spaceId", confluenceSpace.id(),
                "spaceType", confluenceSpace.type().getValue(),
                "spaceStatus", confluenceSpace.status().getValue()
        );
    }
}

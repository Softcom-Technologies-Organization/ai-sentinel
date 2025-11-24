package pro.softcom.aisentinel.application.confluence.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceCacheException;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

/**
 * Rafraîchit le cache des espaces Confluence sur demande.
 * Service applicatif pur (agnostique Spring): aucune annotation/framework.
 * L'ordonnancement (planification périodique) est délégué à l'infrastructure.
 */
@RequiredArgsConstructor
@Slf4j
public class ConfluenceSpaceCacheRefreshService {

    private final ConfluenceClient confluenceClient;
    private final ConfluenceSpaceRepository spaceRepository;

    /**
     * Déclenche un rafraîchissement immédiat du cache. Appelé par un scheduler d'infrastructure.
     */
    public void saveNewConfluenceSpaces() {
        log.debug("Starting background refresh of Confluence spaces cache");

        try {
            var future = confluenceClient.getAllSpaces();
            List<ConfluenceSpace> spaces = (future != null) ? future.join() : List.of();

            if (!spaces.isEmpty()) {
                spaceRepository.saveAll(spaces);
                log.info("Successfully refreshed cache with {} Confluence spaces", spaces.size());
            } else {
                log.warn("Refresh returned empty or null space list - cache not updated");
            }
        } catch (ConfluenceSpaceCacheException e) {
            log.error("Failed to refresh Confluence spaces cache during operation: {} - will retry on next schedule",
                e.getOperation(), e);
        }
    }
}

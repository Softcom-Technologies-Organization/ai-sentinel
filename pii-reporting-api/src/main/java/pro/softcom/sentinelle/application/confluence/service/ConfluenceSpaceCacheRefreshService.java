package pro.softcom.sentinelle.application.confluence.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.softcom.sentinelle.application.confluence.exception.ConfluenceSpaceCacheException;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * Service for asynchronous background refresh of Confluence space cache.
 * Business purpose: keeps cached space data current without impacting UI response times.
 * Runs silently in background, updating the cache periodically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfluenceSpaceCacheRefreshService {

    private final ConfluenceClient confluenceClient;
    private final ConfluenceSpaceRepository spaceRepository;
    private final ConfluenceConfig confluenceConfig;

    @Scheduled(fixedDelayString = "${confluence.cache.refresh-interval-ms:300000}",
               initialDelayString = "${confluence.cache.initial-delay-ms:5000}")
    public void saveNewConfluenceSpaces() {
        log.debug("Starting background refresh of Confluence spaces cache (interval: {}ms)",
                  confluenceConfig.cache().refreshIntervalMs());
        
        try {
            List<ConfluenceSpace> spaces = confluenceClient.getAllSpaces()
                .join();
            
            if (spaces != null && !spaces.isEmpty()) {
                spaceRepository.saveAll(spaces);
                log.info("Successfully refreshed cache with {} Confluence spaces", spaces.size());
            } else {
                log.warn("Refresh returned empty space list - cache not updated");
            }
        } catch (ConfluenceSpaceCacheException e) {
            log.error("Failed to refresh Confluence spaces cache during operation: {} - will retry on next schedule", 
                     e.getOperation(), e);
        }
    }
}

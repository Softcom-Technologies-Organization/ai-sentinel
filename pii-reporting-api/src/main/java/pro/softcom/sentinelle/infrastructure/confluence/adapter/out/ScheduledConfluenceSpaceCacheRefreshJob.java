package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceSpaceCacheRefreshService;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledConfluenceSpaceCacheRefreshJob {

    private final ConfluenceSpaceCacheRefreshService refresher;
    private final ConfluenceConfig confluenceConfig;

    @Scheduled(
        fixedDelayString = "${confluence.cache.refresh-interval-ms:300000}",
        initialDelayString = "${confluence.cache.initial-delay-ms:5000}"
    )
    public void refresh() {
        log.debug("Starting background refresh of Confluence spaces cache (interval: {}ms)",
            confluenceConfig.cache().refreshIntervalMs());
        refresher.saveNewConfluenceSpaces();
    }
}

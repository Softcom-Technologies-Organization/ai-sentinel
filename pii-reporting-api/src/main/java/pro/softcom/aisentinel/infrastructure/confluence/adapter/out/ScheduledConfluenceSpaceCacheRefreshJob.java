package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceSpaceCacheRefreshService;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledConfluenceSpaceCacheRefreshJob {

    private final ConfluenceSpaceCacheRefreshService refresher;

    @Scheduled(
        fixedDelayString = "${ai-sentinel.confluence.cache.refresh-interval-ms:300000}",
        initialDelayString = "${ai-sentinel.confluence.cache.initial-delay-ms:5000}"
    )
    public void refresh() {
        log.debug("Starting background refresh of Confluence spaces cache");
        refresher.saveNewConfluenceSpaces();
    }
}

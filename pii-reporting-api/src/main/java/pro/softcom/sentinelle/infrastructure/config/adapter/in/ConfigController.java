package pro.softcom.sentinelle.infrastructure.config.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * REST Controller for application configuration.
 * Business purpose: exposes configuration values to frontend for synchronization.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuration", description = "Application configuration")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfluenceConfig confluenceConfig;

    @GetMapping("/polling")
    @Operation(summary = "Get frontend polling configuration")
    public PollingConfigResponse getPollingConfig() {
        return new PollingConfigResponse(
            confluenceConfig.cache().refreshIntervalMs(),
            confluenceConfig.polling().intervalMs()
        );
    }

    public record PollingConfigResponse(
        long backendRefreshIntervalMs,
        long frontendPollingIntervalMs
    ) {}
}

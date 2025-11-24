package pro.softcom.aisentinel.infrastructure.config.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.config.port.in.GetPollingConfigPort;
import pro.softcom.aisentinel.domain.config.PollingConfig;

@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuration", description = "Application configuration")
@RequiredArgsConstructor
public class ConfigController {

    private final GetPollingConfigPort getPollingConfigPort;

    @GetMapping("/polling")
    @Operation(summary = "Get frontend polling configuration")
    public PollingConfigResponse getPollingConfig() {
        PollingConfig config = getPollingConfigPort.getPollingConfig();
        return new PollingConfigResponse(
            config.backendRefreshIntervalMs(),
            config.frontendPollingIntervalMs()
        );
    }

    public record PollingConfigResponse(
        long backendRefreshIntervalMs,
        long frontendPollingIntervalMs
    ) {}
}

package pro.softcom.aisentinel.application.config.usecase;

import pro.softcom.aisentinel.application.config.port.in.GetPollingConfigPort;
import pro.softcom.aisentinel.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.aisentinel.domain.config.PollingConfig;

/**
 * Use case for retrieving polling configuration.
 * Orchestrates the retrieval of configuration from the infrastructure layer
 * and presents it as a domain model to the presentation layer.
 */
public class GetPollingConfigUseCase implements GetPollingConfigPort {

    private final ReadConfluenceConfigPort readConfluenceConfigPort;

    public GetPollingConfigUseCase(ReadConfluenceConfigPort readConfluenceConfigPort) {
        this.readConfluenceConfigPort = readConfluenceConfigPort;
    }

    @Override
    public PollingConfig getPollingConfig() {
        return new PollingConfig(
                readConfluenceConfigPort.getCacheRefreshIntervalMs(),
                readConfluenceConfigPort.getPollingIntervalMs()
        );
    }
}

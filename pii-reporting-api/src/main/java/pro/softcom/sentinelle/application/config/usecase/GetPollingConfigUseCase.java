package pro.softcom.sentinelle.application.config.usecase;

import pro.softcom.sentinelle.application.config.port.in.GetPollingConfigPort;
import pro.softcom.sentinelle.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.sentinelle.domain.config.PollingConfig;

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

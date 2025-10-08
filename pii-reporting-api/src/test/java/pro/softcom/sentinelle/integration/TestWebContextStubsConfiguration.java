package pro.softcom.sentinelle.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pro.softcom.sentinelle.application.pii.reporting.port.in.ScanReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceScanSpaceStatus;
import reactor.core.publisher.Flux;

/**
 * Test stubs to allow Spring Web and controllers to start without real persistence/backends.
 */
@TestConfiguration
public class TestWebContextStubsConfiguration {

    @Bean
    @Primary
    public StreamConfluenceScanUseCase streamConfluenceScanUseCaseStub() {
        return new StreamConfluenceScanUseCase() {
            @Override
            public Flux<ScanResult> streamSpace(String spaceKey) {
                return Flux.empty();
            }

            @Override
            public Flux<ScanResult> streamAllSpaces() {
                return Flux.empty();
            }
        };
    }

    @Bean
    @Primary
    public ScanReportingUseCase scanResultUseCaseStub() {
        return new ScanReportingUseCase() {
            @Override
            public java.util.Optional<LastScanMeta> getLatestScan() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<ConfluenceScanSpaceStatus> getLatestSpaceScanStateList(String scanId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<ScanResult> getLatestScanItems() {
                return java.util.List.of();
            }
        };
    }
}

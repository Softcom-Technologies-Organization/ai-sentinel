package pro.softcom.aisentinel.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pro.softcom.aisentinel.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceScanPort;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;
import reactor.core.publisher.Flux;

/**
 * Test stubs to allow Spring Web and controllers to start without real persistence/backends.
 */
@TestConfiguration
public class TestWebContextStubsConfiguration {

    @Bean
    @Primary
    public StreamConfluenceScanPort streamConfluenceScanUseCaseStub() {
        return new StreamConfluenceScanPort() {
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
    public ScanReportingPort scanResultUseCaseStub() {
        return new ScanReportingPort() {
            @Override
            public java.util.Optional<LastScanMeta> getLatestScan() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<ConfluenceSpaceScanState> getLatestSpaceScanStateList(
                String scanId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<ScanResult> getLatestSpaceScanResultList() {
                return java.util.List.of();
            }

            @Override
            public java.util.Optional<ScanReportingSummary> getScanReportingSummary(
                String scanId) {
                return java.util.Optional.empty();
            }
        };
    }
}

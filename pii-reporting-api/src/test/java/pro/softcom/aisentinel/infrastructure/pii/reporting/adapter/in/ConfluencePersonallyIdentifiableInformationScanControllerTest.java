package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import pro.softcom.aisentinel.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.usecase.StreamConfluenceScanUseCase;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ConfluenceContentScanResultEventDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanResultToScanEventMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Unit tests for WebFluxStreamingScanController focusing on SSE mapping and keepalive.
 */
@ExtendWith(MockitoExtension.class)
class ConfluencePersonallyIdentifiableInformationScanControllerTest {

    private StreamConfluenceScanUseCase streamConfluenceScanUseCase;

    private ConfluencePersonallyIdentifiableInformationScanController controller;

    @BeforeEach
    void setUp() {
        streamConfluenceScanUseCase = mock(StreamConfluenceScanUseCase.class);
        controller = new ConfluencePersonallyIdentifiableInformationScanController(streamConfluenceScanUseCase, mock(
            StreamConfluenceResumeScanPort.class), mock(PauseScanPort.class), new ScanResultToScanEventMapper());
    }

    @Test
    @DisplayName("streamSpaceScan - maps eventType to SSE event and preserves data")
    void streamSpaceScan_mapsEvents() {
        String spaceKey = "S1";
        Flux<ConfluenceContentScanResult> events = Flux.just(
            ConfluenceContentScanResult.builder().eventType(ScanEventType.START.toJson()).build(),
            ConfluenceContentScanResult.builder().eventType(ScanEventType.PAGE_START.toJson()).build(),
            ConfluenceContentScanResult.builder().eventType(ScanEventType.ITEM.toJson()).build(),
            ConfluenceContentScanResult.builder().eventType(ScanEventType.PAGE_COMPLETE.toJson()).build(),
            ConfluenceContentScanResult.builder().eventType(ScanEventType.COMPLETE.toJson()).build()
        );
        when(streamConfluenceScanUseCase.streamSpace(anyString())).thenReturn(events);

        Flux<ServerSentEvent<@NonNull ConfluenceContentScanResultEventDto>> flux = controller.streamSpaceScan(spaceKey)
                .filter(sse -> List.of(ScanEventType.START.toJson(),ScanEventType.PAGE_START.toJson(),ScanEventType.ITEM.toJson(),ScanEventType.PAGE_COMPLETE.toJson(),ScanEventType.COMPLETE.toJson()).contains(sse.event()))
                .take(5)
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(sse -> ScanEventType.START.toJson().equals(sse.event()) && sse.data() != null)
                .expectNextMatches(sse -> ScanEventType.PAGE_START.toJson().equals(sse.event()) && sse.data() != null)
                .expectNextMatches(sse -> ScanEventType.ITEM.toJson().equals(sse.event()) && sse.data() != null)
                .expectNextMatches(sse -> ScanEventType.PAGE_COMPLETE.toJson().equals(sse.event()) && sse.data() != null)
                .expectNextMatches(sse -> ScanEventType.COMPLETE.toJson().equals(sse.event()) && sse.data() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSpaceScan - space not found emits error")
    void streamSpaceScan_spaceNotFound() {
        when(streamConfluenceScanUseCase.streamSpace("MISS")).thenReturn(Flux.just(
            ConfluenceContentScanResult.builder().spaceKey("MISS").eventType(ScanEventType.ERROR.toJson()).message("Espace non trouvé").build()
        ));

        Flux<ServerSentEvent<@NonNull ConfluenceContentScanResultEventDto>> flux = controller.streamSpaceScan("MISS")
                .filter(sse -> ScanEventType.ERROR.toJson().equals(sse.event()))
                .take(1)
                .timeout(Duration.ofSeconds(3));

        StepVerifier.create(flux)
                .assertNext(sse -> {
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().eventType()).isEqualTo(ScanEventType.ERROR);
                    assertThat(sse.data().message()).isEqualTo("Espace non trouvé");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpacesScan - no spaces emits error then multi_complete")
    void streamAllSpacesScan_noSpaces() {
        when(streamConfluenceScanUseCase.streamAllSpaces()).thenReturn(Flux.just(
            ConfluenceContentScanResult.builder().eventType(ScanEventType.ERROR.toJson()).build(),
            ConfluenceContentScanResult.builder().eventType(ScanEventType.MULTI_COMPLETE.toJson()).build()
        ));

        Flux<ServerSentEvent<@NonNull ConfluenceContentScanResultEventDto>> flux = controller.streamAllSpacesScan(null)
                .filter(sse -> List.of(ScanEventType.ERROR.toJson(),ScanEventType.MULTI_COMPLETE.toJson()).contains(sse.event()))
                .take(2)
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(sse -> ScanEventType.ERROR.toJson().equals(sse.event()))
                .expectNextMatches(sse -> ScanEventType.MULTI_COMPLETE.toJson().equals(sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSpaceScan - emits keepalive using virtual time")
    void streamSpaceScan_keepaliveVirtualTime() {
        when(streamConfluenceScanUseCase.streamSpace("S-KEEP")).thenReturn(Flux.never());

        StepVerifier.withVirtualTime(() ->
                controller.streamSpaceScan("S-KEEP")
                        .filter(sse -> ScanEventType.KEEPALIVE.toJson().equals(sse.event()))
                        .take(1)
        )
                .thenAwait(Duration.ofSeconds(16))
                .expectNextMatches(sse -> ScanEventType.KEEPALIVE.toJson().equals(sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpacesScan - emits keepalive using virtual time")
    void streamAllSpacesScan_keepaliveVirtualTime() {
        when(streamConfluenceScanUseCase.streamAllSpaces()).thenReturn(Flux.never());

        StepVerifier.withVirtualTime(() ->
                controller.streamAllSpacesScan(null)
                        .filter(sse -> ScanEventType.KEEPALIVE.toJson().equals(sse.event()))
                        .take(1)
        )
                .thenAwait(Duration.ofSeconds(16))
                .expectNextMatches(sse -> ScanEventType.KEEPALIVE.toJson().equals(sse.event()))
                .verifyComplete();
    }
}

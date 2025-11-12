package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper.ScanResultToScanEventMapper;
import reactor.core.publisher.Flux;

/**
 * SSE WebFlux controller that streams a space scan as Server-Sent Events.
 * Business intent: provide a browser-friendly live stream (Flux of ServerSentEvent) without using SseEmitter.
 * This endpoint mirrors the behavior of starting a scan for a space while emitting events progressively.
 */
@RestController
@RequestMapping("/api/v1/stream")
@Tag(name = "Streaming (WebFlux)", description = "Confluence scan streaming via SSE (WebFlux)")
@RequiredArgsConstructor
@Slf4j
public class ScanController {

    private final StreamConfluenceScanUseCase streamConfluenceScanUseCase;
    private final StreamConfluenceResumeScanUseCase streamConfluenceResumeScanUseCase;
    private final ScanResultToScanEventMapper scanResultToScanEventMapper;

    @GetMapping(value = "/confluence/space/{spaceKey}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream Confluence space scan (SSE)")
    @ApiResponse(responseCode = "200", description = "SSE stream started")
    @ApiResponse(responseCode = "404", description = "Space not found")
    public Flux<ServerSentEvent<@NonNull ScanEventDto>> streamSpaceScan(
            @Parameter(description = "Key of the space to scan") @PathVariable String spaceKey
    ) {
        log.info("[SSE] Starting stream for space {}", spaceKey);

        Flux<ServerSentEvent<@NonNull ScanEventDto>> keepalive = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<ScanEventDto>builder()
                        .event(ScanEventType.KEEPALIVE.toJson())
                        .comment("ping")
                        .build());

        Flux<ServerSentEvent<@NonNull ScanEventDto>> data = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .map(ev -> ServerSentEvent.<ScanEventDto>builder()
                        .event(ev.eventType())
                        .data(scanResultToScanEventMapper.toDto(ev))
                        .build());

        return Flux.merge(data, keepalive)
                .doFinally(sig -> log.info("[SSE] Connection closed for space {} (signal={})", spaceKey, sig));
    }


    /**
     * Endpoint that scans the entire Confluence base space-by-space and streams results.
     * Provides a single stream that sequentially processes each space and emits
     * the same per-page events as the single-space scan endpoint (start, page_start, item, page_complete, complete).
     */
    @GetMapping(value = "/confluence/spaces/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream scan of all Confluence spaces (SSE)")
    @ApiResponse(responseCode = "200", description = "SSE stream started")
    public Flux<ServerSentEvent<@NonNull ScanEventDto>> streamAllSpacesScan(
            @RequestParam(name = "scanId", required = false) String scanId
    ) {
        boolean resume = scanId != null && !scanId.isBlank();
        log.info("[SSE] Starting multi-space stream{}", resume ? " (resume scanId=" + scanId + ")" : "");

        Flux<ServerSentEvent<@NonNull ScanEventDto>> keepalive = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<ScanEventDto>builder()
                        .event(ScanEventType.KEEPALIVE.toJson())
                        .comment("ping")
                        .build());

        Flux<ServerSentEvent<@NonNull ScanEventDto>> data;
        if (resume) {
            // When resuming, attach to resumeAllSpaces(scanId) and wrap with multi_start/multi_complete for UI parity
            Flux<ServerSentEvent<@NonNull ScanEventDto>> header = Flux.just(
                    ServerSentEvent.<ScanEventDto>builder()
                            .event(ScanEventType.MULTI_START.toJson())
                            .data(ScanEventDto.builder()
                                    .scanId(scanId)
                                    .eventType(ScanEventType.MULTI_START)
                                    .build())
                            .build()
            );
            Flux<ServerSentEvent<@NonNull ScanEventDto>> body = streamConfluenceResumeScanUseCase.resumeAllSpaces(scanId)
                    .map(ev -> ServerSentEvent.<ScanEventDto>builder()
                            .event(ev.eventType())
                            .data(scanResultToScanEventMapper.toDto(ev))
                            .build());
            Flux<ServerSentEvent<@NonNull ScanEventDto>> footer = Flux.just(
                    ServerSentEvent.<ScanEventDto>builder().event(ScanEventType.MULTI_COMPLETE.toJson()).build()
            );
            data = Flux.concat(header, body, footer);
        } else {
            // Fresh multi-space scan: rely on use case framing (MULTI_START/MULTI_COMPLETE with scanId)
            // and delay the subscription a bit so EventSource listeners are attached before the first event.
            data = streamConfluenceScanUseCase.streamAllSpaces()
                .delaySubscription(Duration.ofMillis(50))
                .map(ev -> ServerSentEvent.<ScanEventDto>builder()
                    .event(ev.eventType())
                    .data(scanResultToScanEventMapper.toDto(ev))
                    .build());
        }

        return Flux.merge(data, keepalive)
                .doFinally(sig -> log.info("[SSE] Connection closed for all spaces scan (signal={})", sig));
    }

    // Backward-compatible entry point used by unit tests that call the controller method directly
    public Flux<ServerSentEvent<@NonNull ScanEventDto>> streamAllSpacesScan() {
        return streamAllSpacesScan(null);
    }
}

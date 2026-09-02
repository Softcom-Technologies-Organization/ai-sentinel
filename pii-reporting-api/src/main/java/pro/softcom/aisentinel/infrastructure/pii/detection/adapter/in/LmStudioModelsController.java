package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.detection.port.in.ListLmStudioModelsPort;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.LmStudioModelDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.LmStudioModelsResponseDto;

import java.util.List;

/**
 * REST endpoint listing the Ministral-PII models LM Studio has on disk.
 */
@RestController
@RequestMapping("/api/v1/pii-detection/lm-studio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "LM Studio", description = "Models available on the LM Studio endpoint")
public class LmStudioModelsController {

    private final ListLmStudioModelsPort listLmStudioModelsPort;

    /**
     * Lists the Ministral-PII models of the configured LM Studio endpoint, or of
     * the given host and port while the operator edits them.
     *
     * @param host optional LM Studio host overriding the configured one
     * @param port optional LM Studio port overriding the configured one
     * @return 200 with the listing; the listing's error is set when LM Studio could not be listed
     */
    @GetMapping("/models")
    @Operation(summary = "List the Ministral-PII models available on LM Studio")
    public ResponseEntity<@NonNull LmStudioModelsResponseDto> listModels(
            @RequestParam(required = false) String host,
            @RequestParam(required = false) Integer port) {
        log.debug("GET /api/v1/pii-detection/lm-studio/models host={} port={}", host, port);
        LmStudioModelListing listing = listLmStudioModelsPort.listModels(host, port);
        List<LmStudioModelDto> models = listing.models().stream()
                .map(model -> new LmStudioModelDto(model.id(), model.quantization(), model.publisher(), model.loaded()))
                .toList();
        return ResponseEntity.ok(new LmStudioModelsResponseDto(listing.endpoint(), listing.family(), models,
                                                               listing.error()));
    }
}

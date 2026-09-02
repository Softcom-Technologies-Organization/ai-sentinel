package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import java.util.List;

/**
 * Ministral-PII models LM Studio has on disk, for the dashboard's model picker.
 *
 * @param endpoint Where the models were listed
 * @param family   Identifier prefix every listed model shares
 * @param models   The models, empty when the listing failed
 * @param error    Empty when the listing succeeded; short technical reason otherwise
 */
public record LmStudioModelsResponseDto(String endpoint, String family, List<LmStudioModelDto> models, String error) {
}

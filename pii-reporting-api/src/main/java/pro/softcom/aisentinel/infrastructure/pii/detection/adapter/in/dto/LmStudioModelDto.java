package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

/**
 * One LM Studio model offered to the dashboard's model picker.
 *
 * @param id           Exact identifier LM Studio expects in requests
 * @param quantization Quantization label, e.g. {@code Q4_K_M}
 * @param publisher    Publisher of the GGUF file
 * @param loaded       Whether the model is in memory and can answer
 */
public record LmStudioModelDto(String id, String quantization, String publisher, boolean loaded) {
}

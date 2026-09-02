package pro.softcom.aisentinel.domain.pii.scan;

/**
 * One model LM Studio has on disk.
 *
 * @param id           Exact identifier LM Studio expects in requests
 * @param quantization Quantization label, e.g. {@code Q4_K_M}
 * @param publisher    Publisher of the GGUF file
 * @param loaded       Whether the model is in memory and can answer
 */
public record LmStudioModel(String id, String quantization, String publisher, boolean loaded) {
}

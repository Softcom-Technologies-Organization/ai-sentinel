package pro.softcom.aisentinel.domain.pii.judge;

/**
 * One finding submitted to the LLM judge: the claimed type, the plaintext value and the text
 * window around it. {@code model} null means the detection model configured on the endpoint.
 */
public record FindingJudgeRequest(
        String piiType,
        String typeLabel,
        String value,
        String context,
        String model,
        String lmStudioHost,
        Integer lmStudioPort
) {
}

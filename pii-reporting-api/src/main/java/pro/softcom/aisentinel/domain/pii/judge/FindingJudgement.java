package pro.softcom.aisentinel.domain.pii.judge;

/**
 * What the LLM judge concluded about one finding. The justification sentence is deliberately
 * not kept: the model may quote the judged value in it, and the remediation projection must
 * stay free of PII values.
 */
public record FindingJudgement(JudgeVerdict verdict, double confidence) {

    public FindingJudgement {
        if (verdict == null) {
            throw new IllegalArgumentException("verdict is required");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public boolean rejects() {
        return verdict == JudgeVerdict.FALSE_POSITIVE;
    }
}

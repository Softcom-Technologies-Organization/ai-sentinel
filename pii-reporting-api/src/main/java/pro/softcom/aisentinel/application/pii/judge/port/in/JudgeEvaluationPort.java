package pro.softcom.aisentinel.application.pii.judge.port.in;

import java.util.List;

/**
 * Port IN to preview and launch LLM judge runs over pending findings.
 */
public interface JudgeEvaluationPort {

    JudgePlan plan(JudgeScope scope);

    /**
     * Creates one job per targeted space that has pending findings and runs them
     * sequentially in the background.
     *
     * @return the created job ids, in scope order
     */
    List<String> launch(JudgeLaunchCommand command);
}

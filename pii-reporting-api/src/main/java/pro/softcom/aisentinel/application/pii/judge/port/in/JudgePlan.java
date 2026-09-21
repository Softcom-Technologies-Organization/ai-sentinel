package pro.softcom.aisentinel.application.pii.judge.port.in;

import java.util.Map;

/**
 * Read-only preview of a judge run: how many model calls it will make, per space.
 */
public record JudgePlan(int totalFindings, Map<String, Integer> findingsBySpace) {

    public JudgePlan {
        findingsBySpace = findingsBySpace == null ? Map.of() : Map.copyOf(findingsBySpace);
    }
}

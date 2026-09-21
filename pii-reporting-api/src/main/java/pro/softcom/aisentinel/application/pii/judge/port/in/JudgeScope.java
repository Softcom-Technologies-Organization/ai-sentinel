package pro.softcom.aisentinel.application.pii.judge.port.in;

import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.List;

/**
 * What a judge run covers: either whole spaces (every pending finding of each, the dashboard
 * entry point) or one criteria-based selection inside a space (the remediation entry point).
 */
public record JudgeScope(List<String> spaceKeys, RemediationSelection selection) {

    public JudgeScope {
        spaceKeys = spaceKeys == null ? List.of() : List.copyOf(spaceKeys);
        if (spaceKeys.isEmpty() == (selection == null)) {
            throw new IllegalArgumentException("either spaceKeys or selection is required, not both");
        }
    }

    /**
     * One selection per targeted space, in scope order.
     */
    public List<RemediationSelection> selections() {
        if (selection != null) {
            return List.of(selection);
        }
        return spaceKeys.stream().map(JudgeScope::everyPendingFindingOf).toList();
    }

    private static RemediationSelection everyPendingFindingOf(String spaceKey) {
        return RemediationSelection.builder()
                .spaceKey(spaceKey)
                .severities(List.of(PersonallyIdentifiableInformationSeverity.values()))
                .build();
    }
}

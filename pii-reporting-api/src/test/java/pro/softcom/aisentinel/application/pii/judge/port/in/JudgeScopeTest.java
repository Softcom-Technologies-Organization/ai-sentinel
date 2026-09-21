package pro.softcom.aisentinel.application.pii.judge.port.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JudgeScope")
class JudgeScopeTest {

    @Test
    @DisplayName("Should_SelectEveryPendingFindingOfEachSpace_When_SpaceKeysGiven")
    void Should_SelectEveryPendingFindingOfEachSpace_When_SpaceKeysGiven() {
        List<RemediationSelection> selections = new JudgeScope(List.of("A", "B"), null).selections();

        assertThat(selections).extracting(RemediationSelection::spaceKey).containsExactly("A", "B");
        assertThat(selections.getFirst().severities())
                .containsExactlyInAnyOrder(PersonallyIdentifiableInformationSeverity.values());
    }

    @Test
    @DisplayName("Should_UseGivenSelection_When_SelectionGiven")
    void Should_UseGivenSelection_When_SelectionGiven() {
        RemediationSelection selection = RemediationSelection.builder().spaceKey("A").piiTypes(List.of("IBAN")).build();

        assertThat(new JudgeScope(null, selection).selections()).containsExactly(selection);
    }

    @Test
    @DisplayName("Should_RejectScope_When_BothOrNeitherGiven")
    void Should_RejectScope_When_BothOrNeitherGiven() {
        RemediationSelection selection = RemediationSelection.builder().spaceKey("A").build();
        List<String> spaceKeys = List.of("A");

        assertThatThrownBy(() -> new JudgeScope(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JudgeScope(spaceKeys, selection)).isInstanceOf(IllegalArgumentException.class);
    }
}

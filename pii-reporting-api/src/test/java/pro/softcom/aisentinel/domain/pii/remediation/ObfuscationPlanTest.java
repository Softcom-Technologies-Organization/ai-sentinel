package pro.softcom.aisentinel.domain.pii.remediation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("ObfuscationPlan")
class ObfuscationPlanTest {

    private static ObfuscationPlan.ObfuscationPlanBuilder plan() {
        return ObfuscationPlan.builder()
                .totalFindings(3)
                .bySeverity(Map.of(PersonallyIdentifiableInformationSeverity.HIGH, 3))
                .pagesImpacted(2)
                .falsePositivesReported(1)
                .attachmentExclusions(1)
                .selectionChecksum("checksum");
    }

    @Nested
    @DisplayName("checksumOf")
    class ChecksumOf {

        @Test
        @DisplayName("Should_ReturnSameChecksum_When_FindingIdsAreInDifferentOrder")
        void Should_ReturnSameChecksum_When_FindingIdsAreInDifferentOrder() {
            String checksumAscending = ObfuscationPlan.checksumOf(List.of("id-a", "id-b", "id-c"));
            String checksumShuffled = ObfuscationPlan.checksumOf(List.of("id-c", "id-a", "id-b"));

            assertThat(checksumShuffled).isEqualTo(checksumAscending);
        }

        @Test
        @DisplayName("Should_ReturnDifferentChecksum_When_ResolvedFindingIdsDiffer")
        void Should_ReturnDifferentChecksum_When_ResolvedFindingIdsDiffer() {
            String baseChecksum = ObfuscationPlan.checksumOf(List.of("id-a", "id-b"));

            assertSoftly(softly -> {
                softly.assertThat(ObfuscationPlan.checksumOf(List.of("id-a")))
                        .as("removed id").isNotEqualTo(baseChecksum);
                softly.assertThat(ObfuscationPlan.checksumOf(List.of("id-a", "id-b", "id-c")))
                        .as("added id").isNotEqualTo(baseChecksum);
                softly.assertThat(ObfuscationPlan.checksumOf(List.of("id-a", "id-x")))
                        .as("changed id").isNotEqualTo(baseChecksum);
            });
        }

        @Test
        @DisplayName("Should_Return64CharacterLowercaseHex_When_Computed")
        void Should_Return64CharacterLowercaseHex_When_Computed() {
            assertThat(ObfuscationPlan.checksumOf(List.of("id-a"))).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("Should_ReturnStableChecksum_When_NoFindingIsResolved")
        void Should_ReturnStableChecksum_When_NoFindingIsResolved() {
            assertThat(ObfuscationPlan.checksumOf(List.of()))
                    .isEqualTo(ObfuscationPlan.checksumOf(List.of()))
                    .matches("[0-9a-f]{64}");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("Should_RejectConstruction_When_SelectionChecksumIsBlank")
        void Should_RejectConstruction_When_SelectionChecksumIsBlank() {
            var builder = plan().selectionChecksum(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("selectionChecksum");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_AnyCountIsNegative")
        void Should_RejectConstruction_When_AnyCountIsNegative() {
            assertSoftly(softly -> {
                softly.assertThatThrownBy(() -> plan().totalFindings(-1).build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("totalFindings");
                softly.assertThatThrownBy(() -> plan().pagesImpacted(-1).build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("pagesImpacted");
                softly.assertThatThrownBy(() -> plan().falsePositivesReported(-1).build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("falsePositivesReported");
                softly.assertThatThrownBy(() -> plan().attachmentExclusions(-1).build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("attachmentExclusions");
            });
        }

        @Test
        @DisplayName("Should_DefaultToEmptySeverityBreakdown_When_MapIsNotProvided")
        void Should_DefaultToEmptySeverityBreakdown_When_MapIsNotProvided() {
            ObfuscationPlan obfuscationPlan = plan().bySeverity(null).build();

            assertThat(obfuscationPlan.bySeverity()).isEmpty();
        }

        @Test
        @DisplayName("Should_ExposeImmutableSeverityBreakdown_When_Constructed")
        void Should_ExposeImmutableSeverityBreakdown_When_Constructed() {
            var bySeverity = plan().build().bySeverity();

            assertThatThrownBy(() -> bySeverity.put(PersonallyIdentifiableInformationSeverity.LOW, 1))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}

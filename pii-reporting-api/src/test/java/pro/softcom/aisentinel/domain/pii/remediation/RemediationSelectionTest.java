package pro.softcom.aisentinel.domain.pii.remediation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("RemediationSelection")
class RemediationSelectionTest {

    @Nested
    @DisplayName("scope invariants")
    class ScopeInvariants {

        @Test
        @DisplayName("Should_RejectConstruction_When_SpaceKeyIsMissing")
        void Should_RejectConstruction_When_SpaceKeyIsMissing() {
            var builder = RemediationSelection.builder();

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("spaceKey");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_SpaceKeyIsBlank")
        void Should_RejectConstruction_When_SpaceKeyIsBlank() {
            var builder = RemediationSelection.builder().spaceKey(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("spaceKey");
        }

        @Test
        @DisplayName("Should_AcceptSpaceWideScope_When_OnlySpaceKeyIsProvided")
        void Should_AcceptSpaceWideScope_When_OnlySpaceKeyIsProvided() {
            RemediationSelection selection = RemediationSelection.builder().spaceKey("SPACE").build();

            assertSoftly(softly -> {
                softly.assertThat(selection.spaceKey()).isEqualTo("SPACE");
                softly.assertThat(selection.pageId()).isNull();
                softly.assertThat(selection.attachmentName()).isNull();
            });
        }

        @Test
        @DisplayName("Should_RejectAttachmentName_When_PageIdIsMissing")
        void Should_RejectAttachmentName_When_PageIdIsMissing() {
            var builder = RemediationSelection.builder()
                    .spaceKey("SPACE")
                    .attachmentName("report.pdf");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pageId");
        }

        @Test
        @DisplayName("Should_AcceptAttachmentScope_When_PageIdIsPresent")
        void Should_AcceptAttachmentScope_When_PageIdIsPresent() {
            RemediationSelection selection = RemediationSelection.builder()
                    .spaceKey("SPACE")
                    .pageId("12345")
                    .attachmentName("report.pdf")
                    .build();

            assertThat(selection.attachmentName()).isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("Should_RejectBlankPageId_When_Provided")
        void Should_RejectBlankPageId_When_Provided() {
            var builder = RemediationSelection.builder().spaceKey("SPACE").pageId(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pageId");
        }

        @Test
        @DisplayName("Should_RejectBlankAttachmentName_When_Provided")
        void Should_RejectBlankAttachmentName_When_Provided() {
            var builder = RemediationSelection.builder()
                    .spaceKey("SPACE")
                    .pageId("12345")
                    .attachmentName(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attachmentName");
        }
    }

    @Nested
    @DisplayName("criteria collections")
    class CriteriaCollections {

        @Test
        @DisplayName("Should_DefaultToEmptyCollections_When_CriteriaAreNotProvided")
        void Should_DefaultToEmptyCollections_When_CriteriaAreNotProvided() {
            RemediationSelection selection = RemediationSelection.builder().spaceKey("SPACE").build();

            assertSoftly(softly -> {
                softly.assertThat(selection.piiTypes()).isEmpty();
                softly.assertThat(selection.severities()).isEmpty();
                softly.assertThat(selection.excludedFindingIds()).isEmpty();
                softly.assertThat(selection.includedFindingIds()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should_CopyCollections_When_Constructed")
        void Should_CopyCollections_When_Constructed() {
            List<String> mutableTypes = new ArrayList<>(List.of("EMAIL_ADDRESS"));
            RemediationSelection selection = RemediationSelection.builder()
                    .spaceKey("SPACE")
                    .piiTypes(mutableTypes)
                    .build();

            mutableTypes.add("PHONE_NUMBER");

            assertThat(selection.piiTypes()).containsExactly("EMAIL_ADDRESS");
        }

        @Test
        @DisplayName("Should_ExposeImmutableCollections_When_Constructed")
        void Should_ExposeImmutableCollections_When_Constructed() {
            RemediationSelection selection = RemediationSelection.builder()
                    .spaceKey("SPACE")
                    .piiTypes(List.of("EMAIL_ADDRESS"))
                    .severities(List.of(PersonallyIdentifiableInformationSeverity.HIGH))
                    .excludedFindingIds(Set.of("finding-1"))
                    .includedFindingIds(Set.of("finding-2"))
                    .build();

            assertSoftly(softly -> {
                softly.assertThatThrownBy(() -> selection.piiTypes().add("X"))
                        .isInstanceOf(UnsupportedOperationException.class);
                softly.assertThatThrownBy(() -> selection.severities().clear())
                        .isInstanceOf(UnsupportedOperationException.class);
                softly.assertThatThrownBy(() -> selection.excludedFindingIds().add("X"))
                        .isInstanceOf(UnsupportedOperationException.class);
                softly.assertThatThrownBy(() -> selection.includedFindingIds().add("X"))
                        .isInstanceOf(UnsupportedOperationException.class);
            });
        }
    }
}

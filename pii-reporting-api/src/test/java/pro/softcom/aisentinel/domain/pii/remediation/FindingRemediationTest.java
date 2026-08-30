package pro.softcom.aisentinel.domain.pii.remediation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("FindingRemediation")
class FindingRemediationTest {

    private static FindingRemediation.FindingRemediationBuilder remediation() {
        return FindingRemediation.builder()
                .findingId("f".repeat(64))
                .scanId("scan-123")
                .spaceKey("SPACE")
                .pageId("12345")
                .piiType("EMAIL_ADDRESS")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .detector("MINISTRAL")
                .status(FindingRemediationStatus.FALSE_POSITIVE)
                .actor("compliance-officer")
                .occurredAt(Instant.parse("2026-07-05T10:00:00Z"));
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("Should_ExposeAllFields_When_BuiltWithFullData")
        void Should_ExposeAllFields_When_BuiltWithFullData() {
            FindingRemediation row = remediation()
                    .attachmentName("report.pdf")
                    .statusReason("manually reviewed")
                    .redactionJobId("job-42")
                    .build();

            assertSoftly(softly -> {
                softly.assertThat(row.findingId()).isEqualTo("f".repeat(64));
                softly.assertThat(row.scanId()).isEqualTo("scan-123");
                softly.assertThat(row.spaceKey()).isEqualTo("SPACE");
                softly.assertThat(row.pageId()).isEqualTo("12345");
                softly.assertThat(row.attachmentName()).isEqualTo("report.pdf");
                softly.assertThat(row.piiType()).isEqualTo("EMAIL_ADDRESS");
                softly.assertThat(row.severity()).isEqualTo(PersonallyIdentifiableInformationSeverity.MEDIUM);
                softly.assertThat(row.detector()).isEqualTo("MINISTRAL");
                softly.assertThat(row.status()).isEqualTo(FindingRemediationStatus.FALSE_POSITIVE);
                softly.assertThat(row.statusReason()).isEqualTo("manually reviewed");
                softly.assertThat(row.actor()).isEqualTo("compliance-officer");
                softly.assertThat(row.occurredAt()).isEqualTo(Instant.parse("2026-07-05T10:00:00Z"));
                softly.assertThat(row.redactionJobId()).isEqualTo("job-42");
            });
        }

        @Test
        @DisplayName("Should_AcceptNullOptionalFields_When_FindingIsOnPageBody")
        void Should_AcceptNullOptionalFields_When_FindingIsOnPageBody() {
            FindingRemediation row = remediation().build();

            assertSoftly(softly -> {
                softly.assertThat(row.attachmentName()).isNull();
                softly.assertThat(row.statusReason()).isNull();
                softly.assertThat(row.redactionJobId()).isNull();
            });
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_FindingIdIsBlank")
        void Should_RejectConstruction_When_FindingIdIsBlank() {
            var builder = remediation().findingId(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("findingId");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_StatusIsMissing")
        void Should_RejectConstruction_When_StatusIsMissing() {
            var builder = remediation().status(null);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_SeverityIsMissing")
        void Should_RejectConstruction_When_SeverityIsMissing() {
            var builder = remediation().severity(null);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("severity");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_OccurredAtIsMissing")
        void Should_RejectConstruction_When_OccurredAtIsMissing() {
            var builder = remediation().occurredAt(null);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("occurredAt");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_ActorIsBlank")
        void Should_RejectConstruction_When_ActorIsBlank() {
            var builder = remediation().actor("");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("actor");
        }
    }
}

package pro.softcom.aisentinel.domain.pii.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("SourceScanCompleted domain event")
class SourceScanCompletedTest {

    @Test
    @DisplayName("Should_CreateEvent_When_AllFieldsAreValid")
    void Should_CreateEvent_When_AllFieldsAreValid() {
        // Act
        SourceScanCompleted event = new SourceScanCompleted("scan-123", "SPACE-KEY", SourceType.CONFLUENCE);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(event.scanId()).isEqualTo("scan-123");
            softly.assertThat(event.sourceKey()).isEqualTo("SPACE-KEY");
            softly.assertThat(event.sourceType()).isEqualTo(SourceType.CONFLUENCE);
        });
    }

    @Test
    @DisplayName("Should_CreateEvent_When_SourceTypeIsJira")
    void Should_CreateEvent_When_SourceTypeIsJira() {
        // Act
        SourceScanCompleted event = new SourceScanCompleted("scan-456", "PROJ-1", SourceType.JIRA);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(event.scanId()).isEqualTo("scan-456");
            softly.assertThat(event.sourceKey()).isEqualTo("PROJ-1");
            softly.assertThat(event.sourceType()).isEqualTo(SourceType.JIRA);
        });
    }

    @Test
    @DisplayName("Should_CreateEvent_When_SourceTypeIsDatabase")
    void Should_CreateEvent_When_SourceTypeIsDatabase() {
        // Act
        SourceScanCompleted event = new SourceScanCompleted("scan-789", "users-table", SourceType.DATABASE);

        // Assert
        assertThat(event.sourceType()).isEqualTo(SourceType.DATABASE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should_ThrowException_When_ScanIdIsBlankOrNull")
    void Should_ThrowException_When_ScanIdIsBlankOrNull(String scanId) {
        assertThatThrownBy(() -> new SourceScanCompleted(scanId, "SPACE-KEY", SourceType.CONFLUENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId cannot be empty");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should_ThrowException_When_SourceKeyIsBlankOrNull")
    void Should_ThrowException_When_SourceKeyIsBlankOrNull(String sourceKey) {
        assertThatThrownBy(() -> new SourceScanCompleted("scan-123", sourceKey, SourceType.CONFLUENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceKey cannot be empty");
    }

    @Test
    @DisplayName("Should_ThrowNullPointerException_When_SourceTypeIsNull")
    void Should_ThrowNullPointerException_When_SourceTypeIsNull() {
        assertThatThrownBy(() -> new SourceScanCompleted("scan-123", "SPACE-KEY", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceType cannot be null");
    }

    @Test
    @DisplayName("Should_BeEqual_When_SameFieldValues")
    void Should_BeEqual_When_SameFieldValues() {
        // Arrange
        SourceScanCompleted event1 = new SourceScanCompleted("scan-1", "KEY-1", SourceType.CONFLUENCE);
        SourceScanCompleted event2 = new SourceScanCompleted("scan-1", "KEY-1", SourceType.CONFLUENCE);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(event1).isEqualTo(event2);
            softly.assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        });
    }

    @Test
    @DisplayName("Should_NotBeEqual_When_DifferentSourceType")
    void Should_NotBeEqual_When_DifferentSourceType() {
        // Arrange
        SourceScanCompleted confluenceEvent = new SourceScanCompleted("scan-1", "KEY-1", SourceType.CONFLUENCE);
        SourceScanCompleted jiraEvent = new SourceScanCompleted("scan-1", "KEY-1", SourceType.JIRA);

        // Assert
        assertThat(confluenceEvent).isNotEqualTo(jiraEvent);
    }
}

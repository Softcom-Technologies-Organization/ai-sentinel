package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageDiscoveredLabelsPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageDiscoveredLabelsPort.PromoteDiscoveredLabelCommand;
import pro.softcom.aisentinel.domain.pii.detection.DiscoveredLabel;
import pro.softcom.aisentinel.domain.pii.detection.DiscoveredLabelStatus;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.DiscoveredLabelResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.PromoteDiscoveredLabelRequestDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscoveredLabelController")
class DiscoveredLabelControllerTest {

    @Mock
    private ManageDiscoveredLabelsPort manageDiscoveredLabelsPort;

    @InjectMocks
    private DiscoveredLabelController controller;

    @Test
    @DisplayName("Should_ReturnPendingLabels_When_ListPending")
    void Should_ReturnPendingLabels_When_ListPending() {
        // Arrange
        DiscoveredLabel label = new DiscoveredLabel(
                "VEHICLE_COLOR", 4L, LocalDateTime.now(), LocalDateTime.now(), DiscoveredLabelStatus.PENDING);
        when(manageDiscoveredLabelsPort.listPending()).thenReturn(List.of(label));

        // Act
        var response = controller.listPending();

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            softly.assertThat(response.getBody()).hasSize(1);
            DiscoveredLabelResponseDto dto = response.getBody().getFirst();
            softly.assertThat(dto.label()).isEqualTo("VEHICLE_COLOR");
            softly.assertThat(dto.occurrenceCount()).isEqualTo(4L);
            softly.assertThat(dto.status()).isEqualTo("PENDING");
        });
    }

    @Test
    @DisplayName("Should_ReturnCreatedConfig_When_Promote")
    void Should_ReturnCreatedConfig_When_Promote() {
        // Arrange
        PiiTypeConfig created = PiiTypeConfig.builder()
                .piiType("VEHICLE_COLOR").detector("MINISTRAL").enabled(true).threshold(0.7)
                .category("OTHER").detectorLabel("vehicle color").severity("LOW").build();
        when(manageDiscoveredLabelsPort.promote(any())).thenReturn(created);
        var request = new PromoteDiscoveredLabelRequestDto("OTHER", "LOW", 0.7, "vehicle color", "CH");

        // Act
        var response = controller.promote("VEHICLE_COLOR", request);

        // Assert
        ArgumentCaptor<PromoteDiscoveredLabelCommand> captor = ArgumentCaptor.forClass(PromoteDiscoveredLabelCommand.class);
        verify(manageDiscoveredLabelsPort).promote(captor.capture());
        PromoteDiscoveredLabelCommand command = captor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            softly.assertThat(response.getBody()).isNotNull();
            softly.assertThat(response.getBody().piiType()).isEqualTo("VEHICLE_COLOR");
            softly.assertThat(command.label()).isEqualTo("VEHICLE_COLOR");
            softly.assertThat(command.category()).isEqualTo("OTHER");
            softly.assertThat(command.severity()).isEqualTo("LOW");
            softly.assertThat(command.threshold()).isEqualTo(0.7);
            softly.assertThat(command.detectorLabel()).isEqualTo("vehicle color");
            softly.assertThat(command.countryCode()).isEqualTo("CH");
            softly.assertThat(command.promotedBy()).isEqualTo("admin");
        });
    }

    @Test
    @DisplayName("Should_ReturnNoContent_When_Ignore")
    void Should_ReturnNoContent_When_Ignore() {
        var response = controller.ignore("MISC_LABEL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(manageDiscoveredLabelsPort).ignore("MISC_LABEL");
    }
}

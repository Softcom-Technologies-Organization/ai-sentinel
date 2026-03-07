package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Delegating export context adapter tests")
class DelegatingExportContextAdapterTest {

    @Mock
    private ReadExportContextPort confluenceDelegate;

    @Mock
    private ReadExportContextPort jiraDelegate;

    private DelegatingExportContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DelegatingExportContextAdapter(Map.of(
                SourceType.CONFLUENCE, confluenceDelegate,
                SourceType.JIRA, jiraDelegate
        ));
    }

    @Test
    @DisplayName("Should_DelegateToConfluenceAdapter_When_SourceTypeConfluence")
    void Should_DelegateToConfluenceAdapter_When_SourceTypeConfluence() {
        // Arrange
        String spaceKey = "TEST";
        ExportContext expectedContext = ExportContext.builder()
                .reportName("Test Space")
                .reportIdentifier(spaceKey)
                .sourceUrl("https://confluence.example.com/space/TEST")
                .contacts(List.of())
                .additionalMetadata(Map.of())
                .build();
        when(confluenceDelegate.findContext(SourceType.CONFLUENCE, spaceKey)).thenReturn(expectedContext);

        // Act
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Assert
        assertThat(result).isEqualTo(expectedContext);
        verify(confluenceDelegate).findContext(SourceType.CONFLUENCE, spaceKey);
        verifyNoInteractions(jiraDelegate);
    }

    @Test
    @DisplayName("Should_DelegateToJiraAdapter_When_SourceTypeJira")
    void Should_DelegateToJiraAdapter_When_SourceTypeJira() {
        // Arrange
        String projectKey = "PROJ";
        ExportContext expectedContext = ExportContext.builder()
                .reportName("My Jira Project")
                .reportIdentifier(projectKey)
                .sourceUrl("https://jira.example.com/browse/PROJ")
                .contacts(List.of())
                .additionalMetadata(Map.of())
                .build();
        when(jiraDelegate.findContext(SourceType.JIRA, projectKey)).thenReturn(expectedContext);

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, projectKey);

        // Assert
        assertThat(result).isEqualTo(expectedContext);
        verify(jiraDelegate).findContext(SourceType.JIRA, projectKey);
        verifyNoInteractions(confluenceDelegate);
    }

    @Test
    @DisplayName("Should_ThrowUnsupportedSourceTypeException_When_UnknownSourceType")
    void Should_ThrowUnsupportedSourceTypeException_When_UnknownSourceType() {
        // Arrange - create adapter with empty delegate map to simulate unknown source type
        var adapterWithNoDelegates = new DelegatingExportContextAdapter(Map.of());

        // Act & Assert
        assertThatThrownBy(() -> adapterWithNoDelegates.findContext(SourceType.CONFLUENCE, "KEY"))
                .isInstanceOf(UnsupportedSourceTypeException.class)
                .hasMessageContaining("CONFLUENCE");

        verifyNoInteractions(confluenceDelegate);
        verifyNoInteractions(jiraDelegate);
    }
}

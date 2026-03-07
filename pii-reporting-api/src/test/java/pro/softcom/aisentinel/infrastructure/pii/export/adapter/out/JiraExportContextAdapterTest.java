package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.jira.port.out.JiraClient;
import pro.softcom.aisentinel.application.jira.port.out.JiraUrlProvider;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.domain.jira.JiraProject;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SoftAssertionsExtension.class})
@DisplayName("Jira export context adapter tests")
class JiraExportContextAdapterTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraUrlProvider jiraUrlProvider;

    private JiraExportContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JiraExportContextAdapter(jiraClient, jiraUrlProvider);
    }

    @Test
    @DisplayName("Should_ReturnExportContext_When_JiraProjectFound")
    void Should_ReturnExportContext_When_JiraProjectFound(SoftAssertions softly) {
        // Arrange
        String projectKey = "PROJ";
        JiraProject project = createJiraProject(projectKey, "My Project");
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of(project)));
        when(jiraUrlProvider.baseUrl()).thenReturn("https://jira.example.com");

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, projectKey);

        // Assert
        softly.assertThat(result.reportName()).isEqualTo("My Project");
        softly.assertThat(result.reportIdentifier()).isEqualTo(projectKey);
        softly.assertThat(result.sourceUrl()).isEqualTo("https://jira.example.com/browse/PROJ");
        softly.assertThat(result.contacts()).isEmpty();
        softly.assertThat(result.additionalMetadata()).containsEntry("projectId", "proj-123");
        verify(jiraClient).getAllProjects();
    }

    @Test
    @DisplayName("Should_ThrowUnsupportedSourceTypeException_When_NotJira")
    void Should_ThrowUnsupportedSourceTypeException_When_NotJira() {
        // Act & Assert
        assertThatThrownBy(() -> adapter.findContext(SourceType.CONFLUENCE, "SPACE"))
                .isInstanceOf(UnsupportedSourceTypeException.class)
                .hasMessageContaining("CONFLUENCE");
    }

    @Test
    @DisplayName("Should_BuildCorrectProjectUrl_When_ProjectKeyProvided")
    void Should_BuildCorrectProjectUrl_When_ProjectKeyProvided() {
        // Arrange
        String projectKey = "SENTINEL";
        JiraProject project = createJiraProject(projectKey, "Sentinel Project");
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of(project)));
        when(jiraUrlProvider.baseUrl()).thenReturn("https://mycompany.atlassian.net/");

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, projectKey);

        // Assert - trailing slash should be normalized
        assertThat(result.sourceUrl()).isEqualTo("https://mycompany.atlassian.net/browse/SENTINEL");
    }

    @Test
    @DisplayName("Should_ReturnEmptyContacts_When_JiraProject")
    void Should_ReturnEmptyContacts_When_JiraProject() {
        // Arrange
        String projectKey = "PROJ";
        JiraProject project = createJiraProject(projectKey, "My Project");
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of(project)));
        when(jiraUrlProvider.baseUrl()).thenReturn("https://jira.example.com");

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, projectKey);

        // Assert
        assertThat(result.contacts()).isEmpty();
    }

    @Test
    @DisplayName("Should_ThrowExportContextNotFoundException_When_ProjectNotFound")
    void Should_ThrowExportContextNotFoundException_When_ProjectNotFound() {
        // Arrange
        String projectKey = "UNKNOWN";
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // Act & Assert
        assertThatThrownBy(() -> adapter.findContext(SourceType.JIRA, projectKey))
                .isInstanceOf(ExportContextNotFoundException.class)
                .hasMessageContaining("JIRA")
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("Should_FindCorrectProject_When_MultipleProjectsExist")
    void Should_FindCorrectProject_When_MultipleProjectsExist() {
        // Arrange
        String targetKey = "TARGET";
        JiraProject otherProject = createJiraProject("OTHER", "Other Project");
        JiraProject targetProject = createJiraProject(targetKey, "Target Project");
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of(otherProject, targetProject)));
        when(jiraUrlProvider.baseUrl()).thenReturn("https://jira.example.com");

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, targetKey);

        // Assert
        assertThat(result.reportName()).isEqualTo("Target Project");
        assertThat(result.reportIdentifier()).isEqualTo(targetKey);
    }

    @Test
    @DisplayName("Should_IncludeProjectIdInMetadata_When_ProjectFound")
    void Should_IncludeProjectIdInMetadata_When_ProjectFound() {
        // Arrange
        String projectKey = "PROJ";
        JiraProject project = createJiraProject(projectKey, "My Project");
        when(jiraClient.getAllProjects())
                .thenReturn(CompletableFuture.completedFuture(List.of(project)));
        when(jiraUrlProvider.baseUrl()).thenReturn("https://jira.example.com");

        // Act
        ExportContext result = adapter.findContext(SourceType.JIRA, projectKey);

        // Assert
        assertThat(result.additionalMetadata())
                .containsEntry("projectId", "proj-123")
                .containsEntry("projectLead", "John Doe");
    }

    private JiraProject createJiraProject(String key, String name) {
        return new JiraProject(
                "proj-123",
                key,
                name,
                "A test project description",
                "John Doe",
                "https://jira.example.com/projects/" + key,
                42,
                Instant.parse("2026-03-01T10:00:00Z")
        );
    }
}

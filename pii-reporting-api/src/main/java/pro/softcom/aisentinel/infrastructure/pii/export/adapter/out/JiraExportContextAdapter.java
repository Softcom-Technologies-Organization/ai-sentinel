package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.jira.port.out.JiraClient;
import pro.softcom.aisentinel.application.jira.port.out.JiraUrlProvider;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.domain.jira.JiraProject;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.util.List;
import java.util.Map;

/**
 * Adapts Jira projects to export contexts.
 * Retrieves project metadata from Jira and converts it into a platform-agnostic export context.
 */
@RequiredArgsConstructor
@Slf4j
public class JiraExportContextAdapter implements ReadExportContextPort {

    private final JiraClient jiraClient;
    private final JiraUrlProvider jiraUrlProvider;

    @Override
    public ExportContext findContext(SourceType sourceType, String sourceIdentifier) {
        if (sourceType != SourceType.JIRA) {
            throw new UnsupportedSourceTypeException(sourceType.getValue());
        }

        log.debug("Retrieving export context for Jira project: {}", sourceIdentifier);

        JiraProject project = findProjectByKey(sourceIdentifier);

        return ExportContext.builder()
                .reportName(project.name())
                .reportIdentifier(project.key())
                .sourceUrl(buildProjectUrl(sourceIdentifier))
                .contacts(List.of())
                .additionalMetadata(buildMetadata(project))
                .build();
    }

    private JiraProject findProjectByKey(String projectKey) {
        List<JiraProject> projects = jiraClient.getAllProjects().join();

        return projects.stream()
                .filter(project -> project.key().equals(projectKey))
                .findFirst()
                .orElseThrow(() -> new ExportContextNotFoundException(
                        SourceType.JIRA.getValue(), projectKey));
    }

    private String buildProjectUrl(String projectKey) {
        String baseUrl = jiraUrlProvider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBaseUrl + "/browse/" + projectKey;
    }

    private Map<String, String> buildMetadata(JiraProject project) {
        return Map.of(
                "projectId", project.id(),
                "projectLead", project.leadDisplayName() != null ? project.leadDisplayName() : ""
        );
    }
}

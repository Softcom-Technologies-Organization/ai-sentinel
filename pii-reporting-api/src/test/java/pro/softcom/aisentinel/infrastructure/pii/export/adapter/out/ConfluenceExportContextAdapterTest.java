package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceNotFoundException;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpaceDataOwner;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Confluence export context adapter tests")
class ConfluenceExportContextAdapterTest {

    @Mock
    private ConfluenceSpaceRepository confluenceSpaceRepository;

    @Mock
    private ConfluenceClient confluenceClient;

    private ConfluenceExportContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ConfluenceExportContextAdapter(confluenceSpaceRepository, confluenceClient);
    }

    @Test
    @DisplayName("Should_ThrowException_When_SpaceNotFoundInRepository")
    void Should_ThrowException_When_SpaceNotFoundInRepository() {
        // Given
        String spaceKey = "NOTFOUND";
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adapter.findContext(SourceType.CONFLUENCE, spaceKey))
                .isInstanceOf(ExportContextNotFoundException.class)
                .hasCauseInstanceOf(ConfluenceSpaceNotFoundException.class);

        verify(confluenceSpaceRepository).findByKey(spaceKey);
        verifyNoInteractions(confluenceClient);
    }

    @Test
    @DisplayName("Should_CreateExportContext_When_DataOwnersAreAlreadyLoaded")
    void Should_CreateExportContext_When_DataOwnersAreAlreadyLoaded() {
        // Given
        String spaceKey = "TEST";
        List<ConfluenceSpaceDataOwner> owners = List.of(
                new ConfluenceSpaceDataOwner("user1", "John Doe", "john@example.com"),
                new ConfluenceSpaceDataOwner("user2", "Jane Smith", "jane@example.com")
        );
        ConfluenceSpace space = createConfluenceSpace(spaceKey, "Test Space", new DataOwners.Loaded(owners));
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.of(space));

        // When
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Then
        assertThat(result.reportName()).isEqualTo("Test Space");
        assertThat(result.reportIdentifier()).isEqualTo(spaceKey);
        assertThat(result.contacts()).hasSize(2);
        assertThat(result.additionalMetadata()).containsKeys("spaceId", "spaceType", "spaceStatus");
        verify(confluenceSpaceRepository).findByKey(spaceKey);
        verifyNoInteractions(confluenceClient);
    }

    @Test
    @DisplayName("Should_LoadDataOwnersFromAPI_When_DataOwnersAreNotLoaded")
    void Should_LoadDataOwnersFromAPI_When_DataOwnersAreNotLoaded() {
        // Given
        String spaceKey = "TEST";
        ConfluenceSpace spaceWithoutOwners = createConfluenceSpace(spaceKey, "Test Space", new DataOwners.NotLoaded());
        List<ConfluenceSpaceDataOwner> apiOwners = List.of(
                new ConfluenceSpaceDataOwner("user1", "API User", "api@example.com")
        );
        ConfluenceSpace spaceWithOwners = createConfluenceSpace(spaceKey, "Test Space", new DataOwners.Loaded(apiOwners));
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.of(spaceWithoutOwners));
        when(confluenceClient.getSpaceWithPermissions(spaceKey))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(spaceWithOwners)));

        // When
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Then
        assertThat(result.contacts()).hasSize(1);
        verify(confluenceSpaceRepository).findByKey(spaceKey);
        verify(confluenceClient).getSpaceWithPermissions(spaceKey);
    }

    @ParameterizedTest
    @MethodSource("provideEmptyOwnersScenarios")
    @DisplayName("Should_HandleEmptyOwnersList_When_DifferentEmptyScenarios")
    void Should_HandleEmptyOwnersList_When_DifferentEmptyScenarios(
            DataOwners dataOwners,
            boolean shouldCallAPI,
            Optional<ConfluenceSpace> apiResponse
    ) {
        // Given
        String spaceKey = "EMPTY";
        ConfluenceSpace space = createConfluenceSpace(spaceKey, "Empty Space", dataOwners);
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.of(space));

        if (shouldCallAPI) {
            when(confluenceClient.getSpaceWithPermissions(spaceKey))
                    .thenReturn(CompletableFuture.completedFuture(apiResponse));
        }

        // When
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Then
        assertThat(result.contacts()).isEmpty();
        verify(confluenceSpaceRepository).findByKey(spaceKey);

        if (shouldCallAPI) {
            verify(confluenceClient).getSpaceWithPermissions(spaceKey);
        } else {
            verifyNoInteractions(confluenceClient);
        }
    }

    @ParameterizedTest
    @MethodSource("provideContactScenarios")
    @DisplayName("Should_MapContacts_When_DifferentOwnerListsProvided")
    void Should_MapContacts_When_DifferentOwnerListsProvided(List<ConfluenceSpaceDataOwner> owners, int expectedSize) {
        // Given
        String spaceKey = "CONTACTS";
        ConfluenceSpace space = createConfluenceSpace(spaceKey, "Contacts Test Space", new DataOwners.Loaded(owners));
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.of(space));

        // When
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Then
        assertThat(result.contacts()).hasSize(expectedSize);
    }

    @ParameterizedTest
    @MethodSource("provideMetadataScenarios")
    @DisplayName("Should_IncludeMetadata_When_CreatingExportContext")
    void Should_IncludeMetadata_When_CreatingExportContext(
            ConfluenceSpace.SpaceType spaceType,
            ConfluenceSpace.SpaceStatus spaceStatus
    ) {
        // Given
        String spaceKey = "META";
        ConfluenceSpace space = new ConfluenceSpace(
                "space-id-456",
                spaceKey,
                "Metadata Space",
                "https://example.com/space/META",
                "Description",
                spaceType,
                spaceStatus,
                new DataOwners.Loaded(List.of()),
                null
        );
        when(confluenceSpaceRepository.findByKey(spaceKey)).thenReturn(Optional.of(space));

        // When
        ExportContext result = adapter.findContext(SourceType.CONFLUENCE, spaceKey);

        // Then
        assertThat(result.additionalMetadata()).containsKeys("spaceId", "spaceType", "spaceStatus");
    }

    private static Stream<Arguments> provideEmptyOwnersScenarios() {
        return Stream.of(
                Arguments.of(new DataOwners.Loaded(List.of()), false, Optional.empty()),
                Arguments.of(new DataOwners.NotLoaded(), true,
                        Optional.of(createConfluenceSpace("KEY", "Space", new DataOwners.Loaded(List.of())))),
                Arguments.of(new DataOwners.NotLoaded(), true, Optional.empty())
        );
    }

    private static Stream<Arguments> provideContactScenarios() {
        return Stream.of(
                Arguments.of(List.of(), 0),
                Arguments.of(List.of(new ConfluenceSpaceDataOwner("user1", "User", "user@example.com")), 1),
                Arguments.of(List.of(
                        new ConfluenceSpaceDataOwner("user1", "User One", "one@example.com"),
                        new ConfluenceSpaceDataOwner("user2", "User Two", "two@example.com"),
                        new ConfluenceSpaceDataOwner("user3", "User Three", "three@example.com")
                ), 3)
        );
    }

    private static Stream<Arguments> provideMetadataScenarios() {
        return Stream.of(
                Arguments.of(ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT),
                Arguments.of(ConfluenceSpace.SpaceType.PROJECT, ConfluenceSpace.SpaceStatus.ARCHIVED),
                Arguments.of(ConfluenceSpace.SpaceType.PERSONAL, ConfluenceSpace.SpaceStatus.CURRENT)
        );
    }

    private static ConfluenceSpace createConfluenceSpace(String key, String name, DataOwners dataOwners) {
        return new ConfluenceSpace(
                "123",
                key,
                name,
                "https://example.com/space/" + key,
                "Test description",
                ConfluenceSpace.SpaceType.GLOBAL,
                ConfluenceSpace.SpaceStatus.CURRENT,
                dataOwners,
                null
        );
    }
}

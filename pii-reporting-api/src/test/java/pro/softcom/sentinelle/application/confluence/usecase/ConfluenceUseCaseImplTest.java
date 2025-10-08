package pro.softcom.sentinelle.application.confluence.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

@ExtendWith(MockitoExtension.class)
class ConfluenceUseCaseImplTest {

    @Mock
    private ConfluenceClient confluenceClient;

    @Mock
    private ConfluenceSpaceRepository spaceRepository;

    private ConfluenceUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfluenceUseCaseImpl(confluenceClient, spaceRepository);
    }

    @Test
    void Should_ReturnCachedSpaces_When_CacheIsNotEmpty() {
        // Given
        var cachedSpace = createTestSpace("SPACE1", "Test Space");
        when(spaceRepository.findAll()).thenReturn(List.of(cachedSpace));

        // When
        var result = useCase.getAllSpaces().join();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().key()).isEqualTo("SPACE1");
        verify(spaceRepository).findAll();
        verify(confluenceClient, never()).getAllSpaces();
    }

    @Test
    void Should_FetchFromApiAndCache_When_CacheIsEmpty() {
        // Given
        var apiSpace = createTestSpace("SPACE2", "API Space");
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceClient.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(apiSpace)));

        // When
        var result = useCase.getAllSpaces().join();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().key()).isEqualTo("SPACE2");
        verify(spaceRepository).findAll();
        verify(confluenceClient).getAllSpaces();
        verify(spaceRepository).saveAll(List.of(apiSpace));
    }

    @Test
    void Should_NotSaveToCache_When_ApiReturnsEmptyList() {
        // Given
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceClient.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When
        var result = useCase.getAllSpaces().join();

        // Then
        assertThat(result).isEmpty();
        verify(spaceRepository).findAll();
        verify(confluenceClient).getAllSpaces();
        verify(spaceRepository, never()).saveAll(any());
    }

    private ConfluenceSpace createTestSpace(String key, String name) {
        return new ConfluenceSpace(
            "id-" + key,
            key,
            name,
            "https://confluence.example.com/spaces/" + key,
            "Test description",
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT
        );
    }
}

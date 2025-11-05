package pro.softcom.sentinelle.application.confluence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.ModifiedPageInfo;
import pro.softcom.sentinelle.domain.confluence.SpaceUpdateInfo;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

/**
 * Unit tests for SpaceUpdateInfoService.
 * 
 * Business validation: Ensures the service correctly determines if spaces have been updated
 * since their last scan by comparing modification dates.
 */
@ExtendWith(MockitoExtension.class)
class SpaceUpdateInfoServiceTest {

    @Mock
    private ConfluenceUseCase confluenceUseCase;

    @Mock
    private ConfluenceClient confluenceClient;

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    private SpaceUpdateInfoService service;

    @BeforeEach
    void setUp() {
        service = new SpaceUpdateInfoService(confluenceUseCase, confluenceClient, scanCheckpointRepository);
    }

    @Test
    void Should_ReturnNoScanYet_When_NoCheckpointExists() {
        // Given - A space with no previous scan
        ConfluenceSpace space = createSpace("TEST", "Test Space", Instant.now());
        
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of());

        // When
        List<SpaceUpdateInfo> result = service.getAllSpacesUpdateInfo().join();

        // Then
        assertSoftly(softly -> {
            softly.assertThat(result).hasSize(1);
            SpaceUpdateInfo info = result.get(0);
            softly.assertThat(info.spaceKey()).isEqualTo("TEST");
            softly.assertThat(info.spaceName()).isEqualTo("Test Space");
            softly.assertThat(info.hasBeenUpdated()).isFalse();
            softly.assertThat(info.lastScanDate()).isNull();
            softly.assertThat(info.lastModified()).isNull();
        });
        
        verify(confluenceUseCase).getAllSpaces();
        verify(scanCheckpointRepository).findBySpace("TEST");
    }

    @Test
    void Should_ReturnNoUpdates_When_SpaceNotModifiedSinceLastScan() {
        // Given - A space last modified before the last scan
        Instant lastModified = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant lastScanDate = Instant.now().minus(5, ChronoUnit.DAYS);
        
        ConfluenceSpace space = createSpace("TEST", "Test Space", lastModified);
        ScanCheckpoint checkpoint = createCheckpoint("TEST", lastScanDate, ScanStatus.COMPLETED);
        
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of(checkpoint));
        when(confluenceClient.getModifiedPagesSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When
        List<SpaceUpdateInfo> result = service.getAllSpacesUpdateInfo().join();

        // Then
        assertSoftly(softly -> {
            softly.assertThat(result).hasSize(1);
            SpaceUpdateInfo info = result.get(0);
            softly.assertThat(info.spaceKey()).isEqualTo("TEST");
            softly.assertThat(info.hasBeenUpdated()).isFalse();
            softly.assertThat(info.lastModified()).isNull();
            softly.assertThat(info.lastScanDate()).isEqualTo(lastScanDate);
        });
    }

    @Test
    void Should_ReturnWithUpdates_When_SpaceModifiedAfterLastScan() {
        // Given - A space last modified after the last scan
        Instant lastScanDate = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant lastModified = Instant.now().minus(5, ChronoUnit.DAYS);
        
        ConfluenceSpace space = createSpace("TEST", "Test Space", lastModified);
        ScanCheckpoint checkpoint = createCheckpoint("TEST", lastScanDate, ScanStatus.COMPLETED);
        
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of(checkpoint));
        when(confluenceClient.getModifiedPagesSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of(
                new ModifiedPageInfo("1", "Updated Page", lastModified)
            )));
        when(confluenceClient.getModifiedAttachmentsSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When
        List<SpaceUpdateInfo> result = service.getAllSpacesUpdateInfo().join();

        // Then
        assertSoftly(softly -> {
            softly.assertThat(result).hasSize(1);
            SpaceUpdateInfo info = result.get(0);
            softly.assertThat(info.spaceKey()).isEqualTo("TEST");
            softly.assertThat(info.spaceName()).isEqualTo("Test Space");
            softly.assertThat(info.hasBeenUpdated()).isTrue();
            softly.assertThat(info.lastModified()).isEqualTo(lastModified);
            softly.assertThat(info.lastScanDate()).isEqualTo(lastScanDate);
        });
    }

    @Test
    void Should_ReturnNoUpdates_When_LastModifiedIsNull() {
        // Given - A space with no lastModified date
        ConfluenceSpace space = createSpace("TEST", "Test Space", null);
        ScanCheckpoint checkpoint = createCheckpoint("TEST", Instant.now(), ScanStatus.COMPLETED);
        
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of(checkpoint));
        when(confluenceClient.getModifiedPagesSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When
        List<SpaceUpdateInfo> result = service.getAllSpacesUpdateInfo().join();

        // Then
        assertSoftly(softly -> {
            softly.assertThat(result).hasSize(1);
            SpaceUpdateInfo info = result.get(0);
            softly.assertThat(info.hasBeenUpdated()).isFalse();
            softly.assertThat(info.lastModified()).isNull();
        });
    }

    @Test
    void Should_ReturnUpdateInfoForSpecificSpace_When_SpaceKeyProvided() {
        // Given
        String spaceKey = "TEST";
        ConfluenceSpace space = createSpace(spaceKey, "Test Space", Instant.now());
        
        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of());

        // When
        Optional<SpaceUpdateInfo> result = service.getSpaceUpdateInfo(spaceKey).join();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().spaceKey()).isEqualTo(spaceKey);
        
        verify(confluenceUseCase).getSpace(spaceKey);
    }

    @Test
    void Should_ReturnEmpty_When_SpaceNotFound() {
        // Given
        String spaceKey = "NOTFOUND";
        
        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // When
        Optional<SpaceUpdateInfo> result = service.getSpaceUpdateInfo(spaceKey).join();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void Should_UseLatestCompletedScan_When_MultipleCompletedScansExist() {
        // Given - Multiple completed scans, should use the most recent
        Instant olderScanDate = Instant.now().minus(20, ChronoUnit.DAYS);
        Instant newerScanDate = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant lastModified = Instant.now().minus(5, ChronoUnit.DAYS);
        
        ConfluenceSpace space = createSpace("TEST", "Test Space", lastModified);
        ScanCheckpoint olderCheckpoint = createCheckpoint("TEST", olderScanDate, ScanStatus.COMPLETED);
        ScanCheckpoint newerCheckpoint = createCheckpoint("TEST", newerScanDate, ScanStatus.COMPLETED);
        
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findBySpace(anyString()))
            .thenReturn(List.of(olderCheckpoint, newerCheckpoint));
        when(confluenceClient.getModifiedPagesSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of(
                new ModifiedPageInfo("1", "Updated Page", lastModified)
            )));
        when(confluenceClient.getModifiedAttachmentsSince(anyString(), any(Instant.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When
        List<SpaceUpdateInfo> result = service.getAllSpacesUpdateInfo().join();

        // Then - Should use the newer scan date for comparison
        assertSoftly(softly -> {
            softly.assertThat(result).hasSize(1);
            SpaceUpdateInfo info = result.get(0);
            softly.assertThat(info.lastScanDate()).isEqualTo(newerScanDate);
            softly.assertThat(info.hasBeenUpdated()).isTrue(); // lastModified (5d ago) > newerScan (10d ago)
        });
    }

    // Helper methods

    private ConfluenceSpace createSpace(String key, String name, Instant lastModified) {
        return new ConfluenceSpace(
            "space-" + key,  // id
            key,              // key
            name,             // name
            "https://confluence.example.com/spaces/" + key,  // url
            "Description for " + name,  // description
            ConfluenceSpace.SpaceType.GLOBAL,  // type
            ConfluenceSpace.SpaceStatus.CURRENT,  // status
            lastModified      // lastModified
        );
    }

    private ScanCheckpoint createCheckpoint(String spaceKey, Instant scanDate, ScanStatus status) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(scanDate, ZoneId.systemDefault());
        return ScanCheckpoint.builder()
            .scanId("scan-123")
            .spaceKey(spaceKey)
            .scanStatus(status)
            .updatedAt(localDateTime)
            .build();
    }
}

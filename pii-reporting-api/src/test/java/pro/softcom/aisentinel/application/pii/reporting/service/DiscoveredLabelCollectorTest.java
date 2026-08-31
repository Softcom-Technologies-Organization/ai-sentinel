package pro.softcom.aisentinel.application.pii.reporting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.DiscoveredLabelStore;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DiscoveredLabelCollectorTest {

    @Mock
    private DiscoveredLabelStore store;

    private DiscoveredLabelCollector collector;

    @BeforeEach
    void setUp() {
        collector = new DiscoveredLabelCollector(store, true);
    }

    @Test
    @DisplayName("Should_NotTouchStore_When_LabelCountsNull")
    void Should_NotTouchStore_When_LabelCountsNull() {
        collector.recordOccurrences(null);

        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Should_NotTouchStore_When_CollectionDisabled")
    void Should_NotTouchStore_When_CollectionDisabled() {
        DiscoveredLabelCollector disabled = new DiscoveredLabelCollector(store, false);

        disabled.recordOccurrences(Map.of("VEHICLE_COLOR", 2));

        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Should_NotTouchStore_When_LabelCountsEmpty")
    void Should_NotTouchStore_When_LabelCountsEmpty() {
        collector.recordOccurrences(Map.of());

        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Should_RecordOccurrences_When_LabelCountsPresent")
    void Should_RecordOccurrences_When_LabelCountsPresent() {
        Map<String, Integer> labelCounts = Map.of("VEHICLE_COLOR", 2, "PET_NAME", 1);

        collector.recordOccurrences(labelCounts);

        verify(store).recordOccurrences(labelCounts);
    }

    @Test
    @DisplayName("Should_SwallowStoreFailure_When_PersistenceThrows")
    void Should_SwallowStoreFailure_When_PersistenceThrows() {
        doThrow(new RuntimeException("db down")).when(store).recordOccurrences(any());
        Map<String, Integer> labelCounts = Map.of("VEHICLE_COLOR", 2);

        // Must not propagate: label collection can never fail the scan.
        assertThatCode(() -> collector.recordOccurrences(labelCounts)).doesNotThrowAnyException();
    }
}

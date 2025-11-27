package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery.SpaceCounter;
import pro.softcom.aisentinel.application.pii.security.PiiAccessAuditService;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository.LatestScanProjection;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository.SpaceCountersProjection;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

@ExtendWith(MockitoExtension.class)
class JpaScanResultQueryAdapterTest {

    private static final String SCAN_ID = "scan-123";
    private static final String SPACE_KEY = "SPACE";
    private static final String PAGE_ID = "123";

    @Mock
    private DetectionEventRepository eventRepository;

    @Mock
    private ScanResultEncryptor scanResultEncryptor;

    @Mock
    private PiiAccessAuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    private JpaScanResultQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaScanResultQueryAdapter(eventRepository, scanResultEncryptor, auditService, objectMapper);
    }

    @Test
    void Should_ReturnEmptyOptional_When_NoScanFound() {
        when(eventRepository.findLatestScanGrouped(any())).thenReturn(List.of());

        Optional<LastScanMeta> result = adapter.findLatestScan();

        assertThat(result).isEmpty();
        verify(eventRepository).findLatestScanGrouped(any());
        verify(eventRepository, never()).countDistinctSpaceKeyByScanId(any());
    }

    @Test
    void Should_ReturnLastScanMeta_When_LatestScanExists() {
        Instant now = Instant.now();

        LatestScanProjection projection = new LatestScanProjection() {
            @Override
            public String getScanId() {
                return SCAN_ID;
            }

            @Override
            public Instant getLastUpdated() {
                return now;
            }
        };

        when(eventRepository.findLatestScanGrouped(any())).thenReturn(List.of(projection));
        when(eventRepository.countDistinctSpaceKeyByScanId(SCAN_ID)).thenReturn(3);

        Optional<LastScanMeta> result = adapter.findLatestScan();

        assertThat(result).isPresent();
        LastScanMeta meta = result.orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(meta.scanId()).isEqualTo(SCAN_ID);
        softly.assertThat(meta.lastUpdated()).isEqualTo(now);
        softly.assertThat(meta.spacesCount()).isEqualTo(3);
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmptyList_When_ScanIdIsNullOrBlank_ForSpaceCounters() {
        assertThat(adapter.getSpaceCounters(null)).isEmpty();
        assertThat(adapter.getSpaceCounters("   ")).isEmpty();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void Should_MapSpaceCountersProjection_When_GetSpaceCounters() {
        Instant lastTs = Instant.now();

        SpaceCountersProjection projection = new SpaceCountersProjection() {
            @Override
            public String getSpaceKey() {
                return SPACE_KEY;
            }

            @Override
            public long getPagesDone() {
                return 10L;
            }

            @Override
            public long getAttachmentsDone() {
                return 5L;
            }

            @Override
            public Instant getLastEventTs() {
                return lastTs;
            }
        };

        when(eventRepository.aggregateSpaceCounters(SCAN_ID)).thenReturn(List.of(projection));

        List<SpaceCounter> counters = adapter.getSpaceCounters(SCAN_ID);

        assertThat(counters).hasSize(1);
        SpaceCounter counter = counters.getFirst();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(counter.spaceKey()).isEqualTo(SPACE_KEY);
        softly.assertThat(counter.pagesDone()).isEqualTo(10L);
        softly.assertThat(counter.attachmentsDone()).isEqualTo(5L);
        softly.assertThat(counter.lastEventTs()).isEqualTo(lastTs);
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmptyList_When_ScanIdIsNullOrBlank_ForListItemEvents() {
        assertThat(adapter.listItemEvents(null)).isEmpty();
        assertThat(adapter.listItemEvents(" ")).isEmpty();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void Should_ReturnDecryptedResults_When_ListItemEventsWithValidEntity() throws Exception {
        ConfluenceContentScanResult encrypted = sampleScanResult();
        ConfluenceContentScanResult decrypted = encrypted.toBuilder().message("decrypted").build();

        ScanEventEntity entity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), any()))
            .thenReturn(List.of(entity));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(
            ConfluenceContentScanResult.class))).thenReturn(encrypted);
        when(scanResultEncryptor.decrypt(encrypted)).thenReturn(decrypted);

        List<ConfluenceContentScanResult> results = adapter.listItemEvents(SCAN_ID);

        assertThat(results).containsExactly(decrypted);
    }

    @Test
    void Should_FilterOutNullResults_When_ListItemEventsWithNullPayloadOrError() throws Exception {
        ScanEventEntity nullPayloadEntity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(null)
            .build();

        ScanEventEntity errorEntity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), any()))
            .thenReturn(List.of(nullPayloadEntity, errorEntity));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(
            ConfluenceContentScanResult.class))).thenThrow(new RuntimeException("boom"));

        List<ConfluenceContentScanResult> results = adapter.listItemEvents(SCAN_ID);

        assertThat(results).isEmpty();
        verifyNoInteractions(scanResultEncryptor);
    }

    @Test
    void Should_ReturnEmptyList_When_ScanIdIsNullOrBlank_ForEncryptedEvents() {
        assertThat(adapter.listItemEventsEncrypted(null)).isEmpty();
        assertThat(adapter.listItemEventsEncrypted(" ")).isEmpty();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void Should_ReturnEncryptedResults_When_ListItemEventsEncrypted() throws Exception {
        ConfluenceContentScanResult encrypted = sampleScanResult();
        ScanEventEntity entity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), any()))
            .thenReturn(List.of(entity));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(
            ConfluenceContentScanResult.class))).thenReturn(encrypted);

        List<ConfluenceContentScanResult> results = adapter.listItemEventsEncrypted(SCAN_ID);

        assertThat(results).containsExactly(encrypted);
        verifyNoInteractions(scanResultEncryptor);
    }

    @Test
    void Should_ReturnEncryptedResults_When_ListItemEventsEncryptedByScanIdAndSpaceKey() throws Exception {
        ConfluenceContentScanResult encrypted = sampleScanResult();
        ScanEventEntity entity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndSpaceKeyAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), eq(SPACE_KEY), any()))
            .thenReturn(List.of(entity));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(
            ConfluenceContentScanResult.class))).thenReturn(encrypted);

        List<ConfluenceContentScanResult> results = adapter.listItemEventsEncryptedByScanIdAndSpaceKey(SCAN_ID, SPACE_KEY);

        assertThat(results).containsExactly(encrypted);
    }

    @Test
    void Should_ReturnEmptyList_When_ScanIdIsNullOrBlank_ForDecryptedEvents() {
        assertThat(adapter.listItemEventsDecrypted(null, PAGE_ID, AccessPurpose.USER_DISPLAY)).isEmpty();
        assertThat(adapter.listItemEventsDecrypted(" ", PAGE_ID, AccessPurpose.USER_DISPLAY)).isEmpty();
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void Should_NotAudit_When_NoDecryptedResultsFound() {
        when(eventRepository.findByScanIdAndPageIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), eq(PAGE_ID), any()))
            .thenReturn(List.of());

        List<ConfluenceContentScanResult> results = adapter.listItemEventsDecrypted(SCAN_ID, PAGE_ID, AccessPurpose.USER_DISPLAY);

        assertThat(results).isEmpty();
        verifyNoInteractions(auditService);
    }

    @Test
    void Should_DecryptAndAudit_When_DecryptedResultsFound() throws Exception {
        ConfluenceContentScanResult encrypted1 = sampleScanResult().toBuilder()
            .detectedPIIList(List.of(DetectedPersonallyIdentifiableInformation.builder().build()))
            .build();
        ConfluenceContentScanResult decrypted1 = encrypted1.toBuilder().message("dec1").build();

        ConfluenceContentScanResult encrypted2 = sampleScanResult().toBuilder()
            .detectedPIIList(List.of(DetectedPersonallyIdentifiableInformation.builder().build(), DetectedPersonallyIdentifiableInformation.builder().build()))
            .build();
        ConfluenceContentScanResult decrypted2 = encrypted2.toBuilder().message("dec2").build();

        ScanEventEntity entity1 = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        ScanEventEntity entity2 = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndPageIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), eq(PAGE_ID), any()))
            .thenReturn(List.of(entity1, entity2));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(ConfluenceContentScanResult.class)))
            .thenReturn(encrypted1, encrypted2);
        when(scanResultEncryptor.decrypt(encrypted1)).thenReturn(decrypted1);
        when(scanResultEncryptor.decrypt(encrypted2)).thenReturn(decrypted2);

        List<ConfluenceContentScanResult> results = adapter.listItemEventsDecrypted(SCAN_ID, PAGE_ID, AccessPurpose.USER_DISPLAY);

        assertThat(results).containsExactly(decrypted1, decrypted2);
        // totalPiiCount = 1 + 2 = 3
        verify(auditService).auditPiiAccess(
            SCAN_ID,
            SPACE_KEY,
            PAGE_ID,
            decrypted1.pageTitle(),
            AccessPurpose.USER_DISPLAY,
            3
        );
    }

    @Test
    void Should_FilterOutNullResults_When_DecryptedEventsHaveInvalidPayloadOrError() throws Exception {
        ScanEventEntity nullPayload = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(null)
            .build();

        ScanEventEntity errorEntity = ScanEventEntity.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .payload(samplePayloadNode())
            .build();

        when(eventRepository.findByScanIdAndPageIdAndEventTypeInOrderByEventSeqAsc(eq(SCAN_ID), eq(PAGE_ID), any()))
            .thenReturn(List.of(nullPayload, errorEntity));
        when(objectMapper.treeToValue(any(JsonNode.class), eq(ConfluenceContentScanResult.class)))
            .thenThrow(new RuntimeException("boom"));

        List<ConfluenceContentScanResult> results = adapter.listItemEventsDecrypted(SCAN_ID, PAGE_ID, AccessPurpose.USER_DISPLAY);

        assertThat(results).isEmpty();
        verifyNoInteractions(auditService);
    }

    private ConfluenceContentScanResult sampleScanResult() {
        return ConfluenceContentScanResult.builder()
            .scanId(SCAN_ID)
            .spaceKey(SPACE_KEY)
            .pageId(PAGE_ID)
            .pageTitle("Page title")
            .eventType("item")
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    private JsonNode samplePayloadNode() {
        return new ObjectMapper().createObjectNode();
    }
}

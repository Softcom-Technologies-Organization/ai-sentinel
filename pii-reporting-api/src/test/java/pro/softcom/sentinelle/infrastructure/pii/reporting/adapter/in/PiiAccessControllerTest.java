package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.softcom.sentinelle.application.pii.reporting.config.PiiReportingProperties;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageRevealRequest;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageSecretsResponse;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.RevealedSecret;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiAccessController - REST controller for PII access control")
class PiiAccessControllerTest {

    @Mock
    private PiiReportingProperties reportingProperties;

    @Mock
    private ScanResultQuery scanResultQuery;

    private PiiAccessController controller;

    @BeforeEach
    void setUp() {
        controller = new PiiAccessController(reportingProperties, scanResultQuery);
    }

    // ========== isRevealAllowed() Tests ==========

    @Nested
    @DisplayName("isRevealAllowed() method tests")
    class IsRevealAllowedTests {

        @Test
        @DisplayName("Should_ReturnTrue_When_RevealIsAllowedByConfiguration")
        void Should_ReturnTrue_When_RevealIsAllowedByConfiguration() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            // When
            ResponseEntity<@NonNull Boolean> response = controller.isRevealAllowed();

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isTrue();
            });
            verify(reportingProperties).isAllowSecretReveal();
        }

        @Test
        @DisplayName("Should_ReturnFalse_When_RevealIsNotAllowedByConfiguration")
        void Should_ReturnFalse_When_RevealIsNotAllowedByConfiguration() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(false);

            // When
            ResponseEntity<@NonNull Boolean> response = controller.isRevealAllowed();

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isFalse();
            });
            verify(reportingProperties).isAllowSecretReveal();
        }

        @Test
        @DisplayName("Should_CallPropertiesOnlyOnce_When_CheckingRevealAllowed")
        void Should_CallPropertiesOnlyOnce_When_CheckingRevealAllowed() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            // When
            controller.isRevealAllowed();

            // Then
            verify(reportingProperties, times(1)).isAllowSecretReveal();
            verifyNoMoreInteractions(reportingProperties);
        }
    }

    // ========== revealPageSecrets() Tests ==========

    @Nested
    @DisplayName("revealPageSecrets() method tests")
    class RevealPageSecretsTests {

        private static final String SCAN_ID = "scan-123";
        private static final String PAGE_ID = "page-456";
        private static final String PAGE_TITLE = "Test Page";

        @Test
        @DisplayName("Should_ReturnForbidden_When_RevealNotAllowedByConfiguration")
        void Should_ReturnForbidden_When_RevealNotAllowedByConfiguration() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(false);
            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(reportingProperties).isAllowSecretReveal();
            verifyNoInteractions(scanResultQuery);
        }

        @Test
        @DisplayName("Should_ReturnNotFound_When_NoResultsFound")
        void Should_ReturnNotFound_When_NoResultsFound() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);
            when(scanResultQuery.listItemEventsDecrypted(
                    eq(SCAN_ID),
                    eq(PAGE_ID),
                    eq(AccessPurpose.USER_DISPLAY)
            )).thenReturn(Collections.emptyList());
            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(reportingProperties).isAllowSecretReveal();
            verify(scanResultQuery).listItemEventsDecrypted(SCAN_ID, PAGE_ID, AccessPurpose.USER_DISPLAY);
        }

        @Test
        @DisplayName("Should_ReturnSecrets_When_ResultsFoundWithEntities")
        void Should_ReturnSecrets_When_ResultsFoundWithEntities() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            PiiEntity entity1 = createEntity(0, 10, "john@example.com", "Email: john@example.com", "Email: [EMAIL]");
            PiiEntity entity2 = createEntity(20, 30, "1234567890", "Phone: 1234567890", "Phone: [PHONE]");

            ScanResult scanResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle(PAGE_TITLE)
                    .detectedEntities(List.of(entity1, entity2))
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(
                    eq(SCAN_ID),
                    eq(PAGE_ID),
                    eq(AccessPurpose.USER_DISPLAY)
            )).thenReturn(List.of(scanResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();

                PageSecretsResponse body = response.getBody();
                softly.assertThat(body.spaceKey()).isNotNull();
                softly.assertThat(body.spaceKey()).isEqualTo(SCAN_ID);
                softly.assertThat(body.pageId()).isEqualTo(PAGE_ID);
                softly.assertThat(body.pageTitle()).isEqualTo(PAGE_TITLE);
                softly.assertThat(body.secrets()).hasSize(2);

                RevealedSecret secret1 = body.secrets().getFirst();
                softly.assertThat(secret1.startPosition()).isZero();
                softly.assertThat(secret1.endPosition()).isEqualTo(10);
                softly.assertThat(secret1.sensitiveValue()).isEqualTo("john@example.com");
                softly.assertThat(secret1.sensitiveContext()).isEqualTo("Email: john@example.com");
                softly.assertThat(secret1.maskedContext()).isEqualTo("Email: [EMAIL]");

                RevealedSecret secret2 = body.secrets().get(1);
                softly.assertThat(secret2.startPosition()).isEqualTo(20);
                softly.assertThat(secret2.endPosition()).isEqualTo(30);
                softly.assertThat(secret2.sensitiveValue()).isEqualTo("1234567890");
            });

            verify(reportingProperties).isAllowSecretReveal();
            verify(scanResultQuery).listItemEventsDecrypted(SCAN_ID, PAGE_ID, AccessPurpose.USER_DISPLAY);
        }

        @Test
        @DisplayName("Should_ReturnEmptySecretsList_When_ResultHasNoEntities")
        void Should_ReturnEmptySecretsList_When_ResultHasNoEntities() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            ScanResult scanResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle(PAGE_TITLE)
                    .detectedEntities(Collections.emptyList())
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(
                    eq(SCAN_ID),
                    eq(PAGE_ID),
                    eq(AccessPurpose.USER_DISPLAY)
            )).thenReturn(List.of(scanResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should_TakeFirstResult_When_MultipleResultsReturned")
        void Should_TakeFirstResult_When_MultipleResultsReturned() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            ScanResult firstResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle("First Page")
                    .detectedEntities(List.of(createEntity(0, 5, "test", "context", "masked")))
                    .build();

            ScanResult secondResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle("Second Page")
                    .detectedEntities(Collections.emptyList())
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(
                    eq(SCAN_ID),
                    eq(PAGE_ID),
                    eq(AccessPurpose.USER_DISPLAY)
            )).thenReturn(List.of(firstResult, secondResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().pageTitle()).isEqualTo("First Page");
                softly.assertThat(response.getBody().secrets()).hasSize(1);
            });
        }

        @Test
        @DisplayName("Should_CallQueryWithCorrectParameters_When_RevealingSecrets")
        void Should_CallQueryWithCorrectParameters_When_RevealingSecrets() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);
            when(scanResultQuery.listItemEventsDecrypted(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            controller.revealPageSecrets(request);

            // Then
            verify(scanResultQuery).listItemEventsDecrypted(
                    eq(SCAN_ID),
                    eq(PAGE_ID),
                    eq(AccessPurpose.USER_DISPLAY)
            );
        }

        @Test
        @DisplayName("Should_HandleSingleSecret_When_OnlyOneEntityPresent")
        void Should_HandleSingleSecret_When_OnlyOneEntityPresent() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            PiiEntity entity = createEntity(
                    10,
                    25,
                    "secret@example.com",
                    "Contact: secret@example.com",
                    "Contact: [EMAIL]"
            );

            ScanResult scanResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle(PAGE_TITLE)
                    .detectedEntities(List.of(entity))
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(any(), any(), any()))
                    .thenReturn(List.of(scanResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).hasSize(1);

                RevealedSecret secret = response.getBody().secrets().getFirst();
                softly.assertThat(secret.startPosition()).isEqualTo(10);
                softly.assertThat(secret.endPosition()).isEqualTo(25);
                softly.assertThat(secret.sensitiveValue()).isEqualTo("secret@example.com");
            });
        }

        @Test
        @DisplayName("Should_HandleMultipleSecrets_When_ManyEntitiesPresent")
        void Should_HandleMultipleSecrets_When_ManyEntitiesPresent() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            List<PiiEntity> entities = List.of(
                    createEntity(0, 10, "entity1", "ctx1", "mask1"),
                    createEntity(10, 20, "entity2", "ctx2", "mask2"),
                    createEntity(20, 30, "entity3", "ctx3", "mask3"),
                    createEntity(30, 40, "entity4", "ctx4", "mask4")
            );

            ScanResult scanResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle(PAGE_TITLE)
                    .detectedEntities(entities)
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(any(), any(), any()))
                    .thenReturn(List.of(scanResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).hasSize(4);
            });
        }

        @Test
        @DisplayName("Should_PreserveEntityOrder_When_MappingToSecrets")
        void Should_PreserveEntityOrder_When_MappingToSecrets() {
            // Given
            when(reportingProperties.isAllowSecretReveal()).thenReturn(true);

            List<PiiEntity> entities = List.of(
                    createEntity(0, 5, "first", "ctx1", "mask1"),
                    createEntity(10, 15, "second", "ctx2", "mask2"),
                    createEntity(20, 25, "third", "ctx3", "mask3")
            );

            ScanResult scanResult = ScanResult.builder()
                    .scanId(SCAN_ID)
                    .pageId(PAGE_ID)
                    .pageTitle(PAGE_TITLE)
                    .detectedEntities(entities)
                    .build();

            when(scanResultQuery.listItemEventsDecrypted(any(), any(), any()))
                    .thenReturn(List.of(scanResult));

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<@NonNull PageSecretsResponse> response = controller.revealPageSecrets(request);

            // Then
            List<RevealedSecret> secrets = response.getBody().secrets();
            assertSoftly(softly -> {
                softly.assertThat(secrets.get(0).sensitiveValue()).isEqualTo("first");
                softly.assertThat(secrets.get(1).sensitiveValue()).isEqualTo("second");
                softly.assertThat(secrets.get(2).sensitiveValue()).isEqualTo("third");
            });
        }
    }

    // ========== Helper Methods ==========

    private PiiEntity createEntity(
            int startPos,
            int endPos,
            String sensitiveValue,
            String sensitiveContext,
            String maskedContext
    ) {
        return PiiEntity.builder()
                .startPosition(startPos)
                .endPosition(endPos)
                .piiType("EMAIL")
                .piiTypeLabel("Email Address")
                .confidence(0.95)
                .sensitiveValue(sensitiveValue)
                .sensitiveContext(sensitiveContext)
                .maskedContext(maskedContext)
                .build();
    }
}

package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.softcom.sentinelle.application.pii.reporting.port.in.RevealPiiSecretsPort;
import pro.softcom.sentinelle.domain.pii.reporting.PageSecretsResponse;
import pro.softcom.sentinelle.domain.pii.reporting.RevealedSecret;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageRevealRequest;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageSecretsResponseDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.RevealedSecretDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiAccessController - REST controller for PII access control")
class PiiAccessControllerTest {

    @Mock
    private RevealPiiSecretsPort revealPiiSecretsPort;

    @Mock
    private PageSecretsResponseMapper mapper;

    private PiiAccessController controller;

    @BeforeEach
    void setUp() {
        controller = new PiiAccessController(revealPiiSecretsPort, mapper);
    }

    // ========== isRevealAllowed() Tests ==========

    @Nested
    @DisplayName("isRevealAllowed() method tests")
    class IsRevealAllowedTests {

        @Test
        @DisplayName("Should_ReturnTrue_When_RevealIsAllowedByConfiguration")
        void Should_ReturnTrue_When_RevealIsAllowedByConfiguration() {
            // Given
            when(revealPiiSecretsPort.isRevealAllowed()).thenReturn(true);

            // When
            ResponseEntity<@NonNull Boolean> response = controller.isRevealAllowed();

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isTrue();
            });
            verify(revealPiiSecretsPort).isRevealAllowed();
        }

        @Test
        @DisplayName("Should_ReturnFalse_When_RevealIsNotAllowedByConfiguration")
        void Should_ReturnFalse_When_RevealIsNotAllowedByConfiguration() {
            // Given
            when(revealPiiSecretsPort.isRevealAllowed()).thenReturn(false);

            // When
            ResponseEntity<@NonNull Boolean> response = controller.isRevealAllowed();

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isFalse();
            });
            verify(revealPiiSecretsPort).isRevealAllowed();
        }

        @Test
        @DisplayName("Should_CallPortOnlyOnce_When_CheckingRevealAllowed")
        void Should_CallPortOnlyOnce_When_CheckingRevealAllowed() {
            // Given
            when(revealPiiSecretsPort.isRevealAllowed()).thenReturn(true);

            // When
            controller.isRevealAllowed();

            // Then
            verify(revealPiiSecretsPort, times(1)).isRevealAllowed();
            verifyNoMoreInteractions(revealPiiSecretsPort);
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
        @DisplayName("Should_ReturnForbidden_When_SecurityExceptionThrown")
        void Should_ReturnForbidden_When_SecurityExceptionThrown() {
            // Given
            when(revealPiiSecretsPort.revealPageSecrets(any(), any()))
                    .thenThrow(new SecurityException("Not allowed"));
            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(revealPiiSecretsPort).revealPageSecrets(SCAN_ID, PAGE_ID);
        }

        @Test
        @DisplayName("Should_ReturnNotFound_When_PortReturnsEmpty")
        void Should_ReturnNotFound_When_PortReturnsEmpty() {
            // Given
            when(revealPiiSecretsPort.revealPageSecrets(SCAN_ID, PAGE_ID))
                    .thenReturn(Optional.empty());
            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(revealPiiSecretsPort).revealPageSecrets(SCAN_ID, PAGE_ID);
        }

        @Test
        @DisplayName("Should_ReturnSecrets_When_PortReturnsResults")
        void Should_ReturnSecrets_When_PortReturnsResults() {
            // Given
            RevealedSecret secret1 = new RevealedSecret(0, 10, "john@example.com", 
                    "Email: john@example.com", "Email: [EMAIL]");
            RevealedSecret secret2 = new RevealedSecret(20, 30, "1234567890", 
                    "Phone: 1234567890", "Phone: [PHONE]");

            PageSecretsResponse domainResponse = new PageSecretsResponse(
                    SCAN_ID,
                    PAGE_ID,
                    PAGE_TITLE,
                    List.of(secret1, secret2)
            );

            RevealedSecretDto secretDto1 = new RevealedSecretDto(0, 10, "john@example.com",
                    "Email: john@example.com", "Email: [EMAIL]");
            RevealedSecretDto secretDto2 = new RevealedSecretDto(20, 30, "1234567890",
                    "Phone: 1234567890", "Phone: [PHONE]");
            PageSecretsResponseDto dto = new PageSecretsResponseDto(SCAN_ID, PAGE_ID, PAGE_TITLE,
                    List.of(secretDto1, secretDto2));

            when(revealPiiSecretsPort.revealPageSecrets(SCAN_ID, PAGE_ID))
                    .thenReturn(Optional.of(domainResponse));
            when(mapper.toDto(domainResponse)).thenReturn(dto);

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();

                PageSecretsResponseDto body = response.getBody();
                softly.assertThat(body.scanId()).isEqualTo(SCAN_ID);
                softly.assertThat(body.pageId()).isEqualTo(PAGE_ID);
                softly.assertThat(body.pageTitle()).isEqualTo(PAGE_TITLE);
                softly.assertThat(body.secrets()).hasSize(2);

                RevealedSecretDto actualDto1 = body.secrets().getFirst();
                softly.assertThat(actualDto1.startPosition()).isZero();
                softly.assertThat(actualDto1.endPosition()).isEqualTo(10);
                softly.assertThat(actualDto1.sensitiveValue()).isEqualTo("john@example.com");
                softly.assertThat(actualDto1.sensitiveContext()).isEqualTo("Email: john@example.com");
                softly.assertThat(actualDto1.maskedContext()).isEqualTo("Email: [EMAIL]");

                RevealedSecretDto actualDto2 = body.secrets().get(1);
                softly.assertThat(actualDto2.startPosition()).isEqualTo(20);
                softly.assertThat(actualDto2.endPosition()).isEqualTo(30);
                softly.assertThat(actualDto2.sensitiveValue()).isEqualTo("1234567890");
            });

            verify(revealPiiSecretsPort).revealPageSecrets(SCAN_ID, PAGE_ID);
            verify(mapper).toDto(domainResponse);
        }

        @Test
        @DisplayName("Should_ReturnEmptySecretsList_When_ResponseHasNoSecrets")
        void Should_ReturnEmptySecretsList_When_ResponseHasNoSecrets() {
            // Given
            PageSecretsResponse domainResponse = new PageSecretsResponse(
                    SCAN_ID,
                    PAGE_ID,
                    PAGE_TITLE,
                    Collections.emptyList()
            );

            PageSecretsResponseDto dto = new PageSecretsResponseDto(SCAN_ID, PAGE_ID, PAGE_TITLE,
                    Collections.emptyList());

            when(revealPiiSecretsPort.revealPageSecrets(SCAN_ID, PAGE_ID))
                    .thenReturn(Optional.of(domainResponse));
            when(mapper.toDto(domainResponse)).thenReturn(dto);

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should_CallPortWithCorrectParameters_When_RevealingSecrets")
        void Should_CallPortWithCorrectParameters_When_RevealingSecrets() {
            // Given
            when(revealPiiSecretsPort.revealPageSecrets(any(), any()))
                    .thenReturn(Optional.empty());

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            controller.revealPageSecrets(request);

            // Then
            verify(revealPiiSecretsPort).revealPageSecrets(SCAN_ID, PAGE_ID);
        }

        @Test
        @DisplayName("Should_HandleSingleSecret_When_OnlyOneSecretPresent")
        void Should_HandleSingleSecret_When_OnlyOneSecretPresent() {
            // Given
            RevealedSecret secret = new RevealedSecret(
                    10,
                    25,
                    "secret@example.com",
                    "Contact: secret@example.com",
                    "Contact: [EMAIL]"
            );

            PageSecretsResponse domainResponse = new PageSecretsResponse(
                    SCAN_ID,
                    PAGE_ID,
                    PAGE_TITLE,
                    List.of(secret)
            );

            RevealedSecretDto secretDto = new RevealedSecretDto(10, 25, "secret@example.com",
                    "Contact: secret@example.com", "Contact: [EMAIL]");
            PageSecretsResponseDto dto = new PageSecretsResponseDto(SCAN_ID, PAGE_ID, PAGE_TITLE,
                    List.of(secretDto));

            when(revealPiiSecretsPort.revealPageSecrets(any(), any()))
                    .thenReturn(Optional.of(domainResponse));
            when(mapper.toDto(any())).thenReturn(dto);

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).hasSize(1);

                RevealedSecretDto actualDto = response.getBody().secrets().getFirst();
                softly.assertThat(actualDto.startPosition()).isEqualTo(10);
                softly.assertThat(actualDto.endPosition()).isEqualTo(25);
                softly.assertThat(actualDto.sensitiveValue()).isEqualTo("secret@example.com");
            });
        }

        @Test
        @DisplayName("Should_HandleMultipleSecrets_When_ManySecretsPresent")
        void Should_HandleMultipleSecrets_When_ManySecretsPresent() {
            // Given
            List<RevealedSecret> secrets = List.of(
                    new RevealedSecret(0, 10, "entity1", "ctx1", "mask1"),
                    new RevealedSecret(10, 20, "entity2", "ctx2", "mask2"),
                    new RevealedSecret(20, 30, "entity3", "ctx3", "mask3"),
                    new RevealedSecret(30, 40, "entity4", "ctx4", "mask4")
            );

            PageSecretsResponse domainResponse = new PageSecretsResponse(
                    SCAN_ID,
                    PAGE_ID,
                    PAGE_TITLE,
                    secrets
            );

            List<RevealedSecretDto> secretDtos = List.of(
                    new RevealedSecretDto(0, 10, "entity1", "ctx1", "mask1"),
                    new RevealedSecretDto(10, 20, "entity2", "ctx2", "mask2"),
                    new RevealedSecretDto(20, 30, "entity3", "ctx3", "mask3"),
                    new RevealedSecretDto(30, 40, "entity4", "ctx4", "mask4")
            );
            PageSecretsResponseDto dto = new PageSecretsResponseDto(SCAN_ID, PAGE_ID, PAGE_TITLE, secretDtos);

            when(revealPiiSecretsPort.revealPageSecrets(any(), any()))
                    .thenReturn(Optional.of(domainResponse));
            when(mapper.toDto(any())).thenReturn(dto);

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isNotNull();
                softly.assertThat(response.getBody().secrets()).hasSize(4);
            });
        }

        @Test
        @DisplayName("Should_PreserveSecretOrder_When_MappingToDtos")
        void Should_PreserveSecretOrder_When_MappingToDtos() {
            // Given
            List<RevealedSecret> secrets = List.of(
                    new RevealedSecret(0, 5, "first", "ctx1", "mask1"),
                    new RevealedSecret(10, 15, "second", "ctx2", "mask2"),
                    new RevealedSecret(20, 25, "third", "ctx3", "mask3")
            );

            PageSecretsResponse domainResponse = new PageSecretsResponse(
                    SCAN_ID,
                    PAGE_ID,
                    PAGE_TITLE,
                    secrets
            );

            List<RevealedSecretDto> secretDtos = List.of(
                    new RevealedSecretDto(0, 5, "first", "ctx1", "mask1"),
                    new RevealedSecretDto(10, 15, "second", "ctx2", "mask2"),
                    new RevealedSecretDto(20, 25, "third", "ctx3", "mask3")
            );
            PageSecretsResponseDto dto = new PageSecretsResponseDto(SCAN_ID, PAGE_ID, PAGE_TITLE, secretDtos);

            when(revealPiiSecretsPort.revealPageSecrets(any(), any()))
                    .thenReturn(Optional.of(domainResponse));
            when(mapper.toDto(any())).thenReturn(dto);

            PageRevealRequest request = new PageRevealRequest(SCAN_ID, PAGE_ID);

            // When
            ResponseEntity<PageSecretsResponseDto> response = controller.revealPageSecrets(request);

            // Then
            List<RevealedSecretDto> actualDtos = response.getBody().secrets();
            assertSoftly(softly -> {
                softly.assertThat(actualDtos.get(0).sensitiveValue()).isEqualTo("first");
                softly.assertThat(actualDtos.get(1).sensitiveValue()).isEqualTo("second");
                softly.assertThat(actualDtos.get(2).sensitiveValue()).isEqualTo("third");
            });
        }
    }
}

package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.sentinelle.domain.pii.reporting.PageSecretsResponse;
import pro.softcom.sentinelle.domain.pii.reporting.RevealedSecret;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.PageSecretsResponseDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.PiiAccessController.RevealedSecretDto;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("PageSecretsResponseMapper - Maps domain models to DTOs")
class PageSecretsResponseMapperTest {

    private PageSecretsResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PageSecretsResponseMapper();
    }

    @Test
    @DisplayName("Should_MapAllFields_When_ResponseHasSecrets")
    void Should_MapAllFields_When_ResponseHasSecrets() {
        // Given
        RevealedSecret secret1 = new RevealedSecret(
                0, 10, "john@example.com",
                "Email: john@example.com", "Email: [EMAIL]"
        );
        RevealedSecret secret2 = new RevealedSecret(
                20, 30, "1234567890",
                "Phone: 1234567890", "Phone: [PHONE]"
        );

        PageSecretsResponse domainResponse = new PageSecretsResponse(
                "scan-123",
                "page-456",
                "Test Page",
                List.of(secret1, secret2)
        );

        // When
        PageSecretsResponseDto dto = mapper.toDto(domainResponse);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(dto.scanId()).isEqualTo("scan-123");
            softly.assertThat(dto.pageId()).isEqualTo("page-456");
            softly.assertThat(dto.pageTitle()).isEqualTo("Test Page");
            softly.assertThat(dto.secrets()).hasSize(2);

            RevealedSecretDto secretDto1 = dto.secrets().get(0);
            softly.assertThat(secretDto1.startPosition()).isZero();
            softly.assertThat(secretDto1.endPosition()).isEqualTo(10);
            softly.assertThat(secretDto1.sensitiveValue()).isEqualTo("john@example.com");
            softly.assertThat(secretDto1.sensitiveContext()).isEqualTo("Email: john@example.com");
            softly.assertThat(secretDto1.maskedContext()).isEqualTo("Email: [EMAIL]");

            RevealedSecretDto secretDto2 = dto.secrets().get(1);
            softly.assertThat(secretDto2.startPosition()).isEqualTo(20);
            softly.assertThat(secretDto2.endPosition()).isEqualTo(30);
            softly.assertThat(secretDto2.sensitiveValue()).isEqualTo("1234567890");
        });
    }

    @Test
    @DisplayName("Should_MapEmptySecretsList_When_ResponseHasNoSecrets")
    void Should_MapEmptySecretsList_When_ResponseHasNoSecrets() {
        // Given
        PageSecretsResponse domainResponse = new PageSecretsResponse(
                "scan-123",
                "page-456",
                "Empty Page",
                Collections.emptyList()
        );

        // When
        PageSecretsResponseDto dto = mapper.toDto(domainResponse);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(dto.scanId()).isEqualTo("scan-123");
            softly.assertThat(dto.pageId()).isEqualTo("page-456");
            softly.assertThat(dto.pageTitle()).isEqualTo("Empty Page");
            softly.assertThat(dto.secrets()).isEmpty();
        });
    }

    @Test
    @DisplayName("Should_PreserveOrder_When_MappingMultipleSecrets")
    void Should_PreserveOrder_When_MappingMultipleSecrets() {
        // Given
        List<RevealedSecret> secrets = List.of(
                new RevealedSecret(0, 5, "first", "ctx1", "mask1"),
                new RevealedSecret(10, 15, "second", "ctx2", "mask2"),
                new RevealedSecret(20, 25, "third", "ctx3", "mask3")
        );

        PageSecretsResponse domainResponse = new PageSecretsResponse(
                "scan-123", "page-456", "Test", secrets
        );

        // When
        PageSecretsResponseDto dto = mapper.toDto(domainResponse);

        // Then
        List<RevealedSecretDto> secretDtos = dto.secrets();
        assertSoftly(softly -> {
            softly.assertThat(secretDtos).hasSize(3);
            softly.assertThat(secretDtos.get(0).sensitiveValue()).isEqualTo("first");
            softly.assertThat(secretDtos.get(1).sensitiveValue()).isEqualTo("second");
            softly.assertThat(secretDtos.get(2).sensitiveValue()).isEqualTo("third");
        });
    }

    @Test
    @DisplayName("Should_MapSingleSecret_When_OnlyOneSecretPresent")
    void Should_MapSingleSecret_When_OnlyOneSecretPresent() {
        // Given
        RevealedSecret secret = new RevealedSecret(
                10, 25, "secret@example.com",
                "Contact: secret@example.com", "Contact: [EMAIL]"
        );

        PageSecretsResponse domainResponse = new PageSecretsResponse(
                "scan-123", "page-456", "Single Secret", List.of(secret)
        );

        // When
        PageSecretsResponseDto dto = mapper.toDto(domainResponse);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(dto.secrets()).hasSize(1);
            RevealedSecretDto secretDto = dto.secrets().get(0);
            softly.assertThat(secretDto.startPosition()).isEqualTo(10);
            softly.assertThat(secretDto.endPosition()).isEqualTo(25);
            softly.assertThat(secretDto.sensitiveValue()).isEqualTo("secret@example.com");
            softly.assertThat(secretDto.sensitiveContext()).isEqualTo("Contact: secret@example.com");
            softly.assertThat(secretDto.maskedContext()).isEqualTo("Contact: [EMAIL]");
        });
    }

    @Test
    @DisplayName("Should_HandleManySecrets_When_LargeListProvided")
    void Should_HandleManySecrets_When_LargeListProvided() {
        // Given
        List<RevealedSecret> secrets = List.of(
                new RevealedSecret(0, 10, "v1", "c1", "m1"),
                new RevealedSecret(10, 20, "v2", "c2", "m2"),
                new RevealedSecret(20, 30, "v3", "c3", "m3"),
                new RevealedSecret(30, 40, "v4", "c4", "m4"),
                new RevealedSecret(40, 50, "v5", "c5", "m5")
        );

        PageSecretsResponse domainResponse = new PageSecretsResponse(
                "scan-123", "page-456", "Many Secrets", secrets
        );

        // When
        PageSecretsResponseDto dto = mapper.toDto(domainResponse);

        // Then
        assertThat(dto.secrets()).hasSize(5);
    }
}

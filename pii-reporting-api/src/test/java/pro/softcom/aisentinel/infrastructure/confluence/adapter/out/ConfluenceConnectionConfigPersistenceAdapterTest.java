package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.ConfluenceConnectionConfigJpaRepository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceConnectionConfigEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfluenceConnectionConfigPersistenceAdapterTest {

    @Mock
    private ConfluenceConnectionConfigJpaRepository jpaRepository;

    @InjectMocks
    private ConfluenceConnectionConfigPersistenceAdapter adapter;

    private ConfluenceConnectionConfigEntity buildEntity() {
        return ConfluenceConnectionConfigEntity.builder()
                .id(1)
                .baseUrl("https://confluence.example.com")
                .username("user@example.com")
                .apiTokenEncrypted("encrypted-token")
                .connectTimeout(30000)
                .readTimeout(60000)
                .maxRetries(3)
                .pagesLimit(50)
                .deploymentType(ConfluenceDeploymentType.CLOUD)
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedBy("admin")
                .build();
    }

    @Test
    void Should_ReturnMappedDomain_When_ConfigExists() {
        when(jpaRepository.findById(1)).thenReturn(Optional.of(buildEntity()));

        ConfluenceConnectionSettings result = adapter.findConfig();

        assertThat(result.baseUrl()).isEqualTo("https://confluence.example.com");
        assertThat(result.username()).isEqualTo("user@example.com");
        assertThat(result.connectTimeout()).isEqualTo(30000);
        assertThat(result.deploymentType()).isEqualTo(ConfluenceDeploymentType.CLOUD);
    }

    @Test
    void Should_ReturnDefaultSettings_When_ConfigNotFound() {
        when(jpaRepository.findById(1)).thenReturn(Optional.empty());

        ConfluenceConnectionSettings result = adapter.findConfig();

        assertThat(result.baseUrl()).isEmpty();
        assertThat(result.username()).isEmpty();
        assertThat(result.connectTimeout()).isEqualTo(30000);
        assertThat(result.deploymentType()).isEqualTo(ConfluenceDeploymentType.CLOUD);
    }

    @Test
    void Should_SaveEntity_When_UpdateConfigCalled() {
        ConfluenceConnectionSettings settings = new ConfluenceConnectionSettings(
                1, "https://new.confluence.com", "new-user",
                15000, 30000, 2, 25,
                ConfluenceDeploymentType.CLOUD,
                Instant.now(), "admin"
        );
        ArgumentCaptor<ConfluenceConnectionConfigEntity> captor =
                ArgumentCaptor.forClass(ConfluenceConnectionConfigEntity.class);

        adapter.updateConfig(settings, "encrypted-new-token");

        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://new.confluence.com");
        assertThat(captor.getValue().getApiTokenEncrypted()).isEqualTo("encrypted-new-token");
    }

    @Test
    void Should_ThrowIllegalArgument_When_UpdateConfigCalledWithNullSettings() {
        assertThatThrownBy(() -> adapter.updateConfig(null, "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration cannot be null");
    }

    @Test
    void Should_ReturnEncryptedToken_When_TokenExists() {
        when(jpaRepository.findById(1)).thenReturn(Optional.of(buildEntity()));

        String token = adapter.getEncryptedApiToken();

        assertThat(token).isEqualTo("encrypted-token");
    }

    @Test
    void Should_ReturnEmptyString_When_TokenNotFound() {
        when(jpaRepository.findById(1)).thenReturn(Optional.empty());

        String token = adapter.getEncryptedApiToken();

        assertThat(token).isEmpty();
    }

    @Test
    void Should_UseEmptyEncryptedToken_When_UpdateConfigCalledWithNullToken() {
        ConfluenceConnectionSettings settings = new ConfluenceConnectionSettings(
                1, "https://confluence.com", "user",
                10000, 20000, 1, 25,
                ConfluenceDeploymentType.DATA_CENTER,
                Instant.now(), "system"
        );
        ArgumentCaptor<ConfluenceConnectionConfigEntity> captor =
                ArgumentCaptor.forClass(ConfluenceConnectionConfigEntity.class);

        adapter.updateConfig(settings, null);

        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getApiTokenEncrypted()).isEmpty();
    }
}

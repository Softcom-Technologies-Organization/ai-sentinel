package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Persistence adapter for PII detection configuration.
 * Implements database access using Spring Data JPA for single-row configuration pattern.
 */
@Component
@Slf4j
public class PiiDetectionConfigPersistenceAdapter implements PiiDetectionConfigRepository {

    private static final Integer CONFIG_ID = 1;
    private static final int DEFAULT_MINISTRAL_CHUNK_SIZE = 2048;
    private static final int DEFAULT_MINISTRAL_OVERLAP = 410;
    private static final String DEFAULT_LM_STUDIO_HOST = "localhost";
    private static final int DEFAULT_LM_STUDIO_PORT = 1234;
    private static final int DEFAULT_MINISTRAL_CONCURRENCY = 1;
    private static final String BENCH_STATUS_IDLE = "IDLE";
    private static final String BENCH_STATUS_PENDING = "PENDING";
    private static final String BENCH_STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    private static final String BENCH_STATUS_CANCELLED = "CANCELLED";
    private static final int DEFAULT_BENCH_MAX_CONCURRENCY = 4;

    private final PiiDetectionConfigJpaRepository jpaRepository;

    private final PiiDetectionConfigRepository self;

    public PiiDetectionConfigPersistenceAdapter(PiiDetectionConfigJpaRepository jpaRepository,
                                                @Lazy PiiDetectionConfigRepository self) {
        this.jpaRepository = jpaRepository;
        this.self = self;
    }

    @Override
    @Transactional
    public PiiDetectionConfig findConfig() {
        log.debug("Retrieving PII detection configuration");
        
        return jpaRepository.findById(CONFIG_ID)
                .map(this::toDomain)
                .orElseGet(() -> {
                    log.warn("Configuration not found, creating default configuration");
                    return createDefaultConfig();
                });
    }

    @Override
    @Transactional
    public void updateConfig(PiiDetectionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        
        log.info("Updating PII detection configuration: presidioEnabled={}, " +
                "regexEnabled={}, ministralEnabled={}, threshold={}, postfilterEnabled={}, updatedBy={}",
                config.presidioEnabled(), config.regexEnabled(), config.ministralEnabled(),
                config.defaultThreshold(), config.postfilterEnabled(), config.updatedBy());


        PiiDetectionConfigEntity entity = toEntity(config);
        // The bench job columns are owned by the benchmark workflow (the
        // detector service reports progress there); a config save must not
        // reset them.
        jpaRepository.findById(CONFIG_ID).ifPresent(existing -> copyBenchState(existing, entity));
        jpaRepository.save(entity);

        log.info("PII detection configuration updated successfully");
    }

    @Override
    @Transactional
    public void requestBenchmark(int maxConcurrency) {
        log.info("Flagging on-demand concurrency benchmark request (up to concurrency {})", maxConcurrency);

        PiiDetectionConfigEntity entity = requireConfigEntity();
        entity.setConcurrencyBenchRequested(true);
        entity.setConcurrencyBenchStatus(BENCH_STATUS_PENDING);
        entity.setConcurrencyBenchProgress(0);
        entity.setConcurrencyBenchMessage(null);
        entity.setConcurrencyBenchMaxConcurrency(maxConcurrency);
        jpaRepository.save(entity);

        log.info("Concurrency benchmark request flagged successfully");
    }

    @Override
    @Transactional
    public void cancelBenchmark() {
        PiiDetectionConfigEntity entity = requireConfigEntity();
        if (Boolean.TRUE.equals(entity.getConcurrencyBenchRequested())) {
            // Not claimed by the detector service yet: withdraw the request outright.
            entity.setConcurrencyBenchRequested(false);
            entity.setConcurrencyBenchStatus(BENCH_STATUS_CANCELLED);
            entity.setConcurrencyBenchMessage("Benchmark cancelled before it started");
        } else {
            // Claimed and running: the detector service stops at its next check.
            entity.setConcurrencyBenchStatus(BENCH_STATUS_CANCEL_REQUESTED);
        }
        jpaRepository.save(entity);
        log.info("Concurrency benchmark cancellation persisted (status {})", entity.getConcurrencyBenchStatus());
    }

    @Override
    @Transactional
    public ConcurrencyBenchStatus findBenchStatus() {
        log.debug("Retrieving concurrency benchmark status");

        PiiDetectionConfigEntity entity = requireConfigEntity();
        return new ConcurrencyBenchStatus(
                entity.getConcurrencyBenchStatus() != null ? entity.getConcurrencyBenchStatus() : BENCH_STATUS_IDLE,
                entity.getConcurrencyBenchProgress() != null ? entity.getConcurrencyBenchProgress() : 0,
                entity.getConcurrencyBenchMessage(),
                entity.getMinistralConcurrency() != null ? entity.getMinistralConcurrency() : DEFAULT_MINISTRAL_CONCURRENCY,
                entity.getMinistralConcurrencyTunedSignature(),
                entity.getConcurrencyBenchMaxConcurrency() != null
                        ? entity.getConcurrencyBenchMaxConcurrency() : DEFAULT_BENCH_MAX_CONCURRENCY
        );
    }

    /**
     * Returns the singleton configuration row, bootstrapping the default
     * configuration when it does not exist yet.
     */
    private PiiDetectionConfigEntity requireConfigEntity() {
        return jpaRepository.findById(CONFIG_ID).orElseGet(() -> {
            self.findConfig();
            return jpaRepository.findById(CONFIG_ID)
                    .orElseThrow(() -> new IllegalStateException("PII detection configuration row could not be created"));
        });
    }

    private void copyBenchState(PiiDetectionConfigEntity source, PiiDetectionConfigEntity target) {
        target.setConcurrencyBenchRequested(source.getConcurrencyBenchRequested());
        target.setConcurrencyBenchStatus(source.getConcurrencyBenchStatus());
        target.setConcurrencyBenchProgress(source.getConcurrencyBenchProgress());
        target.setConcurrencyBenchMessage(source.getConcurrencyBenchMessage());
        target.setConcurrencyBenchMaxConcurrency(source.getConcurrencyBenchMaxConcurrency());
    }

    /**
     * Creates and persists default configuration.
     * Default: Presidio and regex enabled, threshold 0.75, post-filter OFF.
     */
    private PiiDetectionConfig createDefaultConfig() {
        log.info("Creating default PII detection configuration");

        PiiDetectionConfig defaultConfig = new PiiDetectionConfig(
                CONFIG_ID,
                true,  // presidioEnabled
                true,  // regexEnabled
                false, // ministralEnabled (explicit operator opt-in)
                DEFAULT_MINISTRAL_CHUNK_SIZE, // ministralChunkSize
                DEFAULT_MINISTRAL_OVERLAP, // ministralOverlap
                new BigDecimal("0.75"),  // defaultThreshold
                false, // postfilterEnabled (zero-effect rollout default)
                DEFAULT_LM_STUDIO_HOST, // lmStudioHost
                DEFAULT_LM_STUDIO_PORT, // lmStudioPort
                null,  // lmStudioModel (detector service default)
                DEFAULT_MINISTRAL_CONCURRENCY, // ministralConcurrency (sequential)
                true,  // ministralConcurrencyAuto (auto-tune at startup)
                null,  // ministralConcurrencyTunedSignature (never tuned)
                LocalDateTime.now(ZoneId.of("UTC")),
                "system"
        );

        self.updateConfig(defaultConfig);
        return defaultConfig;
    }

    /**
     * Maps JPA entity to domain model.
     */
    private PiiDetectionConfig toDomain(PiiDetectionConfigEntity entity) {
        return new PiiDetectionConfig(
                entity.getId(),
                entity.getPresidioEnabled(),
                entity.getRegexEnabled(),
                entity.getMinistralEnabled() != null && entity.getMinistralEnabled(),
                entity.getMinistralChunkSize() != null ? entity.getMinistralChunkSize() : DEFAULT_MINISTRAL_CHUNK_SIZE,
                entity.getMinistralOverlap() != null ? entity.getMinistralOverlap() : DEFAULT_MINISTRAL_OVERLAP,
                entity.getDefaultThreshold(),
                entity.getPostfilterEnabled() != null && entity.getPostfilterEnabled(),
                entity.getLmStudioHost() != null ? entity.getLmStudioHost() : DEFAULT_LM_STUDIO_HOST,
                entity.getLmStudioPort() != null ? entity.getLmStudioPort() : DEFAULT_LM_STUDIO_PORT,
                entity.getLmStudioModel(),
                entity.getMinistralConcurrency() != null ? entity.getMinistralConcurrency() : DEFAULT_MINISTRAL_CONCURRENCY,
                entity.getMinistralConcurrencyAuto() == null || entity.getMinistralConcurrencyAuto(),
                entity.getMinistralConcurrencyTunedSignature(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    /**
     * Maps domain model to JPA entity.
     */
    private PiiDetectionConfigEntity toEntity(PiiDetectionConfig config) {
        return PiiDetectionConfigEntity.builder()
                .id(CONFIG_ID)
                .presidioEnabled(config.presidioEnabled())
                .regexEnabled(config.regexEnabled())
                .ministralEnabled(config.ministralEnabled())
                .ministralChunkSize(config.ministralChunkSize())
                .ministralOverlap(config.ministralOverlap())
                .defaultThreshold(config.defaultThreshold())
                .postfilterEnabled(config.postfilterEnabled())
                .lmStudioHost(config.lmStudioHost())
                .lmStudioPort(config.lmStudioPort())
                .lmStudioModel(config.lmStudioModel())
                .ministralConcurrency(config.ministralConcurrency())
                .ministralConcurrencyAuto(config.ministralConcurrencyAuto())
                .ministralConcurrencyTunedSignature(config.ministralConcurrencyTunedSignature())
                .updatedAt(config.updatedAt() != null ? config.updatedAt() : LocalDateTime.now(ZoneId.of("UTC")))
                .updatedBy(config.updatedBy())
                .build();
    }
}
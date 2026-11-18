package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DiscardedSensitiveData;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.SensitiveData;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

/** Controlled candidates isolate postfilter transport from detector recognition. */
@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FormatPostfilterDiscardSmokeIT {

    private static final Logger log = LoggerFactory.getLogger(FormatPostfilterDiscardSmokeIT.class);

    private static final String SQL_SEED = "classpath:sql/data-presidio-regex.sql";
    private static final int GRPC_PORT = 50051;

    private static final String POSTGRES_ALIAS = "postgres-it";
    private static final String DB_NAME = "ai-sentinel";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    /** Vrai IP à 4 octets valides : doit traverser le pré-filtre (TP conservé). */
    private static final String VALID_IP = "10.217.4.11";
    /** Vrai IBAN suisse (mod-97 OK) : doit traverser le pré-filtre (TP conservé). */
    private static final String VALID_IBAN = "CH9300762011623852957";

    /**
     * Texte court (PLAN.md §5) : un faux IP non parsable, une plage horaire
     * (faux MAC), un faux IBAN mod-97 KO, plus une vraie IP et un vrai IBAN.
     */
    private static final String SAMPLE_TEXT =
        "Trace réseau du log applicatif : sortie vd.ch. 3600 et fragment 0.244.999.7 ; "
        + "plage horaire 13:56:49-13:56:52 ; IBAN bidon CH00 0000 0000 0000 0000 0 ; "
        + "serveur de production " + VALID_IP + " ; IBAN client " + VALID_IBAN + ".";

    /** Raisons déterministes produites par les stratégies GO (IP / MAC / IBAN). */
    private static final String[] EXPECTED_REASON_FRAGMENTS =
        {"parse failed", "separator", "mod-97"};

    private static final Path HF_CACHE_DIR = Paths.get(
        System.getProperty("corpus.bench.hf-cache",
            System.getProperty("user.home") + "/.ai-sentinel-it-hf-cache"));

    private static final Network NETWORK = Network.newNetwork();
    private static final Logger CONTAINER_LOG = LoggerFactory.getLogger("pii-detector-container");

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD)
            .withNetwork(NETWORK)
            .withNetworkAliases(POSTGRES_ALIAS);

    // Pas de mock LM Studio : le post-filtre est purement déterministe et
    // n'appelle aucun LLM.
    @Container
    static final GenericContainer<?> piiDetector = new GenericContainer<>(buildPiiDetectorImage())
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(""))
        .withCopyFileToContainer(MountableFile.forClasspathResource("python/format_postfilter_fixture.py"),
            "/app/format_postfilter_fixture.py")
        .withCommand("python", "format_postfilter_fixture.py", "--port", String.valueOf(GRPC_PORT))
        .withExposedPorts(GRPC_PORT)
        .withNetwork(NETWORK)
        .withNetworkAliases("pii-detector")
        .withFileSystemBind(ensureHfCacheDir(), "/app/.cache/huggingface", BindMode.READ_WRITE)
        .withEnv("HF_HOME", "/app/.cache/huggingface")
        .withEnv("TRANSFORMERS_CACHE", "/app/.cache/huggingface")
        .withEnv("DB_HOST", POSTGRES_ALIAS)
        .withEnv("DB_PORT", "5432")
        .withEnv("DB_NAME", DB_NAME)
        .withEnv("DB_USER", DB_USER)
        .withEnv("DB_PASSWORD", DB_PASSWORD)
        .withLogConsumer(FormatPostfilterDiscardSmokeIT::routeContainerLog)
        .waitingFor(Wait.forLogMessage(".*Server started on port.*", 1))
        .withStartupTimeout(Duration.ofMinutes(10));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",               postgres::getJdbcUrl);
        registry.add("spring.datasource.username",          postgres::getUsername);
        registry.add("spring.datasource.password",          postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto",       () -> "update");
        registry.add("spring.jpa.show-sql",                 () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
                                                            () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("pii-detector.host", piiDetector::getHost);
        registry.add("pii-detector.port", () -> piiDetector.getMappedPort(GRPC_PORT));
        registry.add("pii-detector.connection-timeout-ms", () -> "600000");
        registry.add("pii-detector.request-timeout-ms",    () -> "600000");

        registry.add("PII_DATABASE_ENCRYPTION_KEY",
            () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("PII_REPORTING_ALLOW_SECRET_REVEAL", () -> "false");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PiiDetectorClient piiDetectorClient;

    @Test
    void Should_TransportRejectedCandidates_When_PostfilterIsEnabled() throws Exception {
        log.info("[postfilter-smoke] === START ===");
        resetAndReseedDbWithPostfilterEnabled();

        ContentPiiDetection detection = piiDetectorClient.analyzeContent(SAMPLE_TEXT);

        log.info("[postfilter-smoke] kept={} discarded={}",
            detection.sensitiveDataFound().size(), detection.discardedByPostfilter().size());
        detection.discardedByPostfilter().forEach(d -> log.info(
            "[postfilter-smoke] [postfilter-FP] type={} detector={} value={} verdict={} confidence={} reason={}",
            d.data().type(), d.data().source(), d.data().value(),
            d.judgeVerdict(), d.judgeConfidence(), d.judgeReason()));
        detection.sensitiveDataFound().forEach(s -> log.info(
            "[postfilter-smoke] [KEPT] type={} detector={} value={}",
            s.type(), s.source(), s.value()));

        Assertions.assertFalse(detection.discardedByPostfilter().isEmpty(),
            "Le pré-filtre doit écarter au moins une valeur au format impossible "
            + "(faux IP / plage horaire / faux IBAN). Si vide, le canal "
            + "discarded_entities (proto/python/java) est cassé, le flag "
            + "postfilter_enabled n'est pas pris en compte, ou le détecteur n'a "
            + "rien remonté sur ces valeurs.");
        Assertions.assertEquals(3, detection.discardedByPostfilter().size());
        Assertions.assertEquals(2, detection.sensitiveDataFound().size());

        for (DiscardedSensitiveData discarded : detection.discardedByPostfilter()) {
            Assertions.assertEquals("FALSE_POSITIVE", discarded.judgeVerdict(),
                "verdict inattendu pour " + discarded);
            Assertions.assertTrue(discarded.judgeConfidence() > 0.9,
                "confidence inattendue (le pré-filtre est déterministe, confidence=1.0) pour "
                + discarded);
            Assertions.assertTrue(containsExpectedReason(discarded.judgeReason()),
                "raison déterministe attendue (parse failed / separator / mod-97), trouvé : "
                + discarded.judgeReason());
            Assertions.assertNotNull(discarded.data().source(),
                "detector source manquant pour " + discarded);
            Assertions.assertFalse(discarded.data().type().isBlank(),
                "pii type manquant pour " + discarded);
        }

        // Invariant fort et déterministe : un vrai positif bien formé ne doit
        // JAMAIS être écarté par le pré-filtre.
        Assertions.assertFalse(discardContainsValue(detection, VALID_IP),
            "La vraie IP " + VALID_IP + " ne doit jamais être écartée par le pré-filtre.");
        Assertions.assertFalse(discardContainsValue(detection, VALID_IBAN),
            "Le vrai IBAN " + VALID_IBAN + " ne doit jamais être écarté par le pré-filtre.");

        // Au moins un des deux vrais positifs doit rester détecté (recall
        // préservé de bout en bout) : on n'exige pas que les DEUX soient remontés.
        boolean validKept = keptContainsValue(detection, VALID_IP)
            || keptContainsValue(detection, VALID_IBAN);
        Assertions.assertTrue(validKept,
            "Au moins une des valeurs valides (" + VALID_IP + " / " + VALID_IBAN
            + ") doit rester dans sensitiveDataFound, trouvé : "
            + detection.sensitiveDataFound());

        jdbcTemplate.update("UPDATE pii_detection_config SET postfilter_enabled = false WHERE id = 1");
        ContentPiiDetection unfiltered = piiDetectorClient.analyzeContent(SAMPLE_TEXT);
        Assertions.assertTrue(unfiltered.discardedByPostfilter().isEmpty());
        Assertions.assertEquals(5, unfiltered.sensitiveDataFound().size());

        log.info("[postfilter-smoke] === DONE — pré-filtre opérationnel via le canal partagé ===");
    }

    private static boolean containsExpectedReason(String reason) {
        if (reason == null) {
            return false;
        }
        for (String fragment : EXPECTED_REASON_FRAGMENTS) {
            if (reason.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean discardContainsValue(ContentPiiDetection detection, String value) {
        return detection.discardedByPostfilter().stream()
            .anyMatch(d -> value.equals(d.data().value()));
    }

    private static boolean keptContainsValue(ContentPiiDetection detection, String value) {
        return detection.sensitiveDataFound().stream()
            .map(SensitiveData::value)
            .anyMatch(v -> v != null && v.contains(value));
    }

    private void resetAndReseedDbWithPostfilterEnabled() throws SQLException {
        jdbcTemplate.execute("DELETE FROM pii_type_config");
        Resource resource = new DefaultResourceLoader().getResource(SQL_SEED);
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new EncodedResource(resource, StandardCharsets.UTF_8));
        }
        jdbcTemplate.execute(
            "UPDATE pii_detection_config SET postfilter_enabled = true WHERE id = 1");
        log.info("[postfilter-smoke] DB reseed depuis {} + postfilter_enabled=true", SQL_SEED);
    }

    // ========================================================================
    // Container plumbing
    // ========================================================================

    private static String ensureHfCacheDir() {
        try {
            Files.createDirectories(HF_CACHE_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create HF cache dir: " + HF_CACHE_DIR, e);
        }
        return HF_CACHE_DIR.toString();
    }

    private static void routeContainerLog(OutputFrame frame) {
        String raw = frame.getUtf8String();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String line = raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;
        if (line.contains(" - DEBUG - ")) {
            CONTAINER_LOG.debug(line);
        } else if (line.contains(" - WARNING - ") || line.contains(" - WARN - ")) {
            CONTAINER_LOG.warn(line);
        } else if (line.contains(" - ERROR - ") || line.contains(" - CRITICAL - ")) {
            CONTAINER_LOG.error(line);
        } else {
            CONTAINER_LOG.info(line);
        }
    }

    private static ImageFromDockerfile buildPiiDetectorImage() {
        Path repoRoot = Paths.get("..").toAbsolutePath().normalize();
        Path detectorRoot = repoRoot.resolve("pii-detector-service");
        return new ImageFromDockerfile("ai-sentinel-pii-detector-it", false)
            .withFileFromPath("pii-detector-service/Dockerfile",     detectorRoot.resolve("Dockerfile"))
            .withFileFromPath("pii-detector-service/pyproject.toml", detectorRoot.resolve("pyproject.toml"))
            .withFileFromPath("pii-detector-service/README.md",      detectorRoot.resolve("README.md"))
            .withFileFromPath("pii-detector-service/pii_detector",   detectorRoot.resolve("pii_detector"))
            .withFileFromPath("pii-detector-service/config",         detectorRoot.resolve("config"))
            .withFileFromPath("pii-detector-service/docker-entrypoint.sh",
                detectorRoot.resolve("docker-entrypoint.sh"))
            .withFileFromPath("proto", repoRoot.resolve("proto"))
            .withDockerfilePath("pii-detector-service/Dockerfile");
    }
}

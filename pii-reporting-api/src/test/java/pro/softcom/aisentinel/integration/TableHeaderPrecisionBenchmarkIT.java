package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.pii.reporting.service.DetectionOffsetRemapper;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent.Segment;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.SensitiveData;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "table.header.benchmark", matches = "true")
class TableHeaderPrecisionBenchmarkIT {

    private static final Logger log = LoggerFactory.getLogger(TableHeaderPrecisionBenchmarkIT.class);
    private static final String MODEL = "ministral-3b-pii-preview@q8_0";
    private static final String LM_STUDIO_HOST = "host.docker.internal";
    private static final int LM_STUDIO_PORT = 1234;
    private static final int GRPC_PORT = 50051;
    private static final Network NETWORK = Network.newNetwork();
    private static final Path TOKENIZER_CACHE = Path.of(System.getProperty("user.home"), ".cache", "huggingface", "hub",
            "models--OpenMed--Ministral-3B-PII-Preview");

    private static final List<Sample> SAMPLES = List.of(
        new Sample("PIN_DISABLED", """
            <table><tr><th>Service</th><th>Port</th><th>Code PIN</th></tr>
            <tr><td>SMTP</td><td>587</td><td>4186</td></tr>
            <tr><td>SFTP</td><td>22</td><td>7319</td></tr>
            <tr><td>Proxy</td><td>8080</td><td>6245</td></tr></table>
            """, Set.of()),
        new Sample("PASSWORD", """
            <table><tr><th>Application</th><th>Version</th><th>Mot de passe</th></tr>
            <tr><td>Archive</td><td>2026</td><td>MoonLake!734</td></tr>
            <tr><td>Intranet</td><td>4210</td><td>RiverStone!926</td></tr></table>
            """, Set.of(new Finding("PASSWORD", "MoonLake!734"),
                new Finding("PASSWORD", "RiverStone!926")))
    );

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("ai-sentinel")
        .withUsername("postgres")
        .withPassword("postgres")
        .withNetwork(NETWORK)
        .withNetworkAliases("postgres-it");

    @Container
    static final GenericContainer<?> piiDetector = new GenericContainer<>(detectorImage())
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(""))
        .withCommand("python", "-m", "pii_detector.server", "--port", String.valueOf(GRPC_PORT))
        .withExposedPorts(GRPC_PORT)
        .withNetwork(NETWORK)
        .withEnv("DB_HOST", "postgres-it")
        .withEnv("DB_PORT", "5432")
        .withEnv("DB_NAME", "ai-sentinel")
        .withEnv("DB_USER", "postgres")
        .withEnv("DB_PASSWORD", "postgres")
        .withFileSystemBind(TOKENIZER_CACHE.toString(),
                "/root/.cache/huggingface/hub/models--OpenMed--Ministral-3B-PII-Preview", BindMode.READ_ONLY)
        .withEnv("HF_HUB_OFFLINE", "1")
        .waitingFor(Wait.forLogMessage(".*Server started on port.*", 1))
        .withStartupTimeout(Duration.ofMinutes(10));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("pii-detector.host", piiDetector::getHost);
        registry.add("pii-detector.port", () -> piiDetector.getMappedPort(GRPC_PORT));
        registry.add("pii-detector.connection-timeout-ms", () -> "600000");
        registry.add("pii-detector.request-timeout-ms", () -> "600000");
        registry.add("PII_DATABASE_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("PII_REPORTING_ALLOW_SECRET_REVEAL", () -> "false");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PiiDetectorClient piiDetectorClient;
    @Autowired private AttachmentTextExtractor attachmentTextExtractor;

    @BeforeEach
    void configureDetectors() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        var models = piiDetectorClient.listLmStudioModels(LM_STUDIO_HOST, LM_STUDIO_PORT);
        assertThat(models.failed()).as(models.error()).isFalse();
        assertThat(models.models()).anySatisfy(model -> {
            assertThat(model.id()).isEqualTo(MODEL);
            assertThat(model.loaded()).isTrue();
        });
        jdbcTemplate.update("""
            UPDATE pii_detection_config
            SET presidio_enabled = true, regex_enabled = true, ministral_enabled = true,
                postfilter_enabled = true, ministral_concurrency_auto = false,
                lm_studio_host = ?, lm_studio_port = ?, lm_studio_model = ?
            WHERE id = 1
            """, LM_STUDIO_HOST, LM_STUDIO_PORT, MODEL);
        jdbcTemplate.update("""
            UPDATE pii_type_config SET enabled = false WHERE pii_type = 'PIN'
            """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pii_type_config WHERE pii_type = 'PIN' AND enabled", Integer.class)).isZero();
    }

    @Test
    void Should_ImproveDetection_When_TableHeadersAreRepeatedByCell() {
        var parser = new HtmlContentParser();
        Score baseline = new Score(0, 0, 0);
        Score enriched = new Score(0, 0, 0);
        for (Sample sample : SAMPLES) {
            Score sampleBaseline = new Score(0, 0, 0);
            Score sampleEnriched = new Score(0, 0, 0);
            ExtractedContent content = parser.extractContent(sample.html());
            assertThat(content.analysisText()).isNotEqualTo(content.contextText());
            for (Finding expected : sample.expected()) {
                assertThat(content.contextText()).contains(expected.value());
            }
            for (int repetition = 0; repetition < 3; repetition++) {
                sampleBaseline = sampleBaseline.add(score(DetectionOffsetRemapper.remap(
                    detect(content.contextText()), withoutLabels(content)), sample.expected()));
                sampleEnriched = sampleEnriched.add(score(DetectionOffsetRemapper.remap(
                    detect(content.analysisText()), content), sample.expected()));
            }
            log.info("Table header benchmark sample={} baseline={} enriched={}",
                sample.name(), sampleBaseline, sampleEnriched);
            baseline = baseline.add(sampleBaseline);
            enriched = enriched.add(sampleEnriched);
        }

        log.info("Table header benchmark model={} baseline={} enriched={}", MODEL, baseline, enriched);
        assertThat(enriched.truePositives()).isGreaterThanOrEqualTo(baseline.truePositives());
        assertThat(enriched.falsePositives()).isLessThanOrEqualTo(baseline.falsePositives());
        assertThat(enriched.f1()).isGreaterThan(baseline.f1());
    }

    @Test
    void Should_RemoveCoordinateCardsAndKeepRealCards_When_OriginalCsvCrossesTheFullStack() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/tables/correspondance_commune_arrondissement.csv")) {
            bytes = input.readAllBytes();
        }
        var content = extractCsv(bytes);
        var flattened = detect(content.contextText().replace(" | ", " "));
        var enriched = DetectionOffsetRemapper.remap(detect(content.analysisText()), content);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        var withoutHeaders = extractCsv(csv.substring(csv.indexOf('\n') + 1).getBytes(StandardCharsets.UTF_8));
        var headerless = DetectionOffsetRemapper.remap(detect(withoutHeaders.analysisText()), withoutHeaders);

        assertThat(cards(flattened)).hasSize(26);
        assertThat(cards(enriched)).isEmpty();
        assertThat(cards(headerless)).isEmpty();
        var controls = extractCsv(("Reference;Value\nCard;4111 1111 1111 1111\n"
                + "Coordinates;1138667\nCoordinates;2505047").getBytes(StandardCharsets.UTF_8));
        var detectedControls = DetectionOffsetRemapper.remap(detect(controls.analysisText()), controls);
        assertThat(cards(detectedControls)).extracting(SensitiveData::value).containsExactly("4111 1111 1111 1111");
        log.info("CSV regression: flattened cards={}, enriched cards={}, headerless cards={}, retained control cards={}",
                cards(flattened).size(), cards(enriched).size(), cards(headerless).size(), cards(detectedControls).size());
    }

    private ExtractedContent extractCsv(byte[] bytes) {
        return attachmentTextExtractor.extractText(new AttachmentInfo("benchmark.csv", "csv", "text/csv", "test"), bytes)
                .orElseThrow();
    }

    private static List<SensitiveData> cards(ContentPiiDetection result) {
        return result.sensitiveDataFound().stream().filter(data -> data.type().equals("CREDIT_CARD")
                && data.source() == DetectorSource.PRESIDIO).toList();
    }

    private static ExtractedContent withoutLabels(ExtractedContent content) {
        return new ExtractedContent(content.contextText(), content.contextText(), content.segments().stream()
                .map(segment -> new Segment(segment.contextStart(), segment.contextStart(), segment.length()))
                .toList());
    }

    private ContentPiiDetection detect(String text) {
        ContentPiiDetection result = piiDetectorClient.analyzeContent(text);
        assertThat(result.sensitiveDataFound()).noneMatch(data -> data.type().equals("PIN"));
        assertThat(result.detectorRunStats()).extracting(stat -> stat.source())
                .contains(DetectorSource.MINISTRAL, DetectorSource.PRESIDIO, DetectorSource.REGEX);
        assertThat(result.detectorRunStats()).allSatisfy(stat -> {
            assertThat(stat.failed()).as(stat.error()).isFalse();
        });
        assertThat(piiDetector.getLogs()).doesNotContain("MINISTRAL_TOKENIZER_LOAD_FAILED");
        return result;
    }

    private static Score score(ContentPiiDetection result, Set<Finding> expected) {
        Set<Finding> missing = new HashSet<>(expected);
        int truePositives = 0;
        int falsePositives = 0;
        for (SensitiveData data : result.sensitiveDataFound()) {
            if (missing.remove(new Finding(data.type(), data.value()))) {
                truePositives++;
            } else {
                falsePositives++;
            }
        }
        return new Score(truePositives, falsePositives, missing.size());
    }

    private static ImageFromDockerfile detectorImage() {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path detector = root.resolve("pii-detector-service");
        return new ImageFromDockerfile("ai-sentinel-table-header-benchmark-it", false)
            .withFileFromPath("pii-detector-service/Dockerfile", detector.resolve("Dockerfile"))
            .withFileFromPath("pii-detector-service/pyproject.toml", detector.resolve("pyproject.toml"))
            .withFileFromPath("pii-detector-service/README.md", detector.resolve("README.md"))
            .withFileFromPath("pii-detector-service/pii_detector", detector.resolve("pii_detector"))
            .withFileFromPath("pii-detector-service/config", detector.resolve("config"))
            .withFileFromPath("pii-detector-service/docker-entrypoint.sh", detector.resolve("docker-entrypoint.sh"))
            .withFileFromPath("proto", root.resolve("proto"))
            .withDockerfilePath("pii-detector-service/Dockerfile");
    }

    private record Finding(String type, String value) {
    }

    private record Sample(String name, String html, Set<Finding> expected) {
    }

    private record Score(int truePositives, int falsePositives, int falseNegatives) {
        Score add(Score other) {
            return new Score(truePositives + other.truePositives,
                falsePositives + other.falsePositives, falseNegatives + other.falseNegatives);
        }

        double f1() {
            int denominator = 2 * truePositives + falsePositives + falseNegatives;
            return denominator == 0 ? 0 : 2.0 * truePositives / denominator;
        }
    }
}

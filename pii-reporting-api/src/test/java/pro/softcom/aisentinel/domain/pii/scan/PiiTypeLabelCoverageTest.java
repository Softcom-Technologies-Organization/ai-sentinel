package pro.softcom.aisentinel.domain.pii.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard-rail (anti-regression) test for PII type label coverage.
 *
 * <p>Business purpose: the gRPC client adapter resolves the human-readable FR
 * label of every detected entity through
 * {@link PersonallyIdentifiableInformationType#valueOf(String)}. Any
 * {@code pii_type} configured in the DB seed scripts that lacks a matching enum
 * constant silently falls back to the raw type and floods the logs with debug
 * lines. This test fails fast whenever a newly configured type is not mapped,
 * pointing the developer to the exact constant to add.
 *
 * <p>It parses the production seed ({@code data.sql}) and every test seed
 * ({@code sql/data-*.sql}), normalizes each {@code pii_type} exactly like the
 * adapter (trim / upper-case / spaces and hyphens to underscores), and asserts
 * that {@code valueOf} succeeds for each one.
 * National REGEX rule IDs are mapped to their emitted SOCIALNUM/TAX_ID types.
 *
 * <p>Resource discovery is classpath-based ({@link PathMatchingResourcePatternResolver})
 * so it works both from the exploded build directory and from a packaged jar,
 * independent of the working directory.
 */
class PiiTypeLabelCoverageTest {

    /** Matches the leading {@code ('TYPE','DETECTOR',} of each pii_type_config INSERT tuple. */
    private static final Pattern TUPLE_PATTERN = Pattern.compile(
            "\\(\\s*'([^']+)'\\s*,\\s*'(PRESIDIO|REGEX|MINISTRAL)'\\s*,");

    private static final String PRODUCTION_SEED = "classpath:data.sql";
    private static final String TEST_SEEDS = "classpath*:sql/data-*.sql";

    @Test
    @DisplayName("Should_ResolveLabelForEveryConfiguredPiiType_When_ParsingAllSeedSqlFiles")
    void Should_ResolveLabelForEveryConfiguredPiiType_When_ParsingAllSeedSqlFiles() throws IOException {
        Set<String> configuredTypes = collectConfiguredPiiTypes();

        assertThat(configuredTypes)
                .as("at least the production seed types must be discovered "
                        + "(empty set means the SQL files were not found on the classpath)")
                .isNotEmpty();

        TreeMap<String, String> missing = new TreeMap<>();
        for (String rawType : configuredTypes) {
            String normalized = normalizeLikeAdapter(rawType);
            if (!isMappedToEnum(normalized)) {
                missing.put(normalized, rawType);
            }
        }

        assertThat(missing)
                .as(buildFailureMessage(missing))
                .isEmpty();
    }

    private Set<String> collectConfiguredPiiTypes() throws IOException {
        Set<String> types = new LinkedHashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources(PRODUCTION_SEED)) {
            extractTypes(resource, types);
        }
        for (Resource resource : resolver.getResources(TEST_SEEDS)) {
            extractTypes(resource, types);
        }
        return types;
    }

    private void extractTypes(Resource resource, Set<String> types) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = TUPLE_PATTERN.matcher(content);
            while (matcher.find()) {
                types.add(emittedType(matcher.group(1), matcher.group(2)));
            }
        }
    }

    private String emittedType(String configuredType, String detector) {
        if (!"REGEX".equals(detector)) {
            return configuredType;
        }
        if (configuredType.startsWith("SSN_")) {
            return "SOCIALNUM";
        }
        return configuredType.startsWith("TAX_ID_") ? "TAX_ID" : configuredType;
    }

    /**
     * Mirrors {@code GrpcPiiDetectorArmeriaClientAdapter.convertToSensitiveData}:
     * {@code entity.getType().trim().toUpperCase().replace(" ", "_").replace("-", "_")}.
     */
    private String normalizeLikeAdapter(String rawType) {
        return rawType.trim().toUpperCase().replace(" ", "_").replace("-", "_");
    }

    private boolean isMappedToEnum(String normalizedType) {
        try {
            PersonallyIdentifiableInformationType.valueOf(normalizedType);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private String buildFailureMessage(TreeMap<String, String> missing) {
        StringBuilder sb = new StringBuilder()
                .append(missing.size())
                .append(" configured pii_type(s) have no constant in ")
                .append("ContentPiiDetection.PersonallyIdentifiableInformationType.\n")
                .append("Add a constant (with its FR label) in the matching section of that enum ")
                .append("(Contact / Identity / Location / Financial / Government IDs / IT & Credentials / ")
                .append("Medical / Assets / Temporal / Country-specific (Presidio)).\n")
                .append("Missing normalized type(s):\n");
        missing.forEach((normalized, raw) ->
                sb.append("  - ").append(normalized)
                        .append(" (from SQL pii_type '").append(raw).append("')\n"));
        return sb.toString();
    }
}

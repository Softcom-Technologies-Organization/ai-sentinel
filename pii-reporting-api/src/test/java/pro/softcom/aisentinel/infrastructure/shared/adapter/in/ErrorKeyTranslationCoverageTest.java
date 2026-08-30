package pro.softcom.aisentinel.infrastructure.shared.adapter.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between the backend and the frontend translation files: every errorKey
 * the backend can hand to the dashboard must exist in both languages, otherwise Transloco
 * renders the raw key to the end user.
 *
 * <p>Two shapes are covered, because the dashboard renders them differently: a REST or
 * remediation error resolves to a single sentence, while a scan error becomes a toast with a
 * title and a detail line.
 *
 * <p>{@code toTranslocoKey} below mirrors the function of the same name in
 * {@code error-notification.service.ts}; the two must be changed together.
 */
class ErrorKeyTranslationCoverageTest {

    /** Sources whose keys resolve to one sentence. */
    private static final List<Path> PLAIN_KEY_SOURCES = List.of(
            Path.of("src/main/java/pro/softcom/aisentinel/infrastructure/shared/adapter/in/GlobalExceptionHandler.java"),
            Path.of("src/main/java/pro/softcom/aisentinel/application/pii/remediation/service/ObfuscationJobRunner.java"),
            Path.of("src/main/java/pro/softcom/aisentinel/application/pii/remediation/usecase/ChangeFindingStatusUseCase.java"));

    /** Source whose keys become a toast, so they need both a title and a detail. */
    private static final Path TOAST_KEY_SOURCE = Path.of(
            "src/main/java/pro/softcom/aisentinel/domain/pii/scan/ScanErrorKeys.java");
    private static final Path I18N_DIR = Path.of("../pii-reporting-ui/src/assets/i18n");
    private static final Pattern ERROR_KEY = Pattern.compile("\"(error\\.[a-z0-9_.]+)\"");
    private static final Pattern SNAKE_SEPARATOR = Pattern.compile("_([a-z])");

    @Test
    @DisplayName("Should_ExposeSameKeys_When_ComparingEnglishAndFrench")
    void Should_ExposeSameKeys_When_ComparingEnglishAndFrench() throws IOException {
        assertThat(translationKeys("en")).containsExactlyInAnyOrderElementsOf(translationKeys("fr"));
    }

    @Test
    @DisplayName("Should_TranslateEveryBackendErrorKey_When_HandlerDeclaresIt")
    void Should_TranslateEveryBackendErrorKey_When_HandlerDeclaresIt() throws IOException {
        Set<String> english = translationKeys("en");
        Set<String> french = translationKeys("fr");
        Set<String> backendKeys = errorKeysIn(PLAIN_KEY_SOURCES);

        assertThat(backendKeys).isNotEmpty();

        List<String> untranslated = backendKeys.stream()
                .filter(key -> !english.contains(toTranslocoKey(key)) || !french.contains(toTranslocoKey(key)))
                .toList();

        assertThat(untranslated).isEmpty();
    }

    @Test
    @DisplayName("Should_GiveEveryScanErrorATitleAndADetail_When_ShownAsAToast")
    void Should_GiveEveryScanErrorATitleAndADetail_When_ShownAsAToast() throws IOException {
        Set<String> english = translationKeys("en");
        Set<String> french = translationKeys("fr");
        Set<String> scanKeys = errorKeysIn(List.of(TOAST_KEY_SOURCE));

        assertThat(scanKeys).isNotEmpty();

        List<String> incomplete = scanKeys.stream()
                .filter(key -> Stream.of(".title", ".detail")
                        .anyMatch(part -> !english.contains(toTranslocoKey(key) + part)
                                || !french.contains(toTranslocoKey(key) + part)))
                .toList();

        assertThat(incomplete).isEmpty();
    }

    private static Set<String> errorKeysIn(List<Path> sources) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path source : sources) {
            Matcher matcher = ERROR_KEY.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static Set<String> translationKeys(String language) throws IOException {
        JsonNode root = new ObjectMapper()
                .readTree(Files.readString(I18N_DIR.resolve(language + ".json"), StandardCharsets.UTF_8));
        Set<String> keys = new LinkedHashSet<>();
        collectLeafPaths(root, "", keys);
        return keys;
    }

    private static void collectLeafPaths(JsonNode node, String prefix, Set<String> keys) {
        node.properties().forEach(property -> {
            String path = prefix.isEmpty() ? property.getKey() : prefix + "." + property.getKey();
            if (property.getValue().isObject()) {
                collectLeafPaths(property.getValue(), path, keys);
            } else {
                keys.add(path);
            }
        });
    }

    private static String toTranslocoKey(String errorKey) {
        List<String> segments = new ArrayList<>(List.of(errorKey.substring("error.".length()).split("\\.")));
        String camelLast = SNAKE_SEPARATOR
                .matcher(segments.remove(segments.size() - 1))
                .replaceAll(match -> match.group(1).toUpperCase());

        if (segments.size() >= 2) {
            String parent = segments.remove(segments.size() - 1);
            String merged = parent + Character.toUpperCase(camelLast.charAt(0)) + camelLast.substring(1);
            return "errors." + String.join(".", segments) + "." + merged;
        }
        if (segments.isEmpty()) {
            return "errors." + camelLast;
        }
        return "errors." + String.join(".", segments) + "." + camelLast;
    }
}

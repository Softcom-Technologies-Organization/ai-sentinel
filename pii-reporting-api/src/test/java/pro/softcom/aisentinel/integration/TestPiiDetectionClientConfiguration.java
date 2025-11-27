package pro.softcom.aisentinel.integration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;

/**
 * Test configuration providing a fake PiiDetectionClient for integration tests.
 * Business intent: allow tests to run without external gRPC service while exercising the domain model.
 */
@TestConfiguration
public class TestPiiDetectionClientConfiguration {

    @Bean
    @Primary
    public PiiDetectorClient testPiiDetectionClient() {
        return new FakePiiDetectionClient();
    }

    static class FakePiiDetectionClient implements PiiDetectorClient {
        private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        private static final Pattern PHONE = Pattern.compile("(?:\\+\\d{2}\\s?)?(?:\\d{2,3}\\s?){3,5}");
        private static final Pattern URL = Pattern.compile("(https?://\\S+)|(\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b)");
        private static final Pattern AVS = Pattern.compile("\\b756\\.\\d{4}\\.\\d{4}\\.\\d{2}\\b");

        @Override
        public ContentPiiDetection analyzeContent(String content) {
            return analyzePageContent(null, null, null, content);
        }

        @Override
        public ContentPiiDetection analyzeContent(String content, float threshold) {
            return analyzePageContent(null, null, null, content, threshold);
        }

        @Override
        public ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) {
            return analyzePageContent(pageId, pageTitle, spaceKey, content, 0.5f);
        }

        @Override
        public ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) {
            // Clean HTML tags to plain text, like the real Python gRPC service does
            String cleanedContent = stripHtml(content);
            
            List<ContentPiiDetection.SensitiveData> items = new ArrayList<>();

            // Emails
            Matcher m = EMAIL.matcher(cleanedContent);
            while (m.find()) {
                items.add(new ContentPiiDetection.SensitiveData(
                    PersonallyIdentifiableInformationType.EMAIL,
                    m.group(),
                    ctx(m.start(), m.end()),
                    m.start(), m.end(), 0.95, "email"));
            }
            // Phones (simple heuristic)
            m = PHONE.matcher(cleanedContent);
            while (m.find()) {
                String value = m.group().trim();
                if (value.length() < 8 || value.contains("@")) continue; // avoid overlaps
                items.add(new ContentPiiDetection.SensitiveData(
                    PersonallyIdentifiableInformationType.PHONE,
                    value,
                    ctx(m.start(), m.end()),
                    m.start(), m.end(), 0.80, "phone"));
            }
            // AVS numbers
            m = AVS.matcher(cleanedContent);
            while (m.find()) {
                items.add(new ContentPiiDetection.SensitiveData(
                    PersonallyIdentifiableInformationType.AVS,
                    m.group(),
                    ctx(m.start(), m.end()),
                    m.start(), m.end(), 0.99, "avs"));
            }
            // URLs and IPs -> mark as ATTACHMENT for backward compat of the test
            m = URL.matcher(cleanedContent);
            while (m.find()) {
                items.add(new ContentPiiDetection.SensitiveData(
                    PersonallyIdentifiableInformationType.ATTACHMENT,
                    m.group(),
                    ctx(m.start(), m.end()),
                    m.start(), m.end(), 0.70, "url"));
            }
            // Simple security hints
            if (cleanedContent.toLowerCase().contains("password") || cleanedContent.toLowerCase().contains("sk-")) {
                items.add(new ContentPiiDetection.SensitiveData(
                    PersonallyIdentifiableInformationType.SECURITY,
                    "***",
                    "Detected security-like token",
                    0, 0, 0.9, "sec"));
            }

            Map<String, Integer> stats = new HashMap<>();
            for (ContentPiiDetection.SensitiveData sd : items) {
                stats.merge(sd.type().name(), 1, Integer::sum);
            }
            if (stats.isEmpty()) stats.put("NONE", 0);

            return ContentPiiDetection.builder()
                .pageId(pageId)
                .pageTitle(pageTitle)
                .spaceKey(spaceKey)
                .analysisDate(LocalDateTime.now())
                .sensitiveDataFound(items)
                .statistics(stats)
                .build();
        }

        private static String ctx(int start, int end) {
            return "Detected at position " + start + "-" + end;
        }
        
        /**
         * Strips HTML tags from content to extract plain text, mimicking real gRPC service behavior.
         * Uses Jsoup to parse HTML and extract text content, ensuring regex patterns work correctly
         * without interference from HTML tags (especially for word boundaries).
         */
        private static String stripHtml(String content) {
            if (content == null || content.isEmpty()) {
                return content;
            }
            try {
                return Jsoup.parse(content).text();
            } catch (Exception _) {
                // Fallback: return original content if Jsoup fails
                return content;
            }
        }
    }
}

package pro.softcom.aisentinel.application.pii.judge.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JudgeCategoryPolicy {

    private static final Map<String, String> PRESIDIO_TYPES = Map.of(
            "EMAIL_ADDRESS", "EMAIL", "PHONE_NUMBER", "PHONE", "PERSON", "PERSON_NAME",
            "IBAN_CODE", "IBAN", "DATE_TIME", "DATE", "CRYPTO", "CRYPTO_WALLET");

    private final PiiDetectionConfigRepository configRepository;
    private final PiiTypeConfigRepository typeRepository;

    public List<String> availableCategories() {
        return typeRepository.findAll().stream().map(PiiTypeConfig::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(JudgeCategoryPolicy::categoryKey).distinct().sorted().toList();
    }

    public List<String> validate(List<String> categories) {
        if (categories == null) {
            return null;
        }
        if (categories.stream().anyMatch(category -> category == null || category.isBlank())
                || !availableCategories().containsAll(categories)) {
            throw new IllegalArgumentException("Unknown judge category");
        }
        return List.copyOf(categories);
    }

    public boolean allows(FindingReference finding) {
        List<String> enabled = configRepository.findConfig().judgeEnabledCategories();
        if (enabled == null) {
            return true;
        }
        if (enabled.isEmpty()) {
            return false;
        }
        Set<String> categories = typeRepository.findAll().stream()
                .filter(type -> type.getDetector().equals(finding.detector()))
                .filter(type -> emittedType(type).equals(finding.piiType()))
                .map(PiiTypeConfig::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(JudgeCategoryPolicy::categoryKey).collect(Collectors.toSet());
        return !categories.isEmpty() && enabled.containsAll(categories);
    }

    private static String categoryKey(String category) {
        String key = category.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return key.equals("GOV_ID") ? "GOVERNMENT_ID" : key;
    }

    private static String emittedType(PiiTypeConfig type) {
        String key = type.getPiiType();
        if (type.getDetector().equals("PRESIDIO")) {
            return PRESIDIO_TYPES.getOrDefault(key, key);
        }
        return key;
    }
}

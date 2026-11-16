package pro.softcom.aisentinel.application.pii.judge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgeCategoryPolicyTest {
    private final PiiDetectionConfigRepository configs = mock(PiiDetectionConfigRepository.class);
    private final PiiTypeConfigRepository types = mock(PiiTypeConfigRepository.class);
    private final PiiDetectionConfig config = mock(PiiDetectionConfig.class);
    private final JudgeCategoryPolicy policy = new JudgeCategoryPolicy(configs, types);

    @BeforeEach
    void setUp() {
        when(configs.findConfig()).thenReturn(config);
        when(config.judgeEnabledCategories()).thenReturn(null);
    }

    @Test
    void Should_UnifyProductAliasesWithoutMergingPersonalIdentity_When_ListingCategories() {
        when(types.findAll()).thenReturn(List.of(type("Contact", "EMAIL_ADDRESS", "PRESIDIO"),
                type("CONTACT", "EMAIL", "MINISTRAL"), type("Financial", "IBAN_CODE", "PRESIDIO"),
                type("FINANCIAL", "IBAN", "MINISTRAL"), type("Government ID", "US_SSN", "PRESIDIO"),
                type("GOV_ID", "PASSPORT", "MINISTRAL"), type("Personal", "PERSON", "PRESIDIO"),
                type("IDENTITY", "FIRST_NAME", "MINISTRAL")));
        assertThat(policy.availableCategories()).containsExactly("CONTACT", "FINANCIAL", "GOVERNMENT_ID", "IDENTITY", "PERSONAL");
    }

    @Test
    void Should_AllowUnknownTypeOnlyForHistoricalConfiguration_When_NoSelectionStored() {
        assertThat(policy.allows(finding("UNKNOWN", "REGEX"))).isTrue();
        when(config.judgeEnabledCategories()).thenReturn(List.of());
        assertThat(policy.allows(finding("UNKNOWN", "REGEX"))).isFalse();
        when(config.judgeEnabledCategories()).thenReturn(List.of("CONTACT"));
        when(types.findAll()).thenReturn(List.of(type("CONTACT", "UNKNOWN", "PRESIDIO")));
        assertThat(policy.allows(finding("UNKNOWN", "REGEX"))).isFalse();
    }

    @Test
    void Should_ResolveVerifiedPresidioAliases_When_CatalogUsesDetectorTypes() {
        when(config.judgeEnabledCategories()).thenReturn(List.of("CONTACT", "PERSONAL", "FINANCIAL"));
        when(types.findAll()).thenReturn(List.of(type("Contact", "EMAIL_ADDRESS", "PRESIDIO"),
                type("Contact", "PHONE_NUMBER", "PRESIDIO"), type("Personal", "PERSON", "PRESIDIO"),
                type("Personal", "DATE_TIME", "PRESIDIO"), type("Financial", "IBAN_CODE", "PRESIDIO"),
                type("Financial", "CRYPTO", "PRESIDIO")));
        for (String emitted : List.of("EMAIL", "PHONE", "PERSON_NAME", "DATE", "IBAN", "CRYPTO_WALLET")) {
            assertThat(policy.allows(finding(emitted, "PRESIDIO"))).as(emitted).isTrue();
            assertThat(policy.allows(finding(emitted, "REGEX"))).as(emitted).isFalse();
        }
    }

    @Test
    void Should_RequireEveryPossibleCategory_When_RegexRulesShareEmittedType() {
        when(types.findAll()).thenReturn(List.of(type("IDENTITY", "SOCIALNUM", "REGEX"),
                type("SSN", "SSN_FR_NIR", "REGEX"), type("TAX_ID", "TAX_ID_CH_UID", "REGEX")));
        when(config.judgeEnabledCategories()).thenReturn(List.of("IDENTITY"));
        assertThat(policy.allows(finding("SOCIALNUM", "REGEX"))).isFalse();
        when(config.judgeEnabledCategories()).thenReturn(List.of("IDENTITY", "SSN", "TAX_ID"));
        assertThat(policy.allows(finding("SOCIALNUM", "REGEX"))).isTrue();
        assertThat(policy.allows(finding("TAX_ID", "REGEX"))).isTrue();
    }

    @Test
    void Should_RejectInvalidKeysAndCopySelection_When_Validating() {
        when(types.findAll()).thenReturn(List.of(type("Contact", "EMAIL_ADDRESS", "PRESIDIO")));
        assertThatThrownBy(() -> policy.validate(List.of("Contact"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(List.of("BOGUS"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(Arrays.asList((String) null))).isInstanceOf(IllegalArgumentException.class);
        List<String> mutable = new ArrayList<>(List.of("CONTACT"));
        List<String> validated = policy.validate(mutable);
        mutable.clear();
        assertThat(validated).containsExactly("CONTACT");
        assertThat(policy.validate(List.of())).isEmpty();
    }

    private static PiiTypeConfig type(String category, String type, String detector) {
        return PiiTypeConfig.builder().category(category).piiType(type).detector(detector).enabled(false).build();
    }

    private static FindingReference finding(String type, String detector) {
        return new FindingReference("S", "P", null, detector, type,
                PersonallyIdentifiableInformationSeverity.HIGH, "fingerprint");
    }
}

package pro.softcom.aisentinel.application.pii.remediation.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationPlan;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanObfuscationUseCase")
class PlanObfuscationUseCaseTest {

    @Mock
    private RemediationConfigPort remediationConfigPort;

    @Mock
    private SelectionResolver selectionResolver;

    private PlanObfuscationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlanObfuscationUseCase(remediationConfigPort, selectionResolver);
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("Should_ThrowRemediationDisabled_When_FeatureFlagIsOff")
    void Should_ThrowRemediationDisabled_When_FeatureFlagIsOff() {
        when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
        var selection = selection();

        assertThatThrownBy(() -> useCase.plan(selection))
                .isInstanceOf(RemediationDisabledException.class);
    }

    @Test
    @DisplayName("Should_AggregateResolvedFindings_When_SelectionResolves")
    void Should_AggregateResolvedFindings_When_SelectionResolves() {
        List<EligibleFinding> pageFindings = List.of(
                finding("EMAIL", "p1", null, "fp-1", PersonallyIdentifiableInformationSeverity.MEDIUM),
                finding("IBAN", "p1", null, "fp-2", PersonallyIdentifiableInformationSeverity.HIGH),
                finding("EMAIL", "p2", null, "fp-3", PersonallyIdentifiableInformationSeverity.MEDIUM));
        List<EligibleFinding> attachmentFindings = List.of(
                finding("EMAIL", "p1", "report.xlsx", "fp-4", PersonallyIdentifiableInformationSeverity.MEDIUM));
        when(selectionResolver.resolve(selection()))
                .thenReturn(new ResolvedSelection("scan-1", pageFindings, attachmentFindings, 2));

        ObfuscationPlan plan = useCase.plan(selection());

        assertSoftly(softly -> {
            softly.assertThat(plan.totalFindings()).isEqualTo(3);
            softly.assertThat(plan.bySeverity()).containsOnly(
                    Map.entry(PersonallyIdentifiableInformationSeverity.MEDIUM, 2),
                    Map.entry(PersonallyIdentifiableInformationSeverity.HIGH, 1));
            softly.assertThat(plan.pagesImpacted()).isEqualTo(2);
            softly.assertThat(plan.falsePositivesReported()).isEqualTo(2);
            softly.assertThat(plan.attachmentExclusions()).isEqualTo(1);
            softly.assertThat(plan.selectionChecksum()).isEqualTo(ObfuscationPlan.checksumOf(
                    pageFindings.stream().map(EligibleFinding::findingId).toList()));
        });
    }

    @Test
    @DisplayName("Should_ReturnEmptyPlanWithChecksum_When_NothingResolves")
    void Should_ReturnEmptyPlanWithChecksum_When_NothingResolves() {
        when(selectionResolver.resolve(selection())).thenReturn(ResolvedSelection.empty());

        ObfuscationPlan plan = useCase.plan(selection());

        assertSoftly(softly -> {
            softly.assertThat(plan.totalFindings()).isZero();
            softly.assertThat(plan.bySeverity()).isEmpty();
            softly.assertThat(plan.pagesImpacted()).isZero();
            softly.assertThat(plan.attachmentExclusions()).isZero();
            softly.assertThat(plan.selectionChecksum()).isEqualTo(ObfuscationPlan.checksumOf(List.of()));
        });
    }

    private static RemediationSelection selection() {
        return RemediationSelection.builder()
                .spaceKey("SPACE")
                .piiTypes(List.of("EMAIL", "IBAN"))
                .build();
    }

    private static EligibleFinding finding(String piiType, String pageId, String attachmentName,
                                           String fingerprint,
                                           PersonallyIdentifiableInformationSeverity severity) {
        FindingReference reference = FindingReference.builder()
                .spaceKey("SPACE")
                .pageId(pageId)
                .attachmentName(attachmentName)
                .detector("PRESIDIO")
                .piiType(piiType)
                .severity(severity)
                .valueFingerprint(fingerprint)
                .build();
        return EligibleFinding.builder()
                .findingId(reference.findingId())
                .reference(reference)
                .confidence(0.9)
                .piiTypeLabel(piiType)
                .maskedContext("masked")
                .pageTitle("Page " + pageId)
                .build();
    }
}

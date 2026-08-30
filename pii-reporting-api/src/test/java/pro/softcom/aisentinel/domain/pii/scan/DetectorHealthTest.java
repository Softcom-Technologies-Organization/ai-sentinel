package pro.softcom.aisentinel.domain.pii.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the translation of a detection-service failure code into what the operator
 * reads. A wrong or missing key means a dashboard showing a raw identifier instead
 * of a sentence, on the very screen that explains why the scan was refused.
 */
class DetectorHealthTest {

    @Test
    @DisplayName("failure - an unloaded model keeps the values its sentence needs")
    void Should_CarryModelAndState_When_ModelIsNotLoaded() {
        DetectorHealth health = new DetectorHealth(DetectorSource.MINISTRAL, false,
            "http://lmstudio:1234/v1", "model x is present but not loaded",
            "MODEL_NOT_LOADED", Map.of("model", "ministral-3b", "state", "not-loaded"));

        TranslatableError failure = health.failure();

        assertThat(failure.key()).isEqualTo(ScanErrorKeys.DETECTOR_MODEL_NOT_LOADED);
        assertThat(failure.params())
            .containsEntry("detector", "MINISTRAL")
            .containsEntry("endpoint", "http://lmstudio:1234/v1")
            .containsEntry("model", "ministral-3b")
            .containsEntry("state", "not-loaded");
    }

    @Test
    @DisplayName("failure - a code this backend does not know still reads as a sentence")
    void Should_FallBackToGenericWording_When_CodeIsUnknown() {
        // A detection service newer than this backend must not leave the operator in
        // front of a refusal with no explanation, so the raw reason becomes a parameter.
        DetectorHealth health = new DetectorHealth(DetectorSource.MINISTRAL, false,
            "http://lmstudio:1234/v1", "quota exhausted", "QUOTA_EXHAUSTED", Map.of());

        TranslatableError failure = health.failure();

        assertThat(failure.key()).isEqualTo(ScanErrorKeys.DETECTOR_UNUSABLE);
        assertThat(failure.params()).containsEntry("cause", "quota exhausted");
    }

    @Test
    @DisplayName("failure - a service reporting no code at all still reads as a sentence")
    void Should_FallBackToGenericWording_When_NoCodeIsReported() {
        DetectorHealth health = new DetectorHealth(DetectorSource.PRESIDIO, false, "", "", "", Map.of());

        TranslatableError failure = health.failure();

        assertThat(failure.key()).isEqualTo(ScanErrorKeys.DETECTOR_UNUSABLE);
        assertThat(failure.params()).containsEntry("detector", "PRESIDIO");
    }
}

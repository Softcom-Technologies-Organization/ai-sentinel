package pro.softcom.aisentinel.domain.pii.scan;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContentPiiDetectionTest {

    @ParameterizedTest
    @ValueSource(strings = {"BARDS", "CBX", "ETTIN"})
    void Should_RetainNativeSource_When_FindingIsCreated(String detector) {
        ContentPiiDetection.SensitiveData finding = new ContentPiiDetection.SensitiveData(
                "PASSWORD", "Password", "secret", "secret", 0, 6, 0.9, null,
                ContentPiiDetection.DetectorSource.valueOf(detector));

        assertThat(finding.source().name()).isEqualTo(detector);
    }
}

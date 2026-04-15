package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScanEventTypeTest {

    @ParameterizedTest
    @EnumSource(ScanEventType.class)
    void Should_ResolveEveryConstant_When_ExactJsonValueProvided(ScanEventType type) {
        assertThat(ScanEventType.from(type.toJson())).isEqualTo(type);
    }

    @ParameterizedTest
    @CsvSource({
        "start, START",
        "START, START",
        "StArT, START",
        "multiStart, MULTI_START",
        "MULTISTART, MULTI_START",
        "pagecomplete, PAGE_COMPLETE",
        "SCANERROR, ERROR"
    })
    void Should_ResolveEnum_When_MixedCaseValueProvided(String input, ScanEventType expected) {
        assertThat(ScanEventType.from(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void Should_ReturnNull_When_InputIsNullOrBlank(String input) {
        assertThat(ScanEventType.from(input)).isNull();
    }

    @Test
    void Should_ReturnNull_When_ValueIsUnknown() {
        assertThat(ScanEventType.from("nonExistentEvent")).isNull();
    }
}

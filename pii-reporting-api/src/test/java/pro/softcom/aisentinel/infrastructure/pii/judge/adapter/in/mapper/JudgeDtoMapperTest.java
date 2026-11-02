package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.mapper;

import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeLaunchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationJobDtoMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeDtoMapperTest {

    private final JudgeDtoMapper mapper = new JudgeDtoMapper(new RemediationJobDtoMapper());

    @Test
    void Should_UseCalibratedThreshold_When_ThresholdOmitted() {
        var command = mapper.toCommand(request(null), "tester");

        assertThat(command.confidenceThreshold()).isEqualTo(0.95);
        assertThat(command.applyMode()).isEqualTo(JudgeApplyMode.PROPOSE);
    }

    @Test
    void Should_PreserveThreshold_When_ExplicitlyRequested() {
        assertThat(mapper.toCommand(request(0.975), "tester").confidenceThreshold()).isEqualTo(0.975);
    }

    @Test
    void Should_ProposeOnly_When_ApplyModeOmitted() {
        var request = new JudgeLaunchRequestDto(new JudgeScopeDto(List.of("SPACE"), null), null, null, null);

        assertThat(mapper.toCommand(request, "tester").applyMode()).isEqualTo(JudgeApplyMode.PROPOSE);
    }

    @Test
    void Should_PreserveAutoApply_When_ExplicitlyRequested() {
        var request = new JudgeLaunchRequestDto(new JudgeScopeDto(List.of("SPACE"), null),
                null, "AUTO_APPLY", null);

        assertThat(mapper.toCommand(request, "tester").applyMode()).isEqualTo(JudgeApplyMode.AUTO_APPLY);
    }

    @Test
    void Should_RejectRequest_When_Missing() {
        assertThatThrownBy(() -> mapper.toCommand(null, "tester"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_RejectThreshold_When_OutsideRange() {
        assertThatThrownBy(() -> mapper.toCommand(request(1.01), "tester"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JudgeLaunchRequestDto request(Double threshold) {
        return new JudgeLaunchRequestDto(new JudgeScopeDto(List.of("SPACE"), null),
                null, "PROPOSE", threshold);
    }
}

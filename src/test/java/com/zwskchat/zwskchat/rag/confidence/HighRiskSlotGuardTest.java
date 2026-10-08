package com.zwskchat.zwskchat.rag.confidence;

import com.zwskchat.zwskchat.config.properties.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HighRiskSlotGuardTest {

    private final AiProperties aiProps = new AiProperties();
    private final ResponseGovernanceService service = new ResponseGovernanceService(aiProps);

    private ConfidenceResult result(ConfidenceDecision decision) {
        return new ConfidenceResult(0.95, decision, Map.of());
    }

    @Test
    void detectsHighRiskSlotsByCategory() {
        assertThat(HighRiskSlotDetector.detect("这个项目报价多少钱")).contains("报价/价格");
        assertThat(HighRiskSlotDetector.detect("这样做是否合法合规")).contains("政策/法律/合规");
        assertThat(HighRiskSlotDetector.detect("能保证百分百上线吗")).contains("资质承诺");
        assertThat(HighRiskSlotDetector.detect("工期多久能交付")).contains("交付边界/工期");
        assertThat(HighRiskSlotDetector.detect("实验参数和配方是什么")).contains("实验参数");
    }

    @Test
    void noHighRiskForNormalQuestion() {
        assertThat(HighRiskSlotDetector.detect("介绍一下你们的产品功能")).isEmpty();
        assertThat(HighRiskSlotDetector.hasHighRisk(null)).isFalse();
        assertThat(HighRiskSlotDetector.hasHighRisk("  ")).isFalse();
    }

    @Test
    void highRiskForcesCautionEvenWhenConfidenceIsAnswer() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER), "请给我一个最终报价");

        assertThat(g.action()).isEqualTo(GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION);
        assertThat(g.note()).contains("人工复核");
        assertThat(g.ctaActions()).isNotEmpty();
    }

    @Test
    void highRiskAppendsNoteOnTopOfExistingCaution() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.CAUTIOUS), "工期多久能交付");

        assertThat(g.action()).isEqualTo(GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION);
        // 既保留原谨慎提示，又追加高风险复核提示
        assertThat(g.note())
                .contains(aiProps.getGovernance().getCautionNote())
                .contains("人工复核");
    }

    @Test
    void normalQuestionKeepsAnswerNormal() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER), "介绍一下公司能力");
        assertThat(g.action()).isEqualTo(GovernanceResult.GovernanceAction.ANSWER_NORMAL);
        assertThat(g.note()).isBlank();
    }

    @Test
    void disabledGuardDoesNotEscalate() {
        aiProps.getGovernance().setHighRiskSlotGuardEnabled(false);
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER), "请给我一个最终报价");
        assertThat(g.action()).isEqualTo(GovernanceResult.GovernanceAction.ANSWER_NORMAL);
    }

    @Test
    void hardRefusalNotDowngradedByHighRisk() {
        aiProps.getGovernance().setEnforceRefusal(true);
        GovernanceResult g = service.govern(result(ConfidenceDecision.REFUSE), "请给我一个最终报价");
        assertThat(g.action()).isEqualTo(GovernanceResult.GovernanceAction.REFUSE_AND_GUIDE);
    }
}

package com.zwskchat.zwskchat.rag.confidence;

import com.zwskchat.zwskchat.config.properties.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseGovernanceServiceTest {

    private final AiProperties aiProps = new AiProperties();
    private final ResponseGovernanceService service = new ResponseGovernanceService(aiProps);

    private ConfidenceResult result(ConfidenceDecision decision) {
        return new ConfidenceResult(0.5, decision, Map.of());
    }

    @Test
    void answerDecisionMapsToNormalWithCta() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER));

        assertSame(GovernanceResult.GovernanceAction.ANSWER_NORMAL, g.action());
        assertTrue(g.note().isBlank());
        assertFalse(g.ctaActions().isEmpty(), "CTA 应始终给出");
        assertFalse(g.isHardRefusal());
    }

    @Test
    void cautiousDecisionAddsCautionNote() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.CAUTIOUS));

        assertSame(GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION, g.action());
        assertFalse(g.note().isBlank());
        assertFalse(g.ctaActions().isEmpty());
    }

    @Test
    void refuseDowngradesToCautionWhenNotEnforced() {
        // 默认 enforceRefusal=false
        GovernanceResult g = service.govern(result(ConfidenceDecision.REFUSE));

        assertSame(GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION, g.action(),
                "默认不硬拒答，应降级为谨慎回答");
        assertFalse(g.isHardRefusal());
    }

    @Test
    void refuseHardBlocksWhenEnforced() {
        aiProps.getGovernance().setEnforceRefusal(true);

        GovernanceResult g = service.govern(result(ConfidenceDecision.REFUSE));

        assertSame(GovernanceResult.GovernanceAction.REFUSE_AND_GUIDE, g.action());
        assertTrue(g.isHardRefusal());
        assertFalse(g.requiredInfo().isEmpty(), "拒答应给出需补充信息清单");

        String text = service.renderRefusalText(g);
        assertTrue(text.contains("建议补充以下信息"));
        assertTrue(text.contains(aiProps.getGovernance().getRequiredInfo().get(0)));
    }

    @Test
    void disabledGovernanceAlwaysAnswersNormalWithoutCta() {
        aiProps.getGovernance().setEnabled(false);

        GovernanceResult g = service.govern(result(ConfidenceDecision.REFUSE));

        assertSame(GovernanceResult.GovernanceAction.ANSWER_NORMAL, g.action());
        assertTrue(g.ctaActions().isEmpty());
        assertTrue(g.note().isBlank());
    }

    @Test
    void nullConfidenceIsSafe() {
        GovernanceResult g = service.govern(null);
        assertSame(GovernanceResult.GovernanceAction.ANSWER_NORMAL, g.action());
    }

    @Test
    void renderRefusalReturnsEmptyForNonRefusal() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER));
        assertEquals("", service.renderRefusalText(g));
    }

    @Test
    void resultCollectionsAreImmutable() {
        GovernanceResult g = service.govern(result(ConfidenceDecision.ANSWER));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> g.ctaActions().add("x"));
    }
}

package com.zwskchat.zwskchat.config.properties;

import com.zwskchat.zwskchat.ai.ModelTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiPropertiesTierTest {

    @Test
    void tierDisabledAlwaysReturnsBaseModel() {
        AiProperties props = new AiProperties();
        props.getChat().setModel("base-model");
        props.getChat().getTier().setEnabled(false);
        props.getChat().getTier().setStrongModel("strong");

        assertEquals("base-model", props.resolveTierModel(ModelTier.STRONG, "base-model"));
        assertEquals("base-model", props.resolveTierModel(ModelTier.LIGHT, "base-model"));
    }

    @Test
    void tierEnabledRoutesByTier() {
        AiProperties props = new AiProperties();
        AiProperties.Tier tier = props.getChat().getTier();
        tier.setEnabled(true);
        tier.setLightModel("light");
        tier.setStandardModel("standard");
        tier.setStrongModel("strong");

        assertEquals("light", props.resolveTierModel(ModelTier.LIGHT, "base"));
        assertEquals("standard", props.resolveTierModel(ModelTier.STANDARD, "base"));
        assertEquals("strong", props.resolveTierModel(ModelTier.STRONG, "base"));
    }

    @Test
    void missingTierModelFallsBackToBase() {
        AiProperties props = new AiProperties();
        AiProperties.Tier tier = props.getChat().getTier();
        tier.setEnabled(true);
        // 只配 strong，light/standard 留空 -> 回退 base
        tier.setStrongModel("strong");

        assertEquals("base", props.resolveTierModel(ModelTier.LIGHT, "base"));
        assertEquals("base", props.resolveTierModel(ModelTier.STANDARD, "base"));
        assertEquals("strong", props.resolveTierModel(ModelTier.STRONG, "base"));
    }

    @Test
    void nullTierTreatedAsStandard() {
        AiProperties props = new AiProperties();
        AiProperties.Tier tier = props.getChat().getTier();
        tier.setEnabled(true);
        tier.setStandardModel("standard");

        assertEquals("standard", props.resolveTierModel(null, "base"));
    }
}

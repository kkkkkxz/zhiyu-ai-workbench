package com.zwskchat.zwskchat.ai.hub.service;

import com.zwskchat.zwskchat.ai.hub.entity.AiActionDefinition;

import java.util.List;

public interface AiActionDefinitionService {

    List<AiActionDefinition> listEnabled();

    AiActionDefinition getByCode(String actionCode);

    AiActionDefinition create(AiActionDefinition definition);

    AiActionDefinition update(AiActionDefinition definition);

    void toggleEnabled(Long id, boolean enabled);
}

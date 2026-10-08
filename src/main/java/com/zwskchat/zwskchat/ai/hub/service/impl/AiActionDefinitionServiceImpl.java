package com.zwskchat.zwskchat.ai.hub.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.ai.hub.entity.AiActionDefinition;
import com.zwskchat.zwskchat.ai.hub.mapper.AiActionDefinitionMapper;
import com.zwskchat.zwskchat.ai.hub.service.AiActionDefinitionService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiActionDefinitionServiceImpl implements AiActionDefinitionService {

    private static final long GLOBAL_TENANT_ID = 0L;

    private final AiActionDefinitionMapper definitionMapper;

    @Override
    public List<AiActionDefinition> listEnabled() {
        LoginUser user = SecurityUtils.currentUser();
        return definitionMapper.selectList(Wrappers.<AiActionDefinition>lambdaQuery()
                .and(wrapper -> wrapper.eq(AiActionDefinition::getTenantId, GLOBAL_TENANT_ID)
                        .or().eq(AiActionDefinition::getTenantId, user.getTenantId()))
                .eq(AiActionDefinition::getEnabled, 1)
                .orderByAsc(AiActionDefinition::getSort)
                .orderByAsc(AiActionDefinition::getId));
    }

    @Override
    public AiActionDefinition getByCode(String actionCode) {
        LoginUser user = SecurityUtils.currentUser();
        return definitionMapper.selectOne(Wrappers.<AiActionDefinition>lambdaQuery()
                .and(wrapper -> wrapper.eq(AiActionDefinition::getTenantId, GLOBAL_TENANT_ID)
                        .or().eq(AiActionDefinition::getTenantId, user.getTenantId()))
                .eq(AiActionDefinition::getActionCode, actionCode)
                .eq(AiActionDefinition::getEnabled, 1)
                .last("LIMIT 1"));
    }

    @Override
    public AiActionDefinition create(AiActionDefinition definition) {
        LoginUser user = SecurityUtils.currentUser();
        definition.setTenantId(user.getTenantId());
        definition.setDeleted(0);
        definition.setEnabled(1);
        definitionMapper.insert(definition);
        return definition;
    }

    @Override
    public AiActionDefinition update(AiActionDefinition definition) {
        if (definition.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "action definition id is required");
        }
        AiActionDefinition existing = requireOwnedDefinition(definition.getId());
        definition.setTenantId(existing.getTenantId());
        definition.setDeleted(existing.getDeleted());
        definitionMapper.updateById(definition);
        return definitionMapper.selectById(definition.getId());
    }

    @Override
    public void toggleEnabled(Long id, boolean enabled) {
        AiActionDefinition definition = requireOwnedDefinition(id);
        definition.setEnabled(enabled ? 1 : 0);
        definitionMapper.updateById(definition);
    }

    private AiActionDefinition requireOwnedDefinition(Long id) {
        AiActionDefinition definition = definitionMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "action definition not found");
        }
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        if (!Objects.equals(definition.getTenantId(), tenantId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "no permission for this action definition");
        }
        return definition;
    }
}

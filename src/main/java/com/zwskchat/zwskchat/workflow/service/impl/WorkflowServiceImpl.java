package com.zwskchat.zwskchat.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.workflow.entity.WfDefinition;
import com.zwskchat.zwskchat.workflow.entity.WfInstance;
import com.zwskchat.zwskchat.workflow.entity.WfTask;
import com.zwskchat.zwskchat.workflow.mapper.WfDefinitionMapper;
import com.zwskchat.zwskchat.workflow.mapper.WfInstanceMapper;
import com.zwskchat.zwskchat.workflow.mapper.WfTaskMapper;
import com.zwskchat.zwskchat.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final String RUNNING_STATUS = "running";
    private static final String PENDING_STATUS = "pending";
    private static final String COMPLETED_STATUS = "completed";
    private static final String REJECT_ACTION = "reject";
    private static final String REJECTED_STATUS = "rejected";
    private static final String APPROVED_STATUS = "approved";

    private final WfDefinitionMapper defMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<WfDefinition> listDefinitions() {
        return defMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getStatus, 1)
                .orderByDesc(WfDefinition::getCreateTime));
    }

    @Override
    @Transactional
    public WfDefinition createDefinition(WfDefinition def) {
        def.setTenantId(SecurityUtils.currentUser().getTenantId());
        defMapper.insert(def);
        return def;
    }

    @Override
    @Transactional
    public WfInstance startInstance(Long definitionId, String bizType, Long bizId, String title, String formData) {
        LoginUser user = SecurityUtils.currentUser();
        WfDefinition definition = defMapper.selectById(definitionId);
        if (definition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "workflow definition not found");
        }

        List<Map<String, Object>> nodes = parseNodes(definition.getNodesJson());
        WfInstance instance = new WfInstance();
        instance.setTenantId(user.getTenantId());
        instance.setDefinitionId(definitionId);
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setTitle(title);
        instance.setInitiatorId(user.getUserId());
        instance.setStatus(RUNNING_STATUS);
        instance.setFormData(formData);
        if (!nodes.isEmpty()) {
            instance.setCurrentNode(asString(nodes.get(0).get("code")));
        }
        instanceMapper.insert(instance);

        if (!nodes.isEmpty()) {
            Map<String, Object> firstNode = nodes.get(0);
            WfTask task = new WfTask();
            task.setTenantId(user.getTenantId());
            task.setInstanceId(instance.getId());
            task.setNodeCode(asString(firstNode.get("code")));
            task.setNodeName(asString(firstNode.get("name")));
            task.setAssigneeId(user.getUserId());
            task.setStatus(PENDING_STATUS);
            taskMapper.insert(task);
        }
        return instance;
    }

    @Override
    public WfInstance getInstance(Long instanceId) {
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "workflow instance not found");
        }
        return instance;
    }

    @Override
    public IPage<WfInstance> myInitiated(Page<WfInstance> page) {
        Long userId = SecurityUtils.currentUser().getUserId();
        return instanceMapper.selectPage(page, new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getInitiatorId, userId)
                .orderByDesc(WfInstance::getCreateTime));
    }

    @Override
    public IPage<WfTask> myPendingTasks(Page<WfTask> page) {
        LoginUser user = SecurityUtils.currentUser();
        return taskMapper.selectPage(page, new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, user.getTenantId())
                .eq(WfTask::getAssigneeId, user.getUserId())
                .eq(WfTask::getStatus, PENDING_STATUS)
                .orderByDesc(WfTask::getCreateTime));
    }

    @Override
    @Transactional
    public void approve(Long taskId, String comment) {
        handleTask(taskId, "approve", comment);
    }

    @Override
    @Transactional
    public void reject(Long taskId, String comment) {
        handleTask(taskId, REJECT_ACTION, comment);
    }

    private void handleTask(Long taskId, String action, String comment) {
        LoginUser user = SecurityUtils.currentUser();
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "workflow task not found");
        }
        if (!Objects.equals(task.getTenantId(), user.getTenantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "no permission for this workflow task");
        }
        if (!Objects.equals(task.getAssigneeId(), user.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "only task assignee can operate this task");
        }
        if (!PENDING_STATUS.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "workflow task has been handled");
        }

        WfInstance instance = getInstance(task.getInstanceId());
        task.setAction(action);
        task.setComment(comment);
        task.setStatus(COMPLETED_STATUS);
        task.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(task);

        if (REJECT_ACTION.equals(action)) {
            instance.setStatus(REJECTED_STATUS);
            instance.setFinishTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
            return;
        }

        Long pendingCount = taskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, user.getTenantId())
                .eq(WfTask::getInstanceId, instance.getId())
                .eq(WfTask::getStatus, PENDING_STATUS));
        if (pendingCount == 0L) {
            instance.setStatus(APPROVED_STATUS);
            instance.setFinishTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
        }
    }

    private List<Map<String, Object>> parseNodes(String nodesJson) {
        try {
            return objectMapper.readValue(nodesJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[Workflow] parse nodesJson failed", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "workflow nodes parse failed");
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}

package com.zwskchat.zwskchat.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.workflow.entity.WfDefinition;
import com.zwskchat.zwskchat.workflow.entity.WfInstance;
import com.zwskchat.zwskchat.workflow.entity.WfTask;

import java.util.List;

public interface WorkflowService {
    List<WfDefinition> listDefinitions();
    WfDefinition createDefinition(WfDefinition def);

    WfInstance startInstance(Long definitionId, String bizType, Long bizId, String title, String formData);
    WfInstance getInstance(Long instanceId);
    IPage<WfInstance> myInitiated(Page<WfInstance> page);

    IPage<WfTask> myPendingTasks(Page<WfTask> page);
    void approve(Long taskId, String comment);
    void reject(Long taskId, String comment);
}

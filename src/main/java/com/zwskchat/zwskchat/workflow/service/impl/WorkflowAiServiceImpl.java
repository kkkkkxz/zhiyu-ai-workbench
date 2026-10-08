package com.zwskchat.zwskchat.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.workflow.entity.WfInstance;
import com.zwskchat.zwskchat.workflow.entity.WfTask;
import com.zwskchat.zwskchat.workflow.mapper.WfInstanceMapper;
import com.zwskchat.zwskchat.workflow.mapper.WfTaskMapper;
import com.zwskchat.zwskchat.workflow.service.WorkflowAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowAiServiceImpl implements WorkflowAiService {

    private final WfTaskMapper taskMapper;
    private final WfInstanceMapper instanceMapper;
    private final AiService aiService;

    private static final String SUGGESTION_PROMPT = """
            你是审批辅助助手。请根据审批任务信息给出审批建议和风险提示。
            审批标题: %s
            业务类型: %s
            表单/说明: %s
            当前节点: %s
            已等待小时: %d
            
            请输出80字内建议，包含建议通过/补充材料/驳回，以及风险提示。
            """;

    @Override
    public Map<String, Object> approvalSuggestion(Long taskId) {
        WfTask task = taskMapper.selectById(taskId);
        Map<String, Object> result = new HashMap<>();
        if (task == null) {
            result.put("suggestion", "审批任务不存在");
            result.put("riskLevel", "UNKNOWN");
            return result;
        }
        WfInstance instance = instanceMapper.selectById(task.getInstanceId());
        long waitHours = task.getCreateTime() == null ? 0 : Duration.between(task.getCreateTime(), LocalDateTime.now()).toHours();
        String prompt = String.format(SUGGESTION_PROMPT,
                instance != null ? instance.getTitle() : "未知",
                instance != null ? instance.getBizType() : "未知",
                instance != null ? instance.getFormData() : "",
                task.getNodeName(),
                waitHours);
        String suggestion = aiService.processTextWithAi(prompt, null).trim();
        result.put("taskId", taskId);
        result.put("suggestion", suggestion);
        result.put("riskLevel", waitHours > 48 ? "HIGH" : waitHours > 24 ? "MEDIUM" : "LOW");
        result.put("waitHours", waitHours);
        return result;
    }

    @Override
    public Map<String, Object> bottleneckReport() {
        LoginUser user = SecurityUtils.currentUser();
        List<WfTask> tasks = taskMapper.selectList(Wrappers.<WfTask>lambdaQuery()
                .eq(WfTask::getTenantId, user.getTenantId())
                .eq(WfTask::getStatus, "pending"));
        int over24 = 0;
        int over48 = 0;
        Map<String, Integer> nodeCount = new HashMap<>();
        for (WfTask task : tasks) {
            nodeCount.merge(task.getNodeName() == null ? task.getNodeCode() : task.getNodeName(), 1, Integer::sum);
            long hours = task.getCreateTime() == null ? 0 : Duration.between(task.getCreateTime(), LocalDateTime.now()).toHours();
            if (hours > 24) over24++;
            if (hours > 48) over48++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("pendingCount", tasks.size());
        result.put("over24h", over24);
        result.put("over48h", over48);
        result.put("nodeDistribution", nodeCount);
        result.put("summary", over48 > 0 ? "存在超过48小时审批瓶颈" : over24 > 0 ? "存在超过24小时待审批任务" : "审批流转正常");
        return result;
    }
}

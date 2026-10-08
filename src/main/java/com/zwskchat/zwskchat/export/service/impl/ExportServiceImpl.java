package com.zwskchat.zwskchat.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizContractMilestone;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.contract.mapper.BizContractMilestoneMapper;
import com.zwskchat.zwskchat.crm.entity.BizContact;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;
import com.zwskchat.zwskchat.crm.mapper.BizContactMapper;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.crm.mapper.BizFollowUpMapper;
import com.zwskchat.zwskchat.export.constant.ExportConstants;
import com.zwskchat.zwskchat.export.dto.ExportDownloadResponse;
import com.zwskchat.zwskchat.export.dto.ExportReport;
import com.zwskchat.zwskchat.export.dto.ExportSection;
import com.zwskchat.zwskchat.export.dto.ExportTaskMessage;
import com.zwskchat.zwskchat.export.entity.ExportTask;
import com.zwskchat.zwskchat.export.mapper.ExportTaskMapper;
import com.zwskchat.zwskchat.export.service.ExportDocumentRenderer;
import com.zwskchat.zwskchat.export.service.ExportService;
import com.zwskchat.zwskchat.export.service.ExportTaskPublisher;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.storage.ObjectKeyBuilder;
import com.zwskchat.zwskchat.storage.ObjectStorage;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrderComment;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderCommentMapper;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExportTaskMapper taskMapper;
    private final ExportTaskPublisher taskPublisher;
    private final ExportDocumentRenderer documentRenderer;
    private final ObjectStorage objectStorage;
    private final ObjectKeyBuilder objectKeyBuilder;
    private final BizCustomerMapper customerMapper;
    private final BizContactMapper contactMapper;
    private final BizFollowUpMapper followUpMapper;
    private final BizContractMapper contractMapper;
    private final BizContractMilestoneMapper milestoneMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWorkOrderCommentMapper workOrderCommentMapper;
    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTask createTask(String bizType, Long bizId, String format) {
        if (bizType == null || bizType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "导出类型不能为空");
        }
        ExportTask task = new ExportTask();
        task.setTenantId(SecurityUtils.currentUser().getTenantId());
        task.setUserId(SecurityUtils.currentUser().getUserId());
        task.setBizType(bizType.trim());
        task.setBizId(bizId);
        task.setFormat(normalizeFormat(format));
        task.setStatus(ExportConstants.STATUS_PENDING);
        taskMapper.insert(task);

        ExportTaskMessage message = new ExportTaskMessage(
                task.getId(),
                task.getTenantId(),
                task.getUserId(),
                task.getBizType(),
                task.getBizId(),
                task.getFormat());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskPublisher.publish(message);
                }
            });
        } else {
            taskPublisher.publish(message);
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTask(Long taskId) {
        ExportTask task = loadTask(taskId);
        if (ExportConstants.STATUS_COMPLETED.equalsIgnoreCase(task.getStatus())
                && task.getFileKey() != null && !task.getFileKey().isBlank()) {
            return;
        }
        markProcessing(task);
        try {
            ExportReport report = buildReport(task);
            byte[] fileBytes = documentRenderer.render(report, task.getFormat());
            String extension = documentRenderer.extension(task.getFormat());
            String fileName = buildFileName(task, extension);
            String objectKey = objectKeyBuilder.build(
                    task.getTenantId(), task.getBizId(), String.valueOf(task.getId()), fileName);
            objectStorage.upload(objectKey, new ByteArrayInputStream(fileBytes), fileBytes.length,
                    documentRenderer.contentType(task.getFormat()));
            markCompleted(task.getId(), objectKey, fileName);
        } catch (Exception ex) {
            markFailed(task.getId(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    public ExportTask getById(Long id) {
        ExportTask task = loadTask(id);
        if (!SecurityUtils.currentUser().getUserId().equals(task.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该导出任务");
        }
        return task;
    }

    @Override
    public ExportDownloadResponse createDownloadUrl(Long id) {
        ExportTask task = getById(id);
        if (!ExportConstants.STATUS_COMPLETED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "导出任务尚未完成");
        }
        if (task.getFileKey() == null || task.getFileKey().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "导出文件不存在");
        }
        String url = objectStorage.presignedDownloadUrl(
                task.getFileKey(), ExportConstants.DOWNLOAD_URL_EXPIRES_IN_SECONDS);
        return new ExportDownloadResponse(url, ExportConstants.DOWNLOAD_URL_EXPIRES_IN_SECONDS);
    }

    @Override
    public IPage<ExportTask> myTasks(Page<ExportTask> page) {
        Long userId = SecurityUtils.currentUser().getUserId();
        return taskMapper.selectPage(page,
                new LambdaQueryWrapper<ExportTask>()
                        .eq(ExportTask::getUserId, userId)
                        .orderByDesc(ExportTask::getCreateTime));
    }

    private void markProcessing(ExportTask task) {
        ExportTask update = new ExportTask();
        update.setId(task.getId());
        update.setStatus(ExportConstants.STATUS_PROCESSING);
        update.setErrorMsg(null);
        taskMapper.updateById(update);
    }

    private void markCompleted(Long taskId, String fileKey, String fileName) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(ExportConstants.STATUS_COMPLETED);
        update.setFileKey(fileKey);
        update.setFileName(fileName);
        update.setErrorMsg(null);
        update.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markFailed(Long taskId, String errorMsg) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(ExportConstants.STATUS_FAILED);
        update.setErrorMsg(errorMsg == null ? "导出失败" : errorMsg);
        update.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? ExportConstants.FORMAT_PDF : format.trim().toLowerCase();
        if (!ExportConstants.FORMAT_PDF.equals(normalized) && !ExportConstants.FORMAT_DOCX.equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的导出格式: " + format);
        }
        return normalized;
    }

    private String buildFileName(ExportTask task, String extension) {
        return task.getBizType() + "-" + task.getBizId() + "-" + task.getId() + "." + extension;
    }

    private ExportReport buildReport(ExportTask task) {
        List<ExportSection> sections = new ArrayList<>();
        sections.add(new ExportSection("任务信息", List.of(
                "任务ID: " + task.getId(),
                "业务类型: " + task.getBizType(),
                "业务ID: " + task.getBizId(),
                "导出格式: " + task.getFormat(),
                "状态: " + task.getStatus(),
                "创建时间: " + formatTime(task.getCreateTime()))));

        if (ExportConstants.BIZ_TYPE_CUSTOMER.equalsIgnoreCase(task.getBizType())) {
            sections.add(buildCustomerSection(task.getBizId()));
            return new ExportReport("客户导出报告", "导出任务明细与业务摘要", sections);
        }
        if (ExportConstants.BIZ_TYPE_CONTRACT.equalsIgnoreCase(task.getBizType())) {
            sections.add(buildContractSection(task.getBizId()));
            return new ExportReport("合同导出报告", "导出任务明细与业务摘要", sections);
        }
        if (ExportConstants.BIZ_TYPE_WORK_ORDER.equalsIgnoreCase(task.getBizType())) {
            sections.add(buildWorkOrderSection(task.getBizId()));
            return new ExportReport("工单导出报告", "导出任务明细与业务摘要", sections);
        }
        if (ExportConstants.BIZ_TYPE_SESSION.equalsIgnoreCase(task.getBizType())) {
            sections.add(buildSessionSection(task.getBizId()));
            return new ExportReport("会话导出报告", "导出任务明细与业务摘要", sections);
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的导出类型: " + task.getBizType());
    }

    private ExportSection buildCustomerSection(Long customerId) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客户不存在");
        }
        List<String> lines = new ArrayList<>();
        lines.add("客户名称: " + safe(customer.getName()));
        lines.add("客户简称: " + safe(customer.getShortName()));
        lines.add("客户类型: " + safe(customer.getType()));
        lines.add("行业: " + safe(customer.getIndustry()));
        lines.add("规模: " + safe(customer.getScale()));
        lines.add("来源: " + safe(customer.getSource()));
        lines.add("状态: " + safe(customer.getStatus()));
        lines.add("评分: " + safe(customer.getAiScore()));
        lines.add("生命周期: " + safe(customer.getLifecycleStage()));
        lines.add("下一步建议: " + safe(customer.getAiNextAction()));
        lines.add("电话: " + safe(customer.getPhone()));
        lines.add("邮箱: " + safe(customer.getEmail()));
        lines.add("地址: " + safe(customer.getAddress()));
        lines.add("最近跟进: " + safe(customer.getLastFollowAt()));
        lines.add("备注: " + safe(customer.getRemark()));

        List<BizContact> contacts = contactMapper.selectList(Wrappers.<BizContact>lambdaQuery()
                .eq(BizContact::getCustomerId, customerId)
                .orderByDesc(BizContact::getIsPrimary)
                .orderByAsc(BizContact::getId));
        lines.add("联系人数量: " + contacts.size());
        for (BizContact contact : contacts) {
            lines.add("- " + safe(contact.getName()) + " / " + safe(contact.getPosition())
                    + " / " + safe(contact.getPhone()) + " / " + safe(contact.getEmail()));
        }

        List<BizFollowUp> followUps = followUpMapper.selectList(Wrappers.<BizFollowUp>lambdaQuery()
                .eq(BizFollowUp::getCustomerId, customerId)
                .orderByDesc(BizFollowUp::getCreateTime)
                .last("limit 5"));
        lines.add("最近跟进");
        for (BizFollowUp followUp : followUps) {
            lines.add("- " + safe(followUp.getType()) + " | " + safe(followUp.getContent()));
        }
        return new ExportSection("客户摘要", lines);
    }

    private ExportSection buildContractSection(Long contractId) {
        BizContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "合同不存在");
        }
        List<String> lines = new ArrayList<>();
        lines.add("合同编号: " + safe(contract.getContractNo()));
        lines.add("合同标题: " + safe(contract.getTitle()));
        lines.add("客户ID: " + safe(contract.getCustomerId()));
        lines.add("合同类型: " + safe(contract.getType()));
        lines.add("金额: " + safe(contract.getAmount()));
        lines.add("币种: " + safe(contract.getCurrency()));
        lines.add("签署日期: " + safe(contract.getSignDate()));
        lines.add("开始日期: " + safe(contract.getStartDate()));
        lines.add("结束日期: " + safe(contract.getEndDate()));
        lines.add("状态: " + safe(contract.getStatus()));
        lines.add("AI 风险分: " + safe(contract.getAiRiskScore()));
        lines.add("AI 风险提示: " + safe(contract.getAiRiskNote()));
        lines.add("备注: " + safe(contract.getRemark()));

        List<BizContractMilestone> milestones = milestoneMapper.selectList(Wrappers.<BizContractMilestone>lambdaQuery()
                .eq(BizContractMilestone::getContractId, contractId)
                .orderByAsc(BizContractMilestone::getDueDate));
        lines.add("里程碑数量: " + milestones.size());
        for (BizContractMilestone milestone : milestones) {
            lines.add("- " + safe(milestone.getName()) + " / " + safe(milestone.getAmount())
                    + " / " + safe(milestone.getDueDate()) + " / " + safe(milestone.getStatus()));
        }
        return new ExportSection("合同摘要", lines);
    }

    private ExportSection buildWorkOrderSection(Long workOrderId) {
        BizWorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        List<String> lines = new ArrayList<>();
        lines.add("工单号: " + safe(workOrder.getOrderNo()));
        lines.add("标题: " + safe(workOrder.getTitle()));
        lines.add("类型: " + safe(workOrder.getType()));
        lines.add("优先级: " + safe(workOrder.getPriority()));
        lines.add("状态: " + safe(workOrder.getStatus()));
        lines.add("客户ID: " + safe(workOrder.getCustomerId()));
        lines.add("报修人: " + safe(workOrder.getReporterId()));
        lines.add("处理人: " + safe(workOrder.getAssigneeId()));
        lines.add("部门: " + safe(workOrder.getDeptId()));
        lines.add("SLA 截止: " + safe(workOrder.getSlaDeadline()));
        lines.add("AI 分类: " + safe(workOrder.getAiCategory()));
        lines.add("AI 建议: " + safe(workOrder.getAiSuggestion()));
        lines.add("分配理由: " + safe(workOrder.getAiAssignedReason()));
        lines.add("相似工单: " + safe(workOrder.getAiSimilarIds()));
        lines.add("解决建议: " + safe(workOrder.getAiResolutionHint()));
        lines.add("描述: " + safe(workOrder.getDescription()));

        List<BizWorkOrderComment> comments = workOrderCommentMapper.selectList(Wrappers.<BizWorkOrderComment>lambdaQuery()
                .eq(BizWorkOrderComment::getOrderId, workOrderId)
                .orderByDesc(BizWorkOrderComment::getCreateTime)
                .last("limit 5"));
        lines.add("最近评论");
        for (BizWorkOrderComment comment : comments) {
            lines.add("- " + safe(comment.getUserId()) + " | " + safe(comment.getContent()));
        }
        return new ExportSection("工单摘要", lines);
    }

    private ExportSection buildSessionSection(Long sessionId) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        List<String> lines = new ArrayList<>();
        lines.add("会话标题: " + safe(session.getTitle()));
        lines.add("会话类型: " + safe(session.getSessionType()));
        lines.add("关联知识库: " + safe(session.getKbIds()));
        lines.add("状态: " + safe(session.getStatus()));
        lines.add("创建时间: " + safe(session.getCreateTime()));
        lines.add("更新时间: " + safe(session.getUpdateTime()));

        List<AiMessage> messages = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime)
                .last("limit 10"));
        lines.add("最近消息");
        for (AiMessage message : messages) {
            lines.add("- " + safe(message.getRole()) + " | " + truncate(message.getContent(), 120));
        }
        return new ExportSection("会话摘要", lines);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private String safe(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMATTER.format(dateTime);
        }
        return String.valueOf(value);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "-";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private ExportTask loadTask(Long id) {
        ExportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "导出任务不存在");
        }
        return task;
    }
}

package com.zwskchat.zwskchat.dispatch.service;

import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.notification.service.NotificationService;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizTaskReminderMapper;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.entity.SysUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AI 驱动的派发服务：
 * 1. 解析自然语言 → 提取目标人员 + 任务内容 + 截止时间
 * 2. 校验层级权限（上级→下级）
 * 3. 生成确认预览
 * 4. 确认后执行派发 + 同步到目标人日程 + 创建提醒
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDispatchService {

    private final AiService aiService;
    private final HierarchyService hierarchyService;
    private final DispatchService dispatchService;
    private final BizScheduleMapper scheduleMapper;
    private final BizTaskReminderMapper reminderMapper;
    private final NotificationService notificationService;

    private static final String PARSE_PROMPT = """
            你是任务派发解析器。请从用户的话中提取以下信息，以JSON格式返回，没有的字段填null：
            {
              "deptName": "目标部门名称",
              "personName": "目标人员姓名",
              "taskTitle": "任务标题（简洁概括）",
              "taskDescription": "任务详细描述",
              "deadline": "截止时间（格式 yyyy-MM-dd HH:mm，如无则null）",
              "priority": "紧急程度（urgent/high/medium/low，默认medium）"
            }
            
            用户说：%s
            
            JSON:""";

    /**
     * 第一步：解析用户意图，生成派发预览
     */
    public DispatchPreview parseAndPreview(String userInput) {
        LoginUser currentUser = SecurityUtils.currentUser();

        // 1. AI 解析
        String prompt = String.format(PARSE_PROMPT, userInput);
        String jsonStr = aiService.processTextWithAi(prompt, null).trim();
        // 清理 markdown 包裹
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceAll("```(?:json)?", "").trim();
        }
        log.info("[AiDispatch] parsed: {}", jsonStr);

        ParsedDispatch parsed;
        try {
            parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(jsonStr, ParsedDispatch.class);
        } catch (Exception e) {
            log.warn("[AiDispatch] parse failed: {}", jsonStr, e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法理解您的派发意图，请说清楚目标人员和任务内容");
        }

        if (parsed.personName == null || parsed.personName.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请说明要把任务派发给谁");
        }
        if (parsed.taskTitle == null || parsed.taskTitle.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请说明要派发什么任务");
        }

        // 2. 找到目标用户
        SysUser targetUser;
        if (parsed.deptName != null && !parsed.deptName.isBlank()) {
            SysDept dept = hierarchyService.findDeptByName(currentUser.getTenantId(), parsed.deptName);
            if (dept == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "找不到部门「" + parsed.deptName + "」");
            }
            targetUser = hierarchyService.findUserInDept(currentUser.getTenantId(), dept.getId(), parsed.personName);
            if (targetUser == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "在「" + parsed.deptName + "」中找不到叫「" + parsed.personName + "」的人");
            }
        } else {
            targetUser = hierarchyService.findUserByName(currentUser.getTenantId(), parsed.personName);
            if (targetUser == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "找不到叫「" + parsed.personName + "」的人");
            }
        }

        // 3. 权限校验：与手动派发的部门/人员下拉保持一致
        if (!canDispatchTo(targetUser.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您不是「" + targetUser.getNickname() + "」的上级，无权派发任务");
        }

        // 4. 组装预览
        DispatchPreview preview = new DispatchPreview();
        preview.setTargetUserId(targetUser.getId());
        preview.setTargetUserName(targetUser.getNickname() != null ? targetUser.getNickname() : targetUser.getUsername());
        preview.setTargetDeptName(parsed.deptName);
        preview.setTaskTitle(parsed.taskTitle);
        preview.setTaskDescription(parsed.taskDescription);
        preview.setPriority(parsed.priority != null ? parsed.priority : "medium");
        if (parsed.deadline != null && !parsed.deadline.isBlank()) {
            try {
                preview.setDeadline(LocalDateTime.parse(parsed.deadline, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            } catch (Exception ignored) { }
        }
        preview.setConfirmMessage(String.format(
                "即将向 %s 派发任务：\n📋 %s\n%s\n⏰ 截止时间：%s\n🔴 优先级：%s\n\n请确认是否派发？",
                preview.getTargetUserName(),
                preview.getTaskTitle(),
                preview.getTaskDescription() != null ? "📝 " + preview.getTaskDescription() : "",
                preview.getDeadline() != null ? preview.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "未指定",
                preview.getPriority()
        ));
        return preview;
    }

    /**
     * 第二步：用户确认后执行派发
     */
    @Transactional
    public BizDispatch confirmAndDispatch(DispatchPreview preview) {
        LoginUser currentUser = SecurityUtils.currentUser();

        // 再次校验权限（防止篡改）
        if (!canDispatchTo(preview.getTargetUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权派发");
        }

        // 1. 创建派发记录
        BizDispatch dispatch = new BizDispatch();
        dispatch.setTitle(preview.getTaskTitle());
        dispatch.setDescription(preview.getTaskDescription());
        dispatch.setType("task");
        dispatch.setPriority(preview.getPriority());
        dispatch.setStatus("pending");
        if (preview.getDeadline() != null) {
            dispatch.setDueDate(preview.getDeadline());
        }
        BizDispatch created = dispatchService.create(dispatch, List.of(preview.getTargetUserId()));

        // 2. 同步到目标人的日程
        BizSchedule schedule = new BizSchedule();
        schedule.setTenantId(currentUser.getTenantId());
        schedule.setUserId(preview.getTargetUserId());
        schedule.setTitle("📋 " + preview.getTaskTitle());
        schedule.setDescription(preview.getTaskDescription());
        schedule.setType("task");
        schedule.setStartTime(LocalDateTime.now());
        if (preview.getDeadline() != null) {
            schedule.setEndTime(preview.getDeadline());
        }
        schedule.setAllDay(0);
        schedule.setBizType("dispatch");
        schedule.setBizId(created.getId());
        schedule.setColor("#f59e0b");
        schedule.setStatus("active");
        scheduleMapper.insert(schedule);

        // 3. 创建任务提醒
        BizTaskReminder reminder = new BizTaskReminder();
        reminder.setTenantId(currentUser.getTenantId());
        reminder.setUserId(preview.getTargetUserId());
        reminder.setTitle("新任务：" + preview.getTaskTitle());
        reminder.setContent("来自 " + currentUser.getUsername() + " 的派发任务");
        reminder.setRemindTime(LocalDateTime.now());
        reminder.setRemindType("immediate");
        reminder.setChannel("web");
        reminder.setBizType("dispatch");
        reminder.setBizId(created.getId());
        reminder.setStatus("pending");
        reminderMapper.insert(reminder);

        // 4. 发送通知
        notificationService.send(
                currentUser.getTenantId(),
                preview.getTargetUserId(),
                "dispatch",
                "新任务派发：" + preview.getTaskTitle(),
                "来自 " + currentUser.getUsername() + " 的任务派发，请查看详情。",
                "dispatch",
                created.getId()
        );

        log.info("[AiDispatch] confirmed: dispatch={} target={}", created.getId(), preview.getTargetUserId());
        return created;
    }

    // ---- DTOs ----

    private boolean canDispatchTo(Long targetUserId) {
        if (targetUserId == null) {
            return false;
        }
        return dispatchService.assignableUsers(null).stream()
                .anyMatch(user -> targetUserId.equals(user.getId()));
    }

    @Data
    public static class ParsedDispatch {
        public String deptName;
        public String personName;
        public String taskTitle;
        public String taskDescription;
        public String deadline;
        public String priority;
    }

    @Data
    public static class DispatchPreview {
        private Long targetUserId;
        private String targetUserName;
        private String targetDeptName;
        private String taskTitle;
        private String taskDescription;
        private String priority;
        private LocalDateTime deadline;
        private String confirmMessage;
    }
}

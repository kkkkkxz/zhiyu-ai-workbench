package com.zwskchat.zwskchat.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizPaymentReminder;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.contract.mapper.BizPaymentReminderMapper;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.mapper.BizDispatchMapper;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizTaskReminderMapper;
import com.zwskchat.zwskchat.schedule.service.ScheduleAiService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleAiServiceImpl implements ScheduleAiService {

    private final BizScheduleMapper scheduleMapper;
    private final BizTaskReminderMapper reminderMapper;
    private final BizContractMapper contractMapper;
    private final BizPaymentReminderMapper paymentReminderMapper;
    private final BizDispatchMapper dispatchMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BizSchedule> generateSmartSchedules() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizSchedule> created = new ArrayList<>();

        List<BizPaymentReminder> paymentReminders = paymentReminderMapper.selectList(Wrappers.<BizPaymentReminder>lambdaQuery()
                .eq(BizPaymentReminder::getTenantId, user.getTenantId())
                .eq(BizPaymentReminder::getStatus, "pending")
                .le(BizPaymentReminder::getDueDate, LocalDate.now().plusDays(7))
                .last("LIMIT 20"));

        for (BizPaymentReminder reminder : paymentReminders) {
            boolean exists = scheduleExists(user.getUserId(), "payment_reminder", reminder.getId());
            if (exists) continue;
            BizSchedule s = new BizSchedule();
            s.setTenantId(user.getTenantId());
            s.setUserId(user.getUserId());
            s.setTitle("跟进回款: " + reminder.getTitle());
            s.setDescription(reminder.getAiSuggestion());
            s.setType("reminder");
            s.setStartTime(LocalDateTime.of(reminder.getDueDate().minusDays(1), LocalTime.of(10, 0)));
            s.setEndTime(s.getStartTime().plusMinutes(30));
            s.setAllDay(0);
            s.setBizType("payment_reminder");
            s.setBizId(reminder.getId());
            s.setStatus("active");
            scheduleMapper.insert(s);
            created.add(s);
        }

        List<BizContract> endingContracts = contractMapper.selectList(Wrappers.<BizContract>lambdaQuery()
                .eq(BizContract::getTenantId, user.getTenantId())
                .le(BizContract::getEndDate, LocalDate.now().plusDays(15))
                .ge(BizContract::getEndDate, LocalDate.now())
                .last("LIMIT 20"));

        for (BizContract contract : endingContracts) {
            boolean exists = scheduleExists(user.getUserId(), "contract", contract.getId());
            if (exists) continue;
            BizSchedule s = new BizSchedule();
            s.setTenantId(user.getTenantId());
            s.setUserId(user.getUserId());
            s.setTitle("合同到期检查: " + contract.getTitle());
            s.setDescription("AI 自动生成：合同即将到期，请确认续约、归档或终止安排。合同编号: " + contract.getContractNo());
            s.setType("follow_up");
            s.setStartTime(LocalDateTime.of(contract.getEndDate().minusDays(3), LocalTime.of(9, 30)));
            s.setEndTime(s.getStartTime().plusMinutes(30));
            s.setAllDay(0);
            s.setBizType("contract");
            s.setBizId(contract.getId());
            s.setStatus("active");
            scheduleMapper.insert(s);
            created.add(s);
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BizTaskReminder> generateSmartReminders() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizTaskReminder> created = new ArrayList<>();
        List<BizDispatch> dispatches = dispatchMapper.selectList(Wrappers.<BizDispatch>lambdaQuery()
                .eq(BizDispatch::getTenantId, user.getTenantId())
                .le(BizDispatch::getDueDate, LocalDateTime.now().plusDays(3))
                .in(BizDispatch::getStatus, List.of("pending", "accepted", "in_progress"))
                .last("LIMIT 50"));
        for (BizDispatch d : dispatches) {
            Long exists = reminderMapper.selectCount(Wrappers.<BizTaskReminder>lambdaQuery()
                    .eq(BizTaskReminder::getUserId, user.getUserId())
                    .eq(BizTaskReminder::getBizType, "dispatch")
                    .eq(BizTaskReminder::getBizId, d.getId())
                    .eq(BizTaskReminder::getStatus, "pending"));
            if (exists > 0) continue;
            BizTaskReminder r = new BizTaskReminder();
            r.setTenantId(user.getTenantId());
            r.setUserId(user.getUserId());
            r.setTitle("派发任务即将到期: " + d.getTitle());
            r.setContent("AI 提醒：该任务截止时间临近，请检查进度并及时提交结果。优先级: " + d.getPriority());
            r.setRemindTime(d.getDueDate() != null ? d.getDueDate().minusHours(4) : LocalDateTime.now().plusHours(1));
            r.setRemindType("once");
            r.setChannel("web");
            r.setBizType("dispatch");
            r.setBizId(d.getId());
            r.setStatus("pending");
            reminderMapper.insert(r);
            created.add(r);
        }
        return created;
    }

    @Override
    public Map<String, Object> todayBriefing() {
        LoginUser user = SecurityUtils.currentUser();
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        List<BizSchedule> schedules = scheduleMapper.selectList(Wrappers.<BizSchedule>lambdaQuery()
                .eq(BizSchedule::getUserId, user.getUserId())
                .ge(BizSchedule::getStartTime, start)
                .le(BizSchedule::getStartTime, end)
                .orderByAsc(BizSchedule::getStartTime));
        List<BizTaskReminder> reminders = reminderMapper.selectList(Wrappers.<BizTaskReminder>lambdaQuery()
                .eq(BizTaskReminder::getUserId, user.getUserId())
                .eq(BizTaskReminder::getStatus, "pending")
                .le(BizTaskReminder::getRemindTime, end)
                .orderByAsc(BizTaskReminder::getRemindTime));
        Map<String, Object> result = new HashMap<>();
        result.put("scheduleCount", schedules.size());
        result.put("reminderCount", reminders.size());
        result.put("schedules", schedules);
        result.put("reminders", reminders);
        result.put("summary", "今天有 " + schedules.size() + " 个日程、" + reminders.size() + " 个待处理提醒");
        return result;
    }

    private boolean scheduleExists(Long userId, String bizType, Long bizId) {
        Long count = scheduleMapper.selectCount(Wrappers.<BizSchedule>lambdaQuery()
                .eq(BizSchedule::getUserId, userId)
                .eq(BizSchedule::getBizType, bizType)
                .eq(BizSchedule::getBizId, bizId));
        return count > 0;
    }
}

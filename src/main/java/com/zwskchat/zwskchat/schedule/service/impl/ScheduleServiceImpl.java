package com.zwskchat.zwskchat.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizScheduleParticipant;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleParticipantMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizTaskReminderMapper;
import com.zwskchat.zwskchat.schedule.service.ScheduleService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final String DEFAULT_SCHEDULE_TYPE = "event";
    private static final String DEFAULT_REMINDER_STATUS = "pending";
    private static final String DISMISSED_STATUS = "dismissed";
    private static final String PARTICIPANT_PENDING = "pending";

    private final BizScheduleMapper scheduleMapper;
    private final BizScheduleParticipantMapper participantMapper;
    private final BizTaskReminderMapper reminderMapper;

    @Override
    public List<BizSchedule> listByRange(LocalDateTime start, LocalDateTime end) {
        LoginUser user = SecurityUtils.currentUser();
        return scheduleMapper.selectList(new LambdaQueryWrapper<BizSchedule>()
                .eq(BizSchedule::getTenantId, user.getTenantId())
                .eq(BizSchedule::getUserId, user.getUserId())
                .ge(BizSchedule::getStartTime, start)
                .le(BizSchedule::getStartTime, end)
                .orderByAsc(BizSchedule::getStartTime));
    }

    @Override
    public IPage<BizSchedule> listPage(Page<BizSchedule> page) {
        LoginUser user = SecurityUtils.currentUser();
        return scheduleMapper.selectPage(page, new LambdaQueryWrapper<BizSchedule>()
                .eq(BizSchedule::getTenantId, user.getTenantId())
                .eq(BizSchedule::getUserId, user.getUserId())
                .orderByAsc(BizSchedule::getStartTime));
    }

    @Override
    public BizSchedule getById(Long id) {
        BizSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "schedule not found");
        }
        if (!Objects.equals(schedule.getUserId(), SecurityUtils.currentUser().getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "no permission for this schedule");
        }
        return schedule;
    }

    @Override
    @Transactional
    public BizSchedule create(BizSchedule schedule, List<Long> participantIds) {
        LoginUser user = SecurityUtils.currentUser();
        schedule.setTenantId(user.getTenantId());
        schedule.setUserId(user.getUserId());
        if (schedule.getType() == null || schedule.getType().isBlank()) {
            schedule.setType(DEFAULT_SCHEDULE_TYPE);
        }
        scheduleMapper.insert(schedule);
        if (participantIds != null) {
            for (Long userId : participantIds) {
                BizScheduleParticipant participant = new BizScheduleParticipant();
                participant.setScheduleId(schedule.getId());
                participant.setUserId(userId);
                participant.setResponse(PARTICIPANT_PENDING);
                participantMapper.insert(participant);
            }
        }
        return schedule;
    }

    @Override
    @Transactional
    public BizSchedule update(Long id, BizSchedule schedule) {
        BizSchedule existing = getById(id);
        schedule.setId(id);
        schedule.setTenantId(existing.getTenantId());
        schedule.setUserId(existing.getUserId());
        if (schedule.getType() == null || schedule.getType().isBlank()) {
            schedule.setType(existing.getType());
        }
        scheduleMapper.updateById(schedule);
        return scheduleMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        scheduleMapper.deleteById(id);
    }

    @Override
    public IPage<BizTaskReminder> myReminders(Page<BizTaskReminder> page) {
        LoginUser user = SecurityUtils.currentUser();
        return reminderMapper.selectPage(page, new LambdaQueryWrapper<BizTaskReminder>()
                .eq(BizTaskReminder::getTenantId, user.getTenantId())
                .eq(BizTaskReminder::getUserId, user.getUserId())
                .eq(BizTaskReminder::getStatus, DEFAULT_REMINDER_STATUS)
                .orderByAsc(BizTaskReminder::getRemindTime));
    }

    @Override
    @Transactional
    public BizTaskReminder createReminder(BizTaskReminder reminder) {
        LoginUser user = SecurityUtils.currentUser();
        reminder.setTenantId(user.getTenantId());
        reminder.setUserId(user.getUserId());
        if (reminder.getStatus() == null || reminder.getStatus().isBlank()) {
            reminder.setStatus(DEFAULT_REMINDER_STATUS);
        }
        reminderMapper.insert(reminder);
        return reminder;
    }

    @Override
    @Transactional
    public void dismissReminder(Long reminderId) {
        BizTaskReminder reminder = reminderMapper.selectById(reminderId);
        if (reminder == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "reminder not found");
        }
        LoginUser user = SecurityUtils.currentUser();
        if (!Objects.equals(reminder.getTenantId(), user.getTenantId())
                || !Objects.equals(reminder.getUserId(), user.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "no permission for this reminder");
        }
        reminder.setStatus(DISMISSED_STATUS);
        reminderMapper.updateById(reminder);
    }
}

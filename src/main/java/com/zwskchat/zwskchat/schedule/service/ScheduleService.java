package com.zwskchat.zwskchat.schedule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleService {
    List<BizSchedule> listByRange(LocalDateTime start, LocalDateTime end);
    IPage<BizSchedule> listPage(Page<BizSchedule> page);
    BizSchedule getById(Long id);
    BizSchedule create(BizSchedule schedule, List<Long> participantIds);
    BizSchedule update(Long id, BizSchedule schedule);
    void delete(Long id);

    IPage<BizTaskReminder> myReminders(Page<BizTaskReminder> page);
    BizTaskReminder createReminder(BizTaskReminder reminder);
    void dismissReminder(Long reminderId);
}

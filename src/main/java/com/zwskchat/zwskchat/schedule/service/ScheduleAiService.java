package com.zwskchat.zwskchat.schedule.service;

import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;

import java.util.List;
import java.util.Map;

public interface ScheduleAiService {

    List<BizSchedule> generateSmartSchedules();

    List<BizTaskReminder> generateSmartReminders();

    Map<String, Object> todayBriefing();
}

package com.zwskchat.zwskchat.schedule.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.service.ScheduleAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "日程提醒 AI")
@RestController
@RequestMapping("/schedule/ai")
@RequiredArgsConstructor
public class ScheduleAiController {

    private final ScheduleAiService scheduleAiService;

    @Operation(summary = "AI 生成智能日程")
    @PostMapping("/smart-schedules")
    public Result<List<BizSchedule>> generateSmartSchedules() {
        return Result.success(scheduleAiService.generateSmartSchedules());
    }

    @Operation(summary = "AI 生成智能提醒")
    @PostMapping("/smart-reminders")
    public Result<List<BizTaskReminder>> generateSmartReminders() {
        return Result.success(scheduleAiService.generateSmartReminders());
    }

    @Operation(summary = "今日待办播报")
    @GetMapping("/today-briefing")
    public Result<Map<String, Object>> todayBriefing() {
        return Result.success(scheduleAiService.todayBriefing());
    }
}

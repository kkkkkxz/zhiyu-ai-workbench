package com.zwskchat.zwskchat.schedule.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "日程管理")
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "日历范围查询")
    @GetMapping("/calendar")
    public Result<List<BizSchedule>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.success(scheduleService.listByRange(start, end));
    }

    @Operation(summary = "日程列表")
    @GetMapping
    public Result<IPage<BizSchedule>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(scheduleService.listPage(new Page<>(page, size)));
    }

    @Operation(summary = "日程详情")
    @GetMapping("/{id}")
    public Result<BizSchedule> get(@PathVariable Long id) {
        return Result.success(scheduleService.getById(id));
    }

    @Operation(summary = "创建日程")
    @PostMapping
    public Result<BizSchedule> create(@RequestBody Map<String, Object> body) {
        BizSchedule s = new BizSchedule();
        s.setTitle((String) body.get("title"));
        s.setDescription((String) body.get("description"));
        s.setType((String) body.getOrDefault("type", "event"));
        s.setStartTime(LocalDateTime.parse((String) body.get("startTime")));
        if (body.containsKey("endTime") && body.get("endTime") != null) {
            s.setEndTime(LocalDateTime.parse((String) body.get("endTime")));
        }
        s.setLocation((String) body.get("location"));
        s.setColor((String) body.get("color"));
        s.setAllDay(body.containsKey("allDay") && Boolean.TRUE.equals(body.get("allDay")) ? 1 : 0);
        @SuppressWarnings("unchecked")
        List<Long> participantIds = body.containsKey("participantIds")
                ? ((List<Number>) body.get("participantIds")).stream().map(Number::longValue).toList()
                : null;
        return Result.success(scheduleService.create(s, participantIds));
    }

    @Operation(summary = "更新日程")
    @PutMapping("/{id}")
    public Result<BizSchedule> update(@PathVariable Long id, @RequestBody BizSchedule schedule) {
        return Result.success(scheduleService.update(id, schedule));
    }

    @Operation(summary = "删除日程")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "我的提醒")
    @GetMapping("/reminders")
    public Result<IPage<BizTaskReminder>> myReminders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(scheduleService.myReminders(new Page<>(page, size)));
    }

    @Operation(summary = "创建提醒")
    @PostMapping("/reminders")
    public Result<BizTaskReminder> createReminder(@RequestBody BizTaskReminder reminder) {
        return Result.success(scheduleService.createReminder(reminder));
    }

    @Operation(summary = "忽略提醒")
    @PutMapping("/reminders/{id}/dismiss")
    public Result<Void> dismissReminder(@PathVariable Long id) {
        scheduleService.dismissReminder(id);
        return Result.success();
    }
}
